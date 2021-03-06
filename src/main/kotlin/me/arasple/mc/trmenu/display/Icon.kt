package me.arasple.mc.trmenu.display

import me.arasple.mc.trmenu.TrMenu
import me.arasple.mc.trmenu.api.Extends.getMenuSession
import me.arasple.mc.trmenu.modules.data.MenuSession
import me.arasple.mc.trmenu.display.icon.IconProperty
import me.arasple.mc.trmenu.display.icon.IconSettings
import me.arasple.mc.trmenu.modules.packets.PacketsHandler
import me.arasple.mc.trmenu.utils.Msger
import me.arasple.mc.trmenu.utils.Tasks
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

/**
 * @author Arasple
 * @date 2020/5/30 13:48
 */
class Icon(val id: String, val settings: IconSettings, val defIcon: IconProperty, val subIcons: List<IconProperty>, val currentIndex: MutableMap<UUID, Int>) {

    fun displayIcon(player: Player, menu: Menu) {
        refreshIcon(player)
        displayItemStack(player)
        startUpdateTasks(player, menu)
    }

    fun setItemStack(player: Player, session: MenuSession) {
        val property = getIconProperty(player)
        val slots = property.display.getPosition(player, session.page)
        val item = property.display.createDisplayItem(player)
        slots?.forEach { PacketsHandler.sendOutSlot(player, it, item) }
        if (property.display.isAnimatedPosition(session.page)) PacketsHandler.sendClearNonIconSlots(player, session)
    }

    fun displayItemStack(player: Player) {
        val session = player.getMenuSession()

        Tasks.task(true) {
            if (!session.isNull()) setItemStack(player, session)
        }
    }

    private fun startUpdateTasks(player: Player, menu: Menu) {
        Tasks.task(true) {
            val session = player.getMenuSession()
            val sessionId = session.id

            // 图标物品更新
            settings.collectUpdatePeriods().let { it ->
                if (it.isEmpty()) return@let
                it.forEach {
                    val period = it.key.toLong()
                    object : BukkitRunnable() {
                        override fun run() {
                            if (session.isDifferent(sessionId)) cancel()
                            else {
                                getIconProperty(player).display.nextFrame(player, it.value, session.page)
                                setItemStack(player, session)
                            }
                        }
                    }.runTaskTimer(TrMenu.plugin, period, period)
                }
            }

            // 子图标刷新
            if (settings.refresh > 0 && subIcons.isNotEmpty()) {
                val period = settings.refresh.toLong()
                menu.tasking.task(
                    player,
                    object : BukkitRunnable() {
                        override fun run() {
                            if (session.isDifferent(sessionId)) cancel()
                            else if (refreshIcon(player)) {
                                displayItemStack(player)
                            }
                        }
                    }.runTaskTimer(TrMenu.plugin, period, period)
                )
            }
        }
    }

    fun getIconProperty(player: Player) = getIconPropertyIndex(player).let { if (it >= 0) subIcons[it] else defIcon }

    fun getIconPropertyIndex(player: Player) = currentIndex.computeIfAbsent(player.uniqueId) { -1 }

    fun refreshIcon(player: Player): Boolean {
        subIcons.forEachIndexed { index, it ->
            if (it.evalCondition(player)) {
                currentIndex[player.uniqueId] = index
                Msger.debug(player, "ICON.SUB-ICON-REFRESHED", id, currentIndex[player.uniqueId].toString())
                return true
            }
        }
        currentIndex[player.uniqueId] = -1
        return false
    }

    fun isInPage(page: Int) = defIcon.display.position.containsKey(page) || subIcons.any { it.display.position.containsKey(page) }

}