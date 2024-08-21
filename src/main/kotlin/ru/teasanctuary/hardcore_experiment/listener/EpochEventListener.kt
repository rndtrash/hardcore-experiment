package ru.teasanctuary.hardcore_experiment.listener

import org.bukkit.Material
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

        plugin.allowEpoch(WorldEpoch.Obsidian)
    }

    @EventHandler
    fun onPlayerPickItem(event: PlayerAttemptPickupItemEvent) {
        // Предотвратим улучшение эпохи, когда оператор поднимает предмет.
        if (event.player.isOp) return

        checkEpoch(event.item.itemStack.type)
    }

    @EventHandler
    fun onPlayerCraft(event: CraftItemEvent) {
        // Предотвратим улучшение эпохи, когда оператор крафтит предмет.
        if (event.whoClicked.isOp) return

        checkEpoch(event.recipe.result.type)
    }

    @EventHandler
    fun onFurnaceExtract(event: FurnaceExtractEvent) {
        // Предотвратим улучшение эпохи, когда оператор забирает предмет из печи.
        if (event.player.isOp) return

        checkEpoch(event.itemType)
    }

    @EventHandler
    fun onClickEvent(event: InventoryClickEvent) {
        // Предотвратим улучшение эпохи, когда оператор кликает по предмету.
        if (event.whoClicked.isOp) return

        val item = event.currentItem
        if (item != null) checkEpoch(item.type)
    }

    private fun checkEpoch(material: Material) {
        val epoch = WorldEpoch.itemToEpoch[material]
        if (epoch != null) plugin.allowEpoch(epoch)
    }
}