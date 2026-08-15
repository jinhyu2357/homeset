package org.example.jinhhyu.homeset

import java.util.UUID
import kotlin.math.floor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

internal const val HOMES_PER_PAGE = 5

internal fun homesPageCount(homeCount: Int): Int =
    if (homeCount <= 0) 1 else (homeCount - 1) / HOMES_PER_PAGE + 1

class HomesGui(
    private val plugin: JavaPlugin,
    private val onTeleport: (Player, String) -> Unit,
    private val onDeleteConfirmed: (Player, String, Int) -> Unit
) : Listener {
    private companion object {
        const val INVENTORY_SIZE = 54
        const val PREVIOUS_SLOT = 45
        const val PAGE_SLOT = 49
        const val NEXT_SLOT = 53
    }

    private enum class Action {
        INFO,
        TELEPORT,
        DELETE,
        PREVIOUS,
        NEXT,
        CONFIRM_DELETE,
        CANCEL_DELETE
    }

    private sealed class GuiHolder(val ownerId: UUID) : InventoryHolder {
        lateinit var backingInventory: Inventory

        override fun getInventory(): Inventory = backingInventory
    }

    private class HomesHolder(
        ownerId: UUID,
        val homes: List<HomeListEntry>,
        val page: Int
    ) : GuiHolder(ownerId)

    private class DeleteHolder(
        ownerId: UUID,
        val homes: List<HomeListEntry>,
        val homeName: String,
        val page: Int
    ) : GuiHolder(ownerId)

    private val actionKey = NamespacedKey(plugin, "homes_gui_action")
    private val homeNameKey = NamespacedKey(plugin, "homes_gui_home")
    private val pageKey = NamespacedKey(plugin, "homes_gui_page")

    fun open(player: Player, homes: List<HomeListEntry>, requestedPage: Int = 0) {
        val pageCount = homesPageCount(homes.size)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        val holder = HomesHolder(player.uniqueId, homes, page)
        val inventory = Bukkit.createInventory(
            holder,
            INVENTORY_SIZE,
            uiText("Homes · ${page + 1}/$pageCount", NamedTextColor.DARK_GREEN)
        )
        holder.backingInventory = inventory

        val firstHomeIndex = page * HOMES_PER_PAGE
        homes.subList(firstHomeIndex, minOf(firstHomeIndex + HOMES_PER_PAGE, homes.size))
            .forEachIndexed { row, entry -> addHomeRow(inventory, row, entry) }

        if (homes.isEmpty()) {
            inventory.setItem(
                22,
                guiItem(Material.BARRIER, "No saved homes", NamedTextColor.GRAY, action = Action.INFO)
            )
        }
        if (page > 0) {
            inventory.setItem(
                PREVIOUS_SLOT,
                guiItem(
                    Material.ARROW,
                    "Previous",
                    NamedTextColor.YELLOW,
                    action = Action.PREVIOUS,
                    targetPage = page - 1
                )
            )
        }
        inventory.setItem(
            PAGE_SLOT,
            guiItem(
                Material.PAPER,
                "Page ${page + 1} / $pageCount",
                NamedTextColor.GOLD,
                action = Action.INFO
            )
        )
        if (page + 1 < pageCount) {
            inventory.setItem(
                NEXT_SLOT,
                guiItem(
                    Material.ARROW,
                    "Next",
                    NamedTextColor.YELLOW,
                    action = Action.NEXT,
                    targetPage = page + 1
                )
            )
        }

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? GuiHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (player.uniqueId != holder.ownerId || event.rawSlot !in 0 until event.view.topInventory.size) {
            return
        }

        val data = event.currentItem?.itemMeta?.persistentDataContainer ?: return
        val actionName = data.get(actionKey, PersistentDataType.STRING) ?: return
        val action = Action.entries.firstOrNull { it.name == actionName } ?: return

        when (holder) {
            is HomesHolder -> handleHomesClick(
                player,
                holder,
                action,
                data.get(homeNameKey, PersistentDataType.STRING),
                data.get(pageKey, PersistentDataType.INTEGER)
            )
            is DeleteHolder -> handleDeleteClick(
                player,
                holder,
                action,
                data.get(homeNameKey, PersistentDataType.STRING)
            )
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is GuiHolder) {
            event.isCancelled = true
        }
    }

    private fun addHomeRow(inventory: Inventory, row: Int, entry: HomeListEntry) {
        val firstSlot = row * 9
        val home = entry.home
        inventory.setItem(
            firstSlot,
            guiItem(
                Material.WHITE_BED,
                entry.name,
                NamedTextColor.GOLD,
                listOf(
                    "World: ${home.worldName}",
                    "X: ${blockCoordinate(home.x)}",
                    "Y: ${blockCoordinate(home.y)}",
                    "Z: ${blockCoordinate(home.z)}"
                ),
                Action.INFO,
                entry.name
            )
        )
        inventory.setItem(
            firstSlot + 7,
            guiItem(
                Material.ARROW,
                "Teleport",
                NamedTextColor.GREEN,
                listOf("Teleport to ${entry.name}"),
                Action.TELEPORT,
                entry.name
            )
        )
        inventory.setItem(
            firstSlot + 8,
            guiItem(
                Material.LAVA_BUCKET,
                "Delete Home",
                NamedTextColor.RED,
                listOf("Delete ${entry.name}"),
                Action.DELETE,
                entry.name
            )
        )
    }

    private fun handleHomesClick(
        player: Player,
        holder: HomesHolder,
        action: Action,
        homeName: String?,
        targetPage: Int?
    ) {
        when (action) {
            Action.TELEPORT -> {
                if (!holder.contains(homeName)) return
                nextTick(player) {
                    player.closeInventory()
                    onTeleport(player, homeName!!)
                }
            }
            Action.DELETE -> {
                if (!holder.contains(homeName)) return
                nextTick(player) { openDeleteConfirmation(player, holder, homeName!!) }
            }
            Action.PREVIOUS, Action.NEXT -> {
                if (targetPage == null || targetPage !in 0 until homesPageCount(holder.homes.size)) return
                nextTick(player) { open(player, holder.homes, targetPage) }
            }
            else -> Unit
        }
    }

    private fun handleDeleteClick(
        player: Player,
        holder: DeleteHolder,
        action: Action,
        taggedHomeName: String?
    ) {
        when (action) {
            Action.CONFIRM_DELETE -> {
                if (taggedHomeName != holder.homeName) return
                nextTick(player) { onDeleteConfirmed(player, holder.homeName, holder.page) }
            }
            Action.CANCEL_DELETE -> nextTick(player) { open(player, holder.homes, holder.page) }
            else -> Unit
        }
    }

    private fun openDeleteConfirmation(player: Player, homesHolder: HomesHolder, homeName: String) {
        val holder = DeleteHolder(player.uniqueId, homesHolder.homes, homeName, homesHolder.page)
        val inventory = Bukkit.createInventory(
            holder,
            9,
            uiText("Delete \"$homeName\"?", NamedTextColor.DARK_RED)
        )
        holder.backingInventory = inventory
        inventory.setItem(
            2,
            guiItem(
                Material.LIME_WOOL,
                "Confirm",
                NamedTextColor.GREEN,
                listOf("Delete $homeName permanently"),
                Action.CONFIRM_DELETE,
                homeName
            )
        )
        inventory.setItem(
            6,
            guiItem(
                Material.BARRIER,
                "Cancel",
                NamedTextColor.RED,
                action = Action.CANCEL_DELETE,
                homeName = homeName
            )
        )
        player.openInventory(inventory)
    }

    private fun guiItem(
        material: Material,
        name: String,
        color: NamedTextColor,
        lore: List<String> = emptyList(),
        action: Action,
        homeName: String? = null,
        targetPage: Int? = null
    ): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(uiText(name, color))
        if (lore.isNotEmpty()) {
            meta.lore(lore.map { uiText(it, NamedTextColor.GRAY) })
        }
        meta.persistentDataContainer.set(actionKey, PersistentDataType.STRING, action.name)
        homeName?.let { meta.persistentDataContainer.set(homeNameKey, PersistentDataType.STRING, it) }
        targetPage?.let { meta.persistentDataContainer.set(pageKey, PersistentDataType.INTEGER, it) }
        item.itemMeta = meta
        return item
    }

    private fun HomesHolder.contains(homeName: String?): Boolean =
        homeName != null && homes.any { it.name == homeName }

    private fun nextTick(player: Player, action: () -> Unit) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline) action()
        })
    }

    private fun blockCoordinate(value: Double): Int = floor(value).toInt()

    private fun uiText(value: String, color: NamedTextColor): Component =
        Component.text(value, color).decoration(TextDecoration.ITALIC, false)
}
