package ru.teasanctuary.hardcore_experiment.listener

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.HumanEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import ru.teasanctuary.hardcore_experiment.HardcoreExperiment
import java.util.*

class TotemEventListener(private val plugin: HardcoreExperiment) : Listener {
    companion object {
        const val SLOT_OFF_HAND = 40
    }

    private val warnedPlayers = mutableSetOf<UUID>()

    @EventHandler
    fun onTotemResurrect(event: EntityResurrectEvent) {
        // Отменяем любое воскрешение игрока внутриигровыми способами (например, тотем)
        if (event.entityType == EntityType.PLAYER) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!player.isOp && event.offHandItem.type == Material.TOTEM_OF_UNDYING) warnPlayer(player)
    }

    @EventHandler
    fun onTakeOffHand(event: InventoryClickEvent) {
        val player = event.whoClicked
        if (player.isOp) return

        // Обрабатываем только события выкладывания на панель
        if (event.slotType != InventoryType.SlotType.QUICKBAR || !(event.action == InventoryAction.PLACE_ALL || event.action == InventoryAction.PLACE_SOME || event.action == InventoryAction.PLACE_ONE)) return

        if (event.slot == SLOT_OFF_HAND && event.cursor.type == Material.TOTEM_OF_UNDYING) warnPlayer(player)
    }

    private fun warnPlayer(player: HumanEntity) {
        val uuid = player.uniqueId
        if (warnedPlayers.contains(uuid)) return

        player.sendMessage(
            MiniMessage.miniMessage()
                .deserialize("<#ffff55>МИНЗДРАВ ПРЕДУПРЕЖДАЕТ:</#ffff55> тотемы на этом сервере <u><#ff5555>ОТКЛЮЧЕНЫ!</#ff5555></u> Держать тотем в руке <u>нет смысла</u>.")
        )
        warnedPlayers.add(uuid)
    }
}