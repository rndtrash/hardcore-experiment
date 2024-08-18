package ru.teasanctuary.hardcore_experiment.listener

import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import ru.teasanctuary.hardcore_experiment.HardcoreExperiment
import ru.teasanctuary.hardcore_experiment.HardcoreExperiment.Companion.REAL_SECONDS_TO_GAME_TIME

class JoinEventListener(private val plugin: HardcoreExperiment) : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.sendCurrentEpoch(player)

        val state = plugin.getPlayerState(player)
        if (state == null) {
            // Позволяем игрокам попасть в игру, если не стоит ограничение по времени на присоединение.
            // Используем время игрового мира, чтобы вычесть время сервера, проведённое в отключенном состоянии.
            val canJoinGame =
                plugin.hardcoreConfig.initialJoinTimer <= 0 || player.world.gameTime <= plugin.hardcoreConfig.initialJoinTimer * REAL_SECONDS_TO_GAME_TIME
            if (player.gameMode != GameMode.SPECTATOR && canJoinGame) {
                plugin.makePlayerAlive(player, null)
            } else {
                plugin.makePlayerSpectate(player)
            }
        }
    }
}