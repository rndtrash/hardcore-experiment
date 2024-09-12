package ru.teasanctuary.hardcore_experiment.config

import org.bukkit.configuration.serialization.ConfigurationSerializable

class HardcoreExperimentConfig(
    /**
     * Максимальная длительность эпохи угля
     *
     * @see ru.teasanctuary.hardcore_experiment.types.WorldEpoch.Coal
     */
    val coalEpochUpgradeTimer: Int,
    /**
     * Время, которое даётся игрокам на то, чтобы зайти в игру в первый раз. По истечению новые игроки будут
     * появляться мёртвыми.
     */
    val initialJoinTimer: Int,
    /**
     * Время, которое даётся игрокам на возрождение умершего игрока. По истечению игрок становится наблюдателем.
     */
    val respawnTimeout: Int,
    /**
     * Время, за которое будет достигнута следующая эпоха, если она была помечена как разрешённая.
     */
    val epochUpgradeTimer: Int
) : ConfigurationSerializable {
    companion object {
        const val DEFAULT_COAL_EPOCH_UPGRADE_TIMER = 180
        const val DEFAULT_INITIAL_JOIN_TIMER = 300
        const val DEFAULT_RESPAWN_TIMEOUT = 1200
        const val DEFAULT_EPOCH_UPGRADE_TIMER = 1200 // 1 игровой день = 20 реальных минут (1200 секунд)
    }

    constructor(config: Map<String, Object>) : this(
        config["coal-epoch-upgrade-timer"] as? Int ?: DEFAULT_COAL_EPOCH_UPGRADE_TIMER,
        config["initial-join-timer"] as? Int ?: DEFAULT_INITIAL_JOIN_TIMER,
        config["respawn-timeout"] as? Int ?: DEFAULT_RESPAWN_TIMEOUT,
        config["epoch-upgrade-timer"] as? Int ?: DEFAULT_EPOCH_UPGRADE_TIMER
    )

    constructor() : this(DEFAULT_COAL_EPOCH_UPGRADE_TIMER, DEFAULT_INITIAL_JOIN_TIMER, DEFAULT_RESPAWN_TIMEOUT, DEFAULT_EPOCH_UPGRADE_TIMER)

    override fun serialize(): MutableMap<String, Any> {
        return mutableMapOf(
            Pair("coal-epoch-upgrade-timer", coalEpochUpgradeTimer),
            Pair("initial-join-timer", initialJoinTimer),
            Pair("respawn-timeout", respawnTimeout),
            Pair("epoch-upgrade-timer", epochUpgradeTimer)
        )
    }
}
