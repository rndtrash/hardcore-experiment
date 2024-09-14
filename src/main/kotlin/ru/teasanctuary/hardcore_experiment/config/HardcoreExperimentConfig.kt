package ru.teasanctuary.hardcore_experiment.config

import org.bukkit.configuration.serialization.ConfigurationSerializable
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

class HardcoreExperimentConfig(
    /**
     * Максимальная длительность эпохи угля
     *
     * @see ru.teasanctuary.hardcore_experiment.types.WorldEpoch.Coal
     */
    @ConfigField var coalEpochUpgradeTimer: Int,
    /**
     * Время, которое даётся игрокам на то, чтобы зайти в игру в первый раз. По истечению новые игроки будут
     * появляться мёртвыми.
     */
    @ConfigField var initialJoinTimer: Int,
    /**
     * Время, которое даётся игрокам на возрождение умершего игрока. По истечению игрок становится наблюдателем.
     */
    @ConfigField var respawnTimeout: Int,
    /**
     * Время, за которое будет достигнута следующая эпоха, если она была помечена как разрешённая.
     */
    @ConfigField var epochUpgradeTimer: Int,
    /**
     * Максимальное количество предметов, которое может быть заплачено за возрождение.
     *
     * Превышение данного лимита запрещает возрождение игрока.
     */
    @ConfigField var resurrectionPriceLimit: Int
) : ConfigurationSerializable {
    companion object {
        const val DEFAULT_COAL_EPOCH_UPGRADE_TIMER = 180
        const val DEFAULT_INITIAL_JOIN_TIMER = 300
        const val DEFAULT_RESPAWN_TIMEOUT = 1200
        const val DEFAULT_EPOCH_UPGRADE_TIMER = 1200 // 1 игровой день = 20 реальных минут (1200 секунд)
        const val DEFAULT_RESURRECTION_PRICE_LIMIT = 2 * 60 // два стака

        val CONFIG_FIELDS =
            HardcoreExperimentConfig::class.memberProperties.filter { it.annotations.any { annotation -> annotation is ConfigField } }
                .map { it as KMutableProperty1 }
    }

    constructor(config: Map<String, Object>) : this(
        config["coal-epoch-upgrade-timer"] as? Int ?: DEFAULT_COAL_EPOCH_UPGRADE_TIMER,
        config["initial-join-timer"] as? Int ?: DEFAULT_INITIAL_JOIN_TIMER,
        config["respawn-timeout"] as? Int ?: DEFAULT_RESPAWN_TIMEOUT,
        config["epoch-upgrade-timer"] as? Int ?: DEFAULT_EPOCH_UPGRADE_TIMER,
        config["resurrection-price-limit"] as? Int ?: DEFAULT_RESURRECTION_PRICE_LIMIT
    )

    constructor() : this(
        DEFAULT_COAL_EPOCH_UPGRADE_TIMER,
        DEFAULT_INITIAL_JOIN_TIMER,
        DEFAULT_RESPAWN_TIMEOUT,
        DEFAULT_EPOCH_UPGRADE_TIMER,
        DEFAULT_RESURRECTION_PRICE_LIMIT
    )

    override fun serialize(): MutableMap<String, Any> {
        return mutableMapOf(
            Pair("coal-epoch-upgrade-timer", coalEpochUpgradeTimer),
            Pair("initial-join-timer", initialJoinTimer),
            Pair("respawn-timeout", respawnTimeout),
            Pair("epoch-upgrade-timer", epochUpgradeTimer),
            Pair("resurrection-price-limit", resurrectionPriceLimit)
        )
    }
}
