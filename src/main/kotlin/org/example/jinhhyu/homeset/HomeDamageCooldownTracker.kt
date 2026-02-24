package org.example.jinhhyu.homeset

import java.util.UUID
import kotlin.math.ceil
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class HomeDamageCooldownTracker(private val plugin: JavaPlugin) : Listener {
    private val lastDamageAtMillis = mutableMapOf<UUID, Long>()

    fun getCooldownSeconds(): Long {
        return plugin.config.getLong("settings.home_damage_cooldown_seconds", 0L).coerceAtLeast(0L)
    }

    fun getRemainingSeconds(playerId: UUID): Long {
        val cooldownSeconds = getCooldownSeconds()
        if (cooldownSeconds <= 0L) {
            return 0L
        }

        val lastDamageMillis = lastDamageAtMillis[playerId] ?: return 0L
        val elapsedMillis = System.currentTimeMillis() - lastDamageMillis
        val cooldownMillis = cooldownSeconds * 1000L
        val remainingMillis = cooldownMillis - elapsedMillis
        if (remainingMillis <= 0L) {
            return 0L
        }

        return ceil(remainingMillis / 1000.0).toLong()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (event.finalDamage <= 0.0) {
            return
        }

        lastDamageAtMillis[player.uniqueId] = System.currentTimeMillis()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        lastDamageAtMillis.remove(event.player.uniqueId)
    }
}
