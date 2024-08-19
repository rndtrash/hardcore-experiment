package ru.teasanctuary.hardcore_experiment.listener

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import ru.teasanctuary.hardcore_experiment.HardcoreExperiment
import ru.teasanctuary.hardcore_experiment.types.PlayerState
import java.util.logging.Level

/**
 * Класс-слушатель, отвечающий за события, влияющие на состояние игрока:
 *
 * - Смерть
 * - Ручное изменение игрового режима
 *
 * Также данный класс обрабатывает события перемещения для ограниченных наблюдателей.
 */
class MortisEventListener(private val plugin: HardcoreExperiment) : Listener {
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        if (plugin.epoch.canRespawn()) {
            val respawnCost = plugin.epoch.getRespawnCost(plugin.defaultWorld.gameTime - plugin.epochSince)
            if (respawnCost == null) {
                // Воскрешение слишком дорогое, кидаем игрока в список мертвецов
                plugin.makePlayerSpectate(player)
                player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                        "<#ff5555><b>Вы умерли.</b></#ff5555>" + " Ваше возрождение стоило слишком дорого из-за долгого нахождения в одной эпохе, либо из-за частых смертей." + "\n\nУвы."
                    )
                )
            } else {
                plugin.killPlayer(player, player.location)
                player.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                        "<#ff5555><b>Вы умерли.</b></#ff5555> <#55ff55>Вас всё ещё можно возродить!</#55ff55>\n\n" + (if (respawnCost.amount == 0) "Для этого попросите кого-нибудь встать на алтарь и написать команду /he-resurrect ${player.name}"
                        else "Для этого попросите кого-нибудь положить на алтарь предмет <lang:${respawnCost.type.translationKey()}> в количестве ${respawnCost.amount} и написать команду /he-resurrect ${player.name}") + "\n\n<#ffff55>На возрождение вам дано ${plugin.hardcoreConfig.respawnTimeout} секунд, поторопитесь!</#ffff55>"
                    )
                )
            }
        } else {
            plugin.makePlayerSpectate(player)
        }
    }

    @EventHandler
    fun onPlayerGameModeChange(event: PlayerGameModeChangeEvent) {
        plugin.logger.log(Level.INFO, event.cause.name)
        if (event.cause == PlayerGameModeChangeEvent.Cause.COMMAND) {
            if (event.newGameMode == GameMode.SPECTATOR) plugin.makePlayerSpectate(event.player)
            else plugin.makePlayerAlive(event.player, null)
        } else if (event.cause == PlayerGameModeChangeEvent.Cause.HARDCORE_DEATH) {
            // Мы уже обрабатываем смерть игрока
            if (plugin.getPlayerState(event.player) == PlayerState.Alive) event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR && plugin.getPlayerState(player) == PlayerState.LimitedSpectator) {
            val newLocation = event.from.clone().setDirection(event.to.direction)
            event.player.teleport(newLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
            //event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (event.cause == PlayerTeleportEvent.TeleportCause.SPECTATE) cancelForLimitedSpectators(event)
    }

    @EventHandler
    fun onPlayerPostRespawn(event: PlayerPostRespawnEvent) {
        plugin.processPlayerStateRequest(event.player)
    }

    /**
     * Отменяет событие, если игрок находится в режиме частичного наблюдателя
     */
    private fun <T> cancelForLimitedSpectators(event: T) where T : PlayerEvent, T : Cancellable {
        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR && plugin.getPlayerState(player) == PlayerState.LimitedSpectator) {
            event.isCancelled = true
        }
    }
}