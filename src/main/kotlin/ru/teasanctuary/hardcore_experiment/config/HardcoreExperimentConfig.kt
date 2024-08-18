package ru.teasanctuary.hardcore_experiment.config

import org.bukkit.configuration.serialization.ConfigurationSerializable

class HardcoreExperimentConfig(
    val coalEpochUpgradeTimer: Int,
    val initialJoinTimer: Int,
    val respawnTimeout: Int
) : ConfigurationSerializable {
    constructor(config: Map<String, Object>) : this(
        config["coal-epoch-upgrade-timer"] as? Int ?: 180,
        config["initial-join-timer"] as? Int ?: 300,
        config["respawn-timeout"] as? Int ?: 1200
    )

    override fun serialize(): MutableMap<String, Any> {
        return mutableMapOf(
            Pair("coal-epoch-upgrade-timer", coalEpochUpgradeTimer),
            Pair("initial-join-timer", initialJoinTimer),
            Pair("respawn-timeout", respawnTimeout),
        )
    }
}
