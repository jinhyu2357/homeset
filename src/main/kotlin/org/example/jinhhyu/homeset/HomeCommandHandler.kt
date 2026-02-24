package org.example.jinhhyu.homeset

import java.sql.SQLException
import java.util.Locale
import java.util.UUID
import java.util.logging.Level
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class HomeCommandHandler(
    private val plugin: JavaPlugin,
    private val homeRepository: HomeRepository,
    private val homeDamageCooldownTracker: HomeDamageCooldownTracker
) : Listener {
    private data class PendingHomeTeleport(
        val task: BukkitTask,
        val startLocation: Location
    )

    private val defaultHomeName = "default"
    private val homeNamePattern = Regex("^[A-Za-z0-9_-]{1,32}$")
    private val pendingHomeTeleports = mutableMapOf<UUID, PendingHomeTeleport>()

    fun executeCommand(
        sender: CommandSender,
        commandName: String,
        args: Array<out String>
    ): Boolean {
        val normalizedCommandName = commandName.lowercase(Locale.ROOT)
        if (normalizedCommandName == "homesetreload") {
            return handleReloadConfig(sender, args)
        }

        if (sender !is Player) {
            sendConfiguredMessage(sender, "only_players")
            return true
        }

        return when (normalizedCommandName) {
            "sethome" -> handleSetHome(sender, args)
            "home" -> handleHome(sender, args)
            "delhome" -> handleDeleteHome(sender, args)
            "homes" -> handleListHomes(sender, args)
            else -> false
        }
    }

    fun completeCommand(
        sender: CommandSender,
        commandName: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player || args.size != 1) {
            return emptyList()
        }

        return when (commandName.lowercase(Locale.ROOT)) {
            "sethome", "home", "delhome" -> {
                val prefix = args[0].lowercase(Locale.ROOT)
                val suggestions = mutableSetOf(defaultHomeName)
                suggestions.addAll(loadHomesSafely(sender))
                suggestions.filter { it.startsWith(prefix) }.sorted()
            }
            else -> emptyList()
        }
    }

    private fun handleSetHome(player: Player, args: Array<out String>): Boolean {
        if (args.size > 1) {
            sendConfiguredMessage(player, "usage_sethome")
            return true
        }

        val homeName = resolveHomeName(args)
        if (!isValidHomeName(homeName)) {
            sendConfiguredMessage(player, "invalid_home_name_detailed")
            return true
        }

        return try {
            val maxHomesPerPlayer = getMaxHomesPerPlayer()
            if (maxHomesPerPlayer > 0) {
                val existingHomes = homeRepository.listHomes(player.uniqueId)
                if (homeName !in existingHomes && existingHomes.size >= maxHomesPerPlayer) {
                    sendConfiguredMessage(
                        player,
                        "sethome_limit_reached",
                        mapOf("max_homes" to maxHomesPerPlayer.toString())
                    )
                    return true
                }
            }

            homeRepository.saveHome(player.uniqueId, homeName, player.location)
            sendConfiguredMessage(player, "sethome_success", mapOf("home_name" to homeName))
            true
        } catch (exception: SQLException) {
            plugin.logger.warning("Failed to save home '$homeName' for ${player.uniqueId}: ${exception.message}")
            sendConfiguredMessage(player, "sethome_db_error")
            true
        }
    }

    private fun handleReloadConfig(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.isNotEmpty()) {
            sendConfiguredMessage(sender, "usage_homesetreload")
            return true
        }

        return try {
            plugin.reloadConfig()
            sendConfiguredMessage(sender, "reload_success")
            true
        } catch (exception: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to reload homeset config.", exception)
            sendConfiguredMessage(sender, "reload_failed")
            true
        }
    }

    private fun handleHome(player: Player, args: Array<out String>): Boolean {
        if (args.size > 1) {
            sendConfiguredMessage(player, "usage_home")
            return true
        }

        val remainingSeconds = homeDamageCooldownTracker.getRemainingSeconds(player.uniqueId)
        if (remainingSeconds > 0L) {
            sendConfiguredMessage(
                player,
                "home_damage_cooldown",
                mapOf(
                    "cooldown_seconds" to homeDamageCooldownTracker.getCooldownSeconds().toString(),
                    "remaining_seconds" to remainingSeconds.toString()
                )
            )
            return true
        }

        val homeName = resolveHomeName(args)
        if (!isValidHomeName(homeName)) {
            sendConfiguredMessage(player, "invalid_home_name")
            return true
        }

        return try {
            val home = homeRepository.findHome(player.uniqueId, homeName)
            if (home == null) {
                sendConfiguredMessage(player, "home_not_found", mapOf("home_name" to homeName))
                return true
            }

            cancelPendingHomeTeleport(player.uniqueId)
            val delaySeconds = getHomeTeleportDelaySeconds()
            if (delaySeconds <= 0L) {
                teleportPlayerToHome(player, homeName, home)
            } else {
                scheduleHomeTeleport(player, homeName, home, delaySeconds)
            }
            true
        } catch (exception: SQLException) {
            plugin.logger.warning("Failed to load home '$homeName' for ${player.uniqueId}: ${exception.message}")
            sendConfiguredMessage(player, "home_db_error")
            true
        }
    }

    private fun handleDeleteHome(player: Player, args: Array<out String>): Boolean {
        if (args.size > 1) {
            sendConfiguredMessage(player, "usage_delhome")
            return true
        }

        val homeName = resolveHomeName(args)
        if (!isValidHomeName(homeName)) {
            sendConfiguredMessage(player, "invalid_home_name")
            return true
        }

        return try {
            val deleted = homeRepository.deleteHome(player.uniqueId, homeName)
            if (deleted) {
                sendConfiguredMessage(player, "delhome_success", mapOf("home_name" to homeName))
            } else {
                sendConfiguredMessage(player, "home_not_found", mapOf("home_name" to homeName))
            }
            true
        } catch (exception: SQLException) {
            plugin.logger.warning("Failed to delete home '$homeName' for ${player.uniqueId}: ${exception.message}")
            sendConfiguredMessage(player, "delhome_db_error")
            true
        }
    }

    private fun handleListHomes(player: Player, args: Array<out String>): Boolean {
        if (args.isNotEmpty()) {
            sendConfiguredMessage(player, "usage_homes")
            return true
        }

        return try {
            val homes = homeRepository.listHomes(player.uniqueId)
            if (homes.isEmpty()) {
                sendConfiguredMessage(player, "homes_empty")
            } else {
                sendConfiguredMessage(
                    player,
                    "homes_list",
                    mapOf("homes" to homes.joinToString(", "))
                )
            }
            true
        } catch (exception: SQLException) {
            plugin.logger.warning("Failed to list homes for ${player.uniqueId}: ${exception.message}")
            sendConfiguredMessage(player, "homes_db_error")
            true
        }
    }

    private fun loadHomesSafely(player: Player): List<String> {
        return try {
            homeRepository.listHomes(player.uniqueId)
        } catch (exception: SQLException) {
            plugin.logger.warning("Failed to load tab completion homes for ${player.uniqueId}: ${exception.message}")
            emptyList()
        }
    }

    private fun resolveHomeName(args: Array<out String>): String {
        return if (args.isEmpty()) defaultHomeName else normalizeHomeName(args[0])
    }

    private fun getMaxHomesPerPlayer(): Int {
        return plugin.config.getInt("settings.max_homes_per_player", 0).coerceAtLeast(0)
    }

    private fun getHomeTeleportDelaySeconds(): Long {
        return plugin.config.getLong("settings.home_teleport_delay_seconds", 0L).coerceAtLeast(0L)
    }

    private fun scheduleHomeTeleport(
        player: Player,
        homeName: String,
        home: HomeRecord,
        delaySeconds: Long
    ) {
        cancelPendingHomeTeleport(player.uniqueId)
        sendConfiguredMessage(
            player,
            "home_delay_started",
            mapOf(
                "home_name" to homeName,
                "delay_seconds" to delaySeconds.toString()
            )
        )

        val playerId = player.uniqueId
        val delayTicks = delaySeconds * 20L
        val startLocation = player.location.clone()
        val task = plugin.server.scheduler.runTaskLater(plugin, Runnable {
            pendingHomeTeleports.remove(playerId)
            val onlinePlayer = Bukkit.getPlayer(playerId) ?: return@Runnable

            val remainingSeconds = homeDamageCooldownTracker.getRemainingSeconds(playerId)
            if (remainingSeconds > 0L) {
                sendConfiguredMessage(
                    onlinePlayer,
                    "home_damage_cooldown",
                    mapOf(
                        "cooldown_seconds" to homeDamageCooldownTracker.getCooldownSeconds().toString(),
                        "remaining_seconds" to remainingSeconds.toString()
                    )
                )
                return@Runnable
            }

            teleportPlayerToHome(onlinePlayer, homeName, home)
        }, delayTicks)

        pendingHomeTeleports[playerId] = PendingHomeTeleport(task, startLocation)
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val pendingTeleport = pendingHomeTeleports[event.player.uniqueId] ?: return
        val toLocation = event.to ?: return
        if (!hasMovedBlocks(pendingTeleport.startLocation, toLocation)) {
            return
        }

        pendingTeleport.task.cancel()
        pendingHomeTeleports.remove(event.player.uniqueId)
        sendConfiguredMessage(event.player, "home_delay_cancelled_moved")
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        cancelPendingHomeTeleport(event.player.uniqueId)
    }

    private fun hasMovedBlocks(from: Location, to: Location): Boolean {
        if (from.world?.uid != to.world?.uid) {
            return true
        }

        return from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ
    }

    private fun cancelPendingHomeTeleport(playerId: UUID) {
        pendingHomeTeleports.remove(playerId)?.task?.cancel()
    }

    private fun teleportPlayerToHome(player: Player, homeName: String, home: HomeRecord) {
        val world = Bukkit.getWorld(home.worldName)
        if (world == null) {
            sendConfiguredMessage(player, "home_world_unavailable", mapOf("home_name" to homeName))
            return
        }

        val location = Location(world, home.x, home.y, home.z, home.yaw, home.pitch)
        if (player.teleport(location)) {
            sendConfiguredMessage(player, "home_teleport_success", mapOf("home_name" to homeName))
        } else {
            sendConfiguredMessage(player, "home_teleport_failed", mapOf("home_name" to homeName))
        }
    }

    private fun sendConfiguredMessage(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, String> = emptyMap()
    ) {
        sender.sendMessage(resolveMessage(key, placeholders))
    }

    private fun resolveMessage(
        key: String,
        placeholders: Map<String, String> = emptyMap()
    ): String {
        val template = plugin.config.getString("messages.$key") ?: key
        val interpolated = placeholders.entries.fold(template) { message, (placeholder, value) ->
            message.replace("{$placeholder}", value)
        }
        return ChatColor.translateAlternateColorCodes('&', interpolated)
    }

    private fun normalizeHomeName(name: String): String = name.lowercase(Locale.ROOT)

    private fun isValidHomeName(name: String): Boolean = homeNamePattern.matches(name)
}
