package ru.teasanctuary.hardcore_experiment.types

import ru.teasanctuary.hardcore_experiment.HardcoreExperiment

data class DeadPlayerStatus(val epochTimeStamp: Long, val epoch: WorldEpoch, val deadline: Long) {
    fun getCost() = epoch.getRespawnCost(epochTimeStamp)

    /**
     * Время в тиках до истечения возможности возродиться.
     *
     * Возвращает отрицательное значение, если возможность упущена. ¯\_(ツ)_/¯
     */
    fun getTimeLeft(plugin: HardcoreExperiment) = deadline - plugin.defaultWorld.gameTime
}
