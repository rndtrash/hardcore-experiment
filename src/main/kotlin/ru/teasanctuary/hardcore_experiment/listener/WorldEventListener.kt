package ru.teasanctuary.hardcore_experiment.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldSaveEvent
import ru.teasanctuary.hardcore_experiment.HardcoreExperiment
import java.util.logging.Level

class WorldEventListener(private val plugin: HardcoreExperiment) : Listener {
    // TODO: походу, этот вызов вообще никогда не срабатывает при нормальных обстоятельствах.
    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        // TODO: playerStateChangeQueue and nkResurrectQueue
        plugin.logger.log(Level.INFO, "World data has loaded!")
    }

    @EventHandler
    fun onWorldSave(event: WorldSaveEvent) {
        plugin.logger.log(Level.INFO, "World data has been saved!")

        if (event.world == plugin.defaultWorld) plugin.saveWorldStorage()
    }
}