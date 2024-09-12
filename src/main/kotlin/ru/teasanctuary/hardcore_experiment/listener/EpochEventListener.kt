package ru.teasanctuary.hardcore_experiment.listener

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.HumanEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import ru.teasanctuary.hardcore_experiment.HardcoreExperiment
import ru.teasanctuary.hardcore_experiment.types.WorldEpoch

class EpochEventListener(private val plugin: HardcoreExperiment) : Listener {
    /**
     * Особый случай: апгрейд до эпохи Obsidian в случае успешного зачарования предмета.
     */
    @EventHandler(ignoreCancelled = true)
    fun onItemEnchant(event: EnchantItemEvent) {
        // Предотвратим улучшение эпохи, когда оператор зачарует предмет.
        if (event.enchanter.isOp) return

        tryAllowEpoch(event.enchanter, WorldEpoch.Obsidian)
    }

    @EventHandler
    fun onPlayerPickItem(event: PlayerAttemptPickupItemEvent) {
        // Предотвратим улучшение эпохи, когда оператор поднимает предмет.
        if (event.player.isOp) return

        checkEpoch(event.player, event.item.itemStack.type)
    }

    @EventHandler
    fun onPlayerCraft(event: CraftItemEvent) {
        // Предотвратим улучшение эпохи, когда оператор крафтит предмет.
        if (event.whoClicked.isOp) return

        checkEpoch(event.whoClicked, event.recipe.result.type)
    }

    @EventHandler
    fun onFurnaceExtract(event: FurnaceExtractEvent) {
        // Предотвратим улучшение эпохи, когда оператор забирает предмет из печи.
        if (event.player.isOp) return

        checkEpoch(event.player, event.itemType)
    }

    @EventHandler
    fun onClickEvent(event: InventoryClickEvent) {
        // Предотвратим улучшение эпохи, когда оператор кликает по предмету.
        if (event.whoClicked.isOp) return

        val item = event.currentItem
        if (item != null) checkEpoch(event.whoClicked, item.type)
    }

    private fun checkEpoch(caller: HumanEntity, material: Material) {
        val epoch = WorldEpoch.itemToEpoch[material] ?: return

        tryAllowEpoch(caller, epoch)
    }

    private fun tryAllowEpoch(caller: HumanEntity, epoch: WorldEpoch) {
        val isFirstToOpenEpoch = plugin.allowEpoch(epoch)
        if (isFirstToOpenEpoch) plugin.notifyAdmins(
            MiniMessage.miniMessage()
                .deserialize("Игрок <#5555ff>${caller.name}</#5555ff> открыл эпоху <u>${epoch.name}</u>.")
        )
    }
}