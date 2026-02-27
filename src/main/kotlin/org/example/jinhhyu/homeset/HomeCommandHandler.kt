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
    private enum class SetHomeVisibilityOption {
        SHARED,
        PRIVATE
    }

    private enum class HomesViewMode {
        SHARED,
        PERSONAL
    }

    private data class PendingHomeTeleport(
        val task: BukkitTask,
        val startLocation: Location
    )

    private val defaultHomeName = "default"
    private val homeNamePattern = Regex("^[A-Za-z0-9_-]{1,32}$")
    private val shareRegisteredValues = setOf("true", "yes", "y", "1", "on", "registered", "share", "shared")
    private val shareUnregisteredValues = setOf("false", "no", "n", "0", "off", "unregistered", "unshare")
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
            "homes" -> handleHomesCommand(sender, args)
            else -> false
        }
    }

    fun completeCommand(
        sender: CommandSender,
        commandName: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player || args.isEmpty()) {
            return emptyList()
        }

        return when (commandName.lowercase(Locale.ROOT)) {
            "sethome" -> {
                when (args.size) {
                    1 -> {
                        val prefix = args[0].lowercase(Locale.ROOT)
                        val suggestions = mutableSetOf(defaultHomeName)
                        suggestions.addAll(loadHomesSafely(sender))
                        suggestions.filter { it.startsWith(prefix) }.sorted()
                    }
                    2 -> suggestSetHomeOptions(sender, args[1])
                    else -> emptyList()
                }
            }
            "delhome" -> {
                if (args.size != 1) {
                    return emptyList()
                }

                val prefix = args[0].lowercase(Locale.ROOT)
                val suggestions = mutableSetOf(defaultHomeName)
                suggestions.addAll(loadHomesSafely(sender))
                suggestions.filter { it.startsWith(prefix) }.sorted()
            }
            "home" -> {
                when (args.size) {
                    1 -> {
                        val prefix = args[0].lowercase(Locale.ROOT)
                        val suggestions = mutableSetOf(defaultHomeName)
                        suggestions.addAll(loadHomesSafely(sender))
                        suggestions.addAll(loadSharedHomesSafely())
                        suggestions.filter { it.startsWith(prefix) }.sorted()
                    }
                    2 -> {
                        if (!hasSharedHomeManagePermission(sender)) {
                            emptyList()
                        } else {
                            suggestShareStates(args[1])
                        }
                    }
                    else -> emptyList()
                }
            }
            "homes" -> {
                if (args.size != 1) {
                    return emptyList()
                }

                suggestHomesViewModes(args[0])
            }
            else -> emptyList()
        }
    }

    private fun handleSetHome(player: Player, args: Array<out String>): Boolean {
        if (args.size > 2) {
            sendConfiguredMessage(player, "usage_sethome")
            return true
        }

        val homeName = if (args.isEmpty()) defaultHomeName else normalizeHomeName(args[0])
        if (!isValidHomeName(homeName)) {
            sendConfiguredMessage(player, "invalid_home_name_detailed")
            return true
        }

        val visibilityOption = when (args.size) {
            0, 1 -> SetHomeVisibilityOption.PRIVATE
            2 -> parseSetHomeVisibilityOption(args[1]) ?: run {
                sendConfiguredMessage(player, "invalid_sethome_option")
                return true
            }
            else -> {
                sendConfiguredMessage(player, "usage_sethome")
                return true
            }
        }

        if (visibilityOption == SetHomeVisibilityOption.SHARED && !hasSharedHomeManagePermission(player)) {
            sendConfiguredMessage(
                player,
                "sethome_shared_permission_denied",
                mapOf("permission" to getSharedHomeManagePermissionDisplay())
            )
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

            if (
                visibilityOption == SetHomeVisibilityOption.SHARED &&
                homeRepository.isSharedHomeNameTakenByAnotherPlayer(player.uniqueId, homeName)
            ) {
                sendConfiguredMessage(player, "home_share_name_taken", mapOf("home_name" to homeName))
                return true
            }

            homeRepository.saveHome(player.uniqueId, homeName, player.location)
            val shouldShare = visibilityOption == SetHomeVisibilityOption.SHARED
            homeRepository.setHomeShared(player.uniqueId, homeName, shouldShare)
            val successMessageKey = if (shouldShare) "sethome_success_shared" else "sethome_success_private"
            sendConfiguredMessage(player, successMessageKey, mapOf("home_name" to homeName))
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
        return when (args.size) {
            0, 1 -> handleHomeTeleport(player, args)
            2 -> handleHomeShareToggle(player, args)
            else -> {
                sendConfiguredMessage(player, "usage_home")
                true
            }
        }
    }

    private fun handleHomeTeleport(player: Player, args: Array<out String>): Boolean {
        if (!canUseHomeTeleport(player)) {
            return true
        }

        val homeName = resolveHomeName(args)
        if (!isValidHomeName(homeName)) {
            sendConfiguredMessage(player, "invalid_home_name")
            return true
        }

        return try {
            val home = homeRepository.findPersonalHome(player.uniqueId, homeName)
                ?: homeRepository.findSharedHome(homeName)
            if (home == null) {
                sendConfiguredMessage(player, "home_not_found", mapOf("home_name" to homeName))
                return true
            }

            teleportWithConfiguredDelay(player, homeName, home)
            true
        } catch (exception: SQLException) {
            plugin.logger.warning("Failed to load home '$homeName' for ${player.uniqueId}: ${exception.message}")
            sendConfiguredMessage(player, "home_db_error")
            true
        }
    }

    private fun handleHomeShareToggle(player: Player, args: Array<out String>): Boolean {
        val homeName = normalizeHomeName(args[0])
        if (!isValidHomeName(homeName)) {
            sendConfiguredMessage(player, "invalid_home_name_detailed")
            return true
        }

        return try {
            if (!homeRepository.homeExists(player.uniqueId, homeName)) {
                sendConfiguredMessage(player, "home_not_found", mapOf("home_name" to homeName))
                return true
            }

            if (!hasSharedHomeManagePermission(player)) {
                homeRepository.setHomeShared(player.uniqueId, homeName, false)
                sendConfiguredMessage(
                    player,
                    "home_share_permission_denied",
                    mapOf("permission" to getSharedHomeManagePermissionDisplay())
                )
                return true
            }

            // Default option is "unregistered" when the provided option is not recognized.
            val shouldShare = parseShareFlag(args[1]) ?: false
            if (shouldShare && homeRepository.isSharedHomeNameTakenByAnotherPlayer(player.uniqueId, homeName)) {
                sendConfiguredMessage(player, "home_share_name_taken", mapOf("home_name" to homeName))
                return true
            }

            homeRepository.setHomeShared(player.uniqueId, homeName, shouldShare)
            val messageKey = if (shouldShare) "home_share_added" else "home_share_removed"
            sendConfiguredMessage(player, messageKey, mapOf("home_name" to homeName))
            true
        } catch (exception: SQLException) {
            plugin.logger.warning("Failed to update share state for home '$homeName' (${player.uniqueId}): ${exception.message}")
            sendConfiguredMessage(player, "home_share_db_error")
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

    private fun handleHomesCommand(player: Player, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            return handleHomesList(player, HomesViewMode.PERSONAL)
        }

        if (args.size != 1) {
            sendConfiguredMessage(player, "usage_homes")
            return true
        }

        val viewMode = parseHomesViewMode(args[0])
        if (viewMode == null) {
            sendConfiguredMessage(player, "invalid_homes_view_mode")
            return true
        }

        return handleHomesList(player, viewMode)
    }

    private fun handleHomesList(player: Player, viewMode: HomesViewMode): Boolean {
        return try {
            when (viewMode) {
                HomesViewMode.PERSONAL -> {
                    val homes = homeRepository.listPersonalHomes(player.uniqueId)
                    if (homes.isEmpty()) {
                        sendConfiguredMessage(player, "homes_empty")
                    } else {
                        sendConfiguredMessage(
                            player,
                            "homes_list",
                            mapOf("homes" to homes.joinToString(", "))
                        )
                    }
                }
                HomesViewMode.SHARED -> {
                    val sharedHomes = homeRepository.listSharedHomes()
                    if (sharedHomes.isEmpty()) {
                        sendConfiguredMessage(player, "shared_homes_empty")
                    } else {
                        sendConfiguredMessage(
                            player,
                            "shared_homes_list",
                            mapOf("homes" to sharedHomes.joinToString(", "))
                        )
                    }
                }
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

    private fun loadSharedHomesSafely(): List<String> {
        return try {
            homeRepository.listSharedHomes()
        } catch (exception: SQLException) {
            plugin.logger.warning("Failed to load tab completion shared homes: ${exception.message}")
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

    private fun getSharedHomeManagePermission(): String {
        return plugin.config.getString("settings.shared_home_manage_permission", "homeset.share").orEmpty()
    }

    private fun getSharedHomeManagePermissionDisplay(): String {
        val permission = getSharedHomeManagePermission().trim()
        return if (permission.equals("homeset.share", ignoreCase = true)) "op" else permission
    }

    private fun hasSharedHomeManagePermission(player: Player): Boolean {
        val permission = getSharedHomeManagePermission().trim()
        if (permission.isBlank()) {
            return true
        }

        if (permission.equals("homeset.share", ignoreCase = true)) {
            return player.isOp
        }

        return player.hasPermission(permission)
    }

    private fun parseShareFlag(rawValue: String): Boolean? {
        val normalized = rawValue.lowercase(Locale.ROOT)
        return when {
            normalized in shareRegisteredValues -> true
            normalized in shareUnregisteredValues -> false
            else -> null
        }
    }

    private fun parseSetHomeVisibilityOption(rawValue: String): SetHomeVisibilityOption? {
        return when (rawValue.lowercase(Locale.ROOT)) {
            "s", "share", "shared" -> SetHomeVisibilityOption.SHARED
            "p", "personal", "private" -> SetHomeVisibilityOption.PRIVATE
            else -> null
        }
    }

    private fun suggestSetHomeOptions(player: Player, rawPrefix: String): List<String> {
        val prefix = rawPrefix.lowercase(Locale.ROOT)
        val options = mutableListOf("personal")
        if (hasSharedHomeManagePermission(player)) {
            options += "share"
        }
        return options.filter { it.startsWith(prefix) }
    }

    private fun suggestShareStates(rawPrefix: String): List<String> {
        val prefix = rawPrefix.lowercase(Locale.ROOT)
        return listOf("registered", "unregistered").filter { it.startsWith(prefix) }
    }

    private fun parseHomesViewMode(rawValue: String): HomesViewMode? {
        return when (rawValue.lowercase(Locale.ROOT)) {
            "share", "shared" -> HomesViewMode.SHARED
            "personal", "private", "mine", "my" -> HomesViewMode.PERSONAL
            else -> null
        }
    }

    private fun suggestHomesViewModes(rawPrefix: String): List<String> {
        val prefix = rawPrefix.lowercase(Locale.ROOT)
        return listOf("share", "personal").filter { it.startsWith(prefix) }
    }

    private fun canUseHomeTeleport(player: Player): Boolean {
        val remainingSeconds = homeDamageCooldownTracker.getRemainingSeconds(player.uniqueId)
        if (remainingSeconds <= 0L) {
            return true
        }

        sendConfiguredMessage(
            player,
            "home_damage_cooldown",
            mapOf(
                "cooldown_seconds" to homeDamageCooldownTracker.getCooldownSeconds().toString(),
                "remaining_seconds" to remainingSeconds.toString()
            )
        )
        return false
    }

    private fun teleportWithConfiguredDelay(player: Player, homeName: String, home: HomeRecord) {
        cancelPendingHomeTeleport(player.uniqueId)
        val delaySeconds = getHomeTeleportDelaySeconds()
        if (delaySeconds <= 0L) {
            teleportPlayerToHome(player, homeName, home)
            return
        }

        scheduleHomeTeleport(player, homeName, home, delaySeconds)
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
