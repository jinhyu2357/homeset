package org.example.jinhhyu.homeset

import java.util.UUID
import java.util.Locale
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

internal val HOME_ICON_COLORS = listOf(
    "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
    "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
)

internal fun nextHomeIconColor(current: String): String =
    HOME_ICON_COLORS[(HOME_ICON_COLORS.indexOf(current) + 1) % HOME_ICON_COLORS.size]

internal fun localizedHomeSection(base: String, language: String): String =
    "${base}_${language.lowercase(Locale.ROOT)}"

enum class HomesViewMode(
    val title: String,
    val allowsDeletion: Boolean,
    val allowsSharedManagement: Boolean
) {
    PERSONAL("Private Homes", true, false),
    SHARED("Shared Homes", false, true)
}

class HomesGui(
    private val plugin: JavaPlugin,
    private val onTeleport: (Player, String, HomesViewMode) -> Unit,
    private val onDeleteConfirmed: (Player, String, Int, HomesViewMode) -> Unit,
    private val onHomeIconChanged: (Player, String, String, Int) -> Unit,
    private val onSharedHomeUpdateConfirmed: (Player, String, Int) -> Unit,
    private val canManageSharedHomes: (Player) -> Boolean,
    private val onViewChanged: (Player, HomesViewMode) -> Unit
) : Listener {
    private companion object {
        const val INVENTORY_SIZE = 54
        const val PREVIOUS_SLOT = 45
        const val PAGE_SLOT = 49
        const val VIEW_SLOT = 51
        const val NEXT_SLOT = 53
    }

    private enum class Action {
        INFO,
        TELEPORT,
        DELETE,
        CHANGE_ICON,
        PREVIOUS,
        NEXT,
        VIEW_PERSONAL,
        VIEW_SHARED,
        UPDATE_SHARED,
        CONFIRM_DELETE,
        CANCEL_DELETE,
        CONFIRM_SHARED_UPDATE,
        CANCEL_SHARED_UPDATE
    }

    private sealed class GuiHolder(val ownerId: UUID) : InventoryHolder {
        lateinit var backingInventory: Inventory

        override fun getInventory(): Inventory = backingInventory
    }

    private class HomesHolder(
        ownerId: UUID,
        val homes: List<HomeListEntry>,
        val viewMode: HomesViewMode,
        val page: Int
    ) : GuiHolder(ownerId)

    private class DeleteHolder(
        ownerId: UUID,
        val homes: List<HomeListEntry>,
        val homeName: String,
        val page: Int,
        val viewMode: HomesViewMode
    ) : GuiHolder(ownerId)

    private class SharedUpdateHolder(
        ownerId: UUID,
        val homes: List<HomeListEntry>,
        val homeName: String,
        val page: Int
    ) : GuiHolder(ownerId)

    private val actionKey = NamespacedKey(plugin, "homes_gui_action")
    private val homeNameKey = NamespacedKey(plugin, "homes_gui_home")
    private val pageKey = NamespacedKey(plugin, "homes_gui_page")

    fun open(
        player: Player,
        homes: List<HomeListEntry>,
        viewMode: HomesViewMode = HomesViewMode.PERSONAL,
        requestedPage: Int = 0
    ) {
        val pageCount = homesPageCount(homes.size)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        val holder = HomesHolder(player.uniqueId, homes, viewMode, page)
        val viewTitle = guiText(
            player,
            if (viewMode == HomesViewMode.PERSONAL) "personal_homes" else "shared_homes",
            viewMode.title
        )
        val inventory = Bukkit.createInventory(
            holder,
            INVENTORY_SIZE,
            uiText("$viewTitle · ${page + 1}/$pageCount", NamedTextColor.DARK_GREEN)
        )
        holder.backingInventory = inventory

        val firstHomeIndex = page * HOMES_PER_PAGE
        homes.subList(firstHomeIndex, minOf(firstHomeIndex + HOMES_PER_PAGE, homes.size))
            .forEachIndexed { row, entry -> addHomeRow(player, inventory, row, entry, viewMode) }

        if (homes.isEmpty()) {
            inventory.setItem(
                22,
                guiItem(
                    Material.BARRIER,
                    if (viewMode == HomesViewMode.SHARED) {
                        guiText(player, "no_shared_homes", "No shared homes")
                    } else {
                        guiText(player, "no_private_homes", "No private homes")
                    },
                    NamedTextColor.GRAY,
                    action = Action.INFO
                )
            )
        }
        if (page > 0) {
            inventory.setItem(
                PREVIOUS_SLOT,
                guiItem(
                    Material.ARROW,
                    guiText(player, "previous", "Previous"),
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
                guiText(
                    player,
                    "page",
                    "Page {current} / {total}",
                    mapOf("current" to (page + 1).toString(), "total" to pageCount.toString())
                ),
                NamedTextColor.GOLD,
                action = Action.INFO
            )
        )
        val targetView = if (viewMode == HomesViewMode.PERSONAL) HomesViewMode.SHARED else HomesViewMode.PERSONAL
        inventory.setItem(
            VIEW_SLOT,
            guiItem(
                if (targetView == HomesViewMode.SHARED) Material.ENDER_CHEST else Material.WHITE_BED,
                guiText(
                    player,
                    if (targetView == HomesViewMode.PERSONAL) "view_personal_homes" else "view_shared_homes",
                    "View ${targetView.title}"
                ),
                NamedTextColor.AQUA,
                action = if (targetView == HomesViewMode.SHARED) Action.VIEW_SHARED else Action.VIEW_PERSONAL
            )
        )
        if (page + 1 < pageCount) {
            inventory.setItem(
                NEXT_SLOT,
                guiItem(
                    Material.ARROW,
                    guiText(player, "next", "Next"),
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
            is SharedUpdateHolder -> handleSharedUpdateClick(
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

    private fun addHomeRow(
        player: Player,
        inventory: Inventory,
        row: Int,
        entry: HomeListEntry,
        viewMode: HomesViewMode
    ) {
        val firstSlot = row * 9
        val home = entry.home
        inventory.setItem(
            firstSlot,
            guiItem(
                if (viewMode == HomesViewMode.SHARED) Material.CYAN_BED else homeIcon(entry.iconColor),
                entry.name,
                NamedTextColor.GOLD,
                listOfNotNull(
                    guiText(
                        player,
                        "world",
                        "World: {world}",
                        mapOf("world" to home.worldName)
                    ),
                    "X: ${blockCoordinate(home.x)}",
                    "Y: ${blockCoordinate(home.y)}",
                    "Z: ${blockCoordinate(home.z)}",
                    guiText(player, "change_color", "Click to change color")
                        .takeIf { viewMode == HomesViewMode.PERSONAL }
                ),
                if (viewMode == HomesViewMode.PERSONAL) Action.CHANGE_ICON else Action.INFO,
                entry.name
            )
        )
        inventory.setItem(
            firstSlot + 7,
            guiItem(
                Material.ARROW,
                guiText(player, "teleport", "Teleport"),
                NamedTextColor.GREEN,
                listOf(
                    guiText(
                        player,
                        "teleport_to",
                        "Teleport to {home}",
                        mapOf("home" to entry.name)
                    )
                ),
                Action.TELEPORT,
                entry.name
            )
        )
        if (viewMode.allowsDeletion) {
            inventory.setItem(
                firstSlot + 8,
                guiItem(
                    Material.LAVA_BUCKET,
                    guiText(player, "delete_home", "Delete Home"),
                    NamedTextColor.RED,
                    listOf(guiText(player, "delete", "Delete {home}", mapOf("home" to entry.name))),
                    Action.DELETE,
                    entry.name
                )
            )
        } else if (viewMode.allowsSharedManagement && canManageSharedHomes(player)) {
            inventory.setItem(
                firstSlot + 6,
                guiItem(
                    Material.LAVA_BUCKET,
                    guiText(player, "delete_shared_home", "Delete Shared Home"),
                    NamedTextColor.RED,
                    listOf(guiText(player, "delete", "Delete {home}", mapOf("home" to entry.name))),
                    Action.DELETE,
                    entry.name
                )
            )
            inventory.setItem(
                firstSlot + 8,
                guiItem(
                    Material.WHITE_BED,
                    guiText(player, "update_shared_home", "Update Shared Home"),
                    NamedTextColor.AQUA,
                    listOf(
                        guiText(
                            player,
                            "set_to_current_location",
                            "Set {home} to your current location",
                            mapOf("home" to entry.name)
                        )
                    ),
                    Action.UPDATE_SHARED,
                    entry.name
                )
            )
        }
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
                if (holder.find(homeName) == null) return
                nextTick(player) {
                    player.closeInventory()
                    onTeleport(player, homeName!!, holder.viewMode)
                }
            }
            Action.DELETE -> {
                if (
                    (!holder.viewMode.allowsDeletion &&
                        !(holder.viewMode.allowsSharedManagement && canManageSharedHomes(player))) ||
                    holder.find(homeName) == null
                ) return
                nextTick(player) { openDeleteConfirmation(player, holder, homeName!!) }
            }
            Action.UPDATE_SHARED -> {
                if (
                    !holder.viewMode.allowsSharedManagement ||
                    !canManageSharedHomes(player) ||
                    holder.find(homeName) == null
                ) return
                nextTick(player) { openSharedUpdateConfirmation(player, holder, homeName!!) }
            }
            Action.CHANGE_ICON -> {
                val home = holder.find(homeName) ?: return
                if (holder.viewMode != HomesViewMode.PERSONAL) return
                nextTick(player) {
                    onHomeIconChanged(player, home.name, nextHomeIconColor(home.iconColor), holder.page)
                }
            }
            Action.PREVIOUS, Action.NEXT -> {
                if (targetPage == null || targetPage !in 0 until homesPageCount(holder.homes.size)) return
                nextTick(player) { open(player, holder.homes, holder.viewMode, targetPage) }
            }
            Action.VIEW_PERSONAL -> nextTick(player) { onViewChanged(player, HomesViewMode.PERSONAL) }
            Action.VIEW_SHARED -> nextTick(player) { onViewChanged(player, HomesViewMode.SHARED) }
            else -> Unit
        }
    }

    private fun handleSharedUpdateClick(
        player: Player,
        holder: SharedUpdateHolder,
        action: Action,
        taggedHomeName: String?
    ) {
        when (action) {
            Action.CONFIRM_SHARED_UPDATE -> {
                if (taggedHomeName != holder.homeName) return
                nextTick(player) { onSharedHomeUpdateConfirmed(player, holder.homeName, holder.page) }
            }
            Action.CANCEL_SHARED_UPDATE -> nextTick(player) {
                open(player, holder.homes, HomesViewMode.SHARED, holder.page)
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
                nextTick(player) {
                    onDeleteConfirmed(player, holder.homeName, holder.page, holder.viewMode)
                }
            }
            Action.CANCEL_DELETE -> nextTick(player) {
                open(player, holder.homes, holder.viewMode, holder.page)
            }
            else -> Unit
        }
    }

    private fun openDeleteConfirmation(player: Player, homesHolder: HomesHolder, homeName: String) {
        val holder = DeleteHolder(
            player.uniqueId,
            homesHolder.homes,
            homeName,
            homesHolder.page,
            homesHolder.viewMode
        )
        val inventory = Bukkit.createInventory(
            holder,
            9,
            uiText(
                guiText(
                    player,
                    "delete_title",
                    "Delete \"{home}\"?",
                    mapOf("home" to homeName)
                ),
                NamedTextColor.DARK_RED
            )
        )
        holder.backingInventory = inventory
        inventory.setItem(
            2,
            guiItem(
                Material.LIME_WOOL,
                guiText(player, "confirm", "Confirm"),
                NamedTextColor.GREEN,
                listOf(
                    guiText(
                        player,
                        "delete_permanently",
                        "Delete {home} permanently",
                        mapOf("home" to homeName)
                    )
                ),
                Action.CONFIRM_DELETE,
                homeName
            )
        )
        inventory.setItem(
            6,
            guiItem(
                Material.BARRIER,
                guiText(player, "cancel", "Cancel"),
                NamedTextColor.RED,
                action = Action.CANCEL_DELETE,
                homeName = homeName
            )
        )
        player.openInventory(inventory)
    }

    private fun openSharedUpdateConfirmation(player: Player, homesHolder: HomesHolder, homeName: String) {
        val holder = SharedUpdateHolder(player.uniqueId, homesHolder.homes, homeName, homesHolder.page)
        val inventory = Bukkit.createInventory(
            holder,
            9,
            uiText(
                guiText(
                    player,
                    "update_title",
                    "Update \"{home}\"?",
                    mapOf("home" to homeName)
                ),
                NamedTextColor.DARK_AQUA
            )
        )
        holder.backingInventory = inventory
        inventory.setItem(
            2,
            guiItem(
                Material.WHITE_BED,
                guiText(player, "set_current_location", "Set Current Location"),
                NamedTextColor.GREEN,
                listOf(
                    guiText(
                        player,
                        "replace_saved_location",
                        "Replace {home}'s saved location",
                        mapOf("home" to homeName)
                    )
                ),
                Action.CONFIRM_SHARED_UPDATE,
                homeName
            )
        )
        inventory.setItem(
            6,
            guiItem(
                Material.BARRIER,
                guiText(player, "cancel", "Cancel"),
                NamedTextColor.RED,
                action = Action.CANCEL_SHARED_UPDATE,
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

    private fun HomesHolder.find(homeName: String?): HomeListEntry? =
        homes.firstOrNull { it.name == homeName }

    private fun nextTick(player: Player, action: () -> Unit) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (player.isOnline) action()
        })
    }

    private fun blockCoordinate(value: Double): Int = floor(value).toInt()

    private fun homeIcon(color: String): Material =
        Material.matchMaterial("${color}_BED") ?: Material.WHITE_BED

    private fun guiText(
        player: Player,
        key: String,
        fallback: String,
        placeholders: Map<String, String> = emptyMap()
    ): String {
        val section = localizedHomeSection("gui", player.locale().language)
        val template = plugin.config.getString("$section.$key")
            ?: plugin.config.getString("gui.$key")
            ?: fallback
        return placeholders.entries.fold(template) { text, (placeholder, value) ->
            text.replace("{$placeholder}", value)
        }
    }

    private fun uiText(value: String, color: NamedTextColor): Component =
        Component.text(value, color).decoration(TextDecoration.ITALIC, false)
}
