package org.example.jinhhyu.homeset

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import java.io.File
import java.util.logging.Level
import org.bukkit.plugin.java.JavaPlugin

class Homeset : JavaPlugin() {
    private lateinit var homeRepository: HomeRepository

    override fun onEnable() {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.severe("Could not create plugin data folder: ${dataFolder.absolutePath}")
            server.pluginManager.disablePlugin(this)
            return
        }
        saveDefaultConfig()

        val databaseFile = File(dataFolder, "homes.db")
        homeRepository = try {
            HomeRepository.connect(databaseFile).also { it.initializeSchema() }
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "Could not initialize homes database.", exception)
            server.pluginManager.disablePlugin(this)
            return
        }

        val homeDamageCooldownTracker = HomeDamageCooldownTracker(this)
        server.pluginManager.registerEvents(homeDamageCooldownTracker, this)
        val commandHandler = HomeCommandHandler(this, homeRepository, homeDamageCooldownTracker)
        server.pluginManager.registerEvents(commandHandler, this)
        try {
            registerPaperCommand("sethome", "Set home with visibility option: share or personal.", "homeset.sethome", commandHandler)
            registerPaperCommand("home", "Teleport to your home or update shared-home status.", "homeset.use", commandHandler)
            registerPaperCommand("delhome", "Delete one of your homes.", "homeset.delhome", commandHandler)
            registerPaperCommand("homes", "View personal or shared home lists.", "homeset.use", commandHandler)
            registerPaperCommand("homesetreload", "Reload the homeset config file.", "homeset.reload", commandHandler)
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "Could not register plugin commands.", exception)
            server.pluginManager.disablePlugin(this)
            return
        }
    }

    override fun onDisable() {
        if (::homeRepository.isInitialized) {
            homeRepository.close()
        }
    }

    private fun registerPaperCommand(
        name: String,
        description: String,
        requiredPermission: String,
        handler: HomeCommandHandler
    ) {
        registerCommand(name, description, object : BasicCommand {
            override fun execute(commandSource: CommandSourceStack, args: Array<String>) {
                handler.executeCommand(commandSource.sender, name, args)
            }

            override fun suggest(commandSource: CommandSourceStack, args: Array<String>): Collection<String> {
                return handler.completeCommand(commandSource.sender, name, args)
            }

            override fun permission(): String = requiredPermission
        })
    }
}
