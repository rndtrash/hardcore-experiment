package ru.teasanctuary.hardcore_experiment

import com.mojang.brigadier.Command
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import ru.teasanctuary.hardcore_experiment.config.HardcoreExperimentConfig
import ru.teasanctuary.hardcore_experiment.listener.JoinEventListener
import ru.teasanctuary.hardcore_experiment.listener.MortisEventListener
import ru.teasanctuary.hardcore_experiment.listener.WorldEventListener
import ru.teasanctuary.hardcore_experiment.types.*
import ru.teasanctuary.hardcore_experiment.types.PlayerStateChangeRequest
import java.util.*
import java.util.logging.Level

class HardcoreExperiment : JavaPlugin() {
    companion object {
        /**
         * Преобразование реальных секунд к игровым.
         *
         * @sample World.getGameTime / REAL_SECONDS_TO_GAME_TIME
         */
        const val REAL_SECONDS_TO_GAME_TIME: Long = 20;
        private val mmListOfDeadPlayers = MiniMessage.miniMessage().deserialize("<b>Список мёртвых игроков:</b>")
    }

    /**
     * Ключ для хранения текущей эпохи мира.
     *
     * Хранится на стороне мира.
     */
    private val nkEpoch: NamespacedKey = NamespacedKey(this, "epoch")

    /**
     * Ключ для хранения момента времени, когда мир перешёл в новую эпоху.
     *
     * Хранится на стороне мира.
     */
    private val nkEpochTimestamp: NamespacedKey = NamespacedKey(this, "epoch_timestamp")

    /**
     * Ключ для хранения списка игроков, готовых к воскрешению, но ещё не присутствующих на сервере.
     *
     * Хранится на стороне мира.
     */
    private val nkResurrectQueue: NamespacedKey = NamespacedKey(this, "resurrect_queue")

    /**
     * Ключ для хранения списка игроков, которых можно воскресить за плату.
     *
     * Хранится на стороне мира.
     */
    private val nkDeadPlayers: NamespacedKey = NamespacedKey(this, "dead_players")

    /**
     * Ключ для хранения состояния игрока.
     *
     * Хранится на стороне игрока.
     */
    private val nkState: NamespacedKey = NamespacedKey(this, "state")

    private val permissionManualUpgrade = Permission("hardcore_experiment.manual_upgrade", PermissionDefault.OP)
    private val permissionResurrect = Permission("hardcore_experiment.resurrect", PermissionDefault.OP)

    // TODO: Очень хреновое решение, надо будет потом доработать
    var _defaultWorld: World? = null
    val defaultWorld: World
        get() = _defaultWorld!!

    val playerStateChangeQueue = mutableMapOf<UUID, PlayerStateChangeRequest>()

    /**
     * Список мёртвых игроков, которых можно возродить.
     */
    val deadPlayers = mutableMapOf<UUID, DeadPlayerStatus>()

    private var _hardcoreConfig: HardcoreExperimentConfig? = null;
    val hardcoreConfig
        get() = _hardcoreConfig!!

    /**
     * Инициализирует Permanent Data Container мира, если необходимо.
     */
    private fun initializeWorldStorage() {
        var epoch = defaultWorld.persistentDataContainer.get(nkEpoch, WorldEpochDataType())
        // Отсутствие эпохи принимаем как признак отсутствия остальных полей.
        if (epoch != null) return

        setWorldEpoch(defaultWorld, WorldEpoch.Coal)
    }

    /**
     * Возвращает состояние игрока. null, если игрок ещё не играл на данном сервере.
     *
     * @see PlayerState
     */
    fun getPlayerState(player: Player): PlayerState? {
        return player.persistentDataContainer.get(nkState, PlayerStateDataType())
    }

    /**
     * Задаёт состояние игрока.
     *
     * @see PlayerState
     */
    private fun setPlayerState(player: Player, state: PlayerState) {
        logger.log(Level.INFO, "Setting state for player \"${player.name}\": ${state.name}")
        player.persistentDataContainer.set(nkState, PlayerStateDataType(), state)
    }

    /**
     * Оживляет игрока, если он "возродился" в наблюдателя и сейчас на сервере. В противном случае ставит в очередь.
     *
     * @see makeOfflinePlayerAlive
     */
    fun makePlayerAlive(player: Player, location: Location?) {
        val newLocation = location ?: player.world.spawnLocation
        if (player.isValid) {
            setPlayerState(player, PlayerState.Alive)
            player.gameMode = GameMode.SURVIVAL
            player.teleport(newLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)

            deadPlayers.remove(player.uniqueId)
        } else {
            makeOfflinePlayerAlive(player.uniqueId, newLocation)
        }
    }

    /**
     * Ставит игрока в очередь на воскрешение.
     */
    fun makeOfflinePlayerAlive(playerId: UUID, location: Location) {
        val player = Bukkit.getOfflinePlayer(playerId)
        assert(player.hasPlayedBefore())

        playerStateChangeQueue[playerId] = PlayerStateChangeRequest(PlayerState.Alive, location)
    }

    /**
     * Убивает игрока, добавляя его в список возрождаемых игроков.
     */
    fun killPlayer(player: Player, location: Location) {
        val (epoch, epochTimestamp) = getWorldEpoch(defaultWorld)
        deadPlayers[player.uniqueId] = DeadPlayerStatus(
            defaultWorld.gameTime - epochTimestamp, epoch, defaultWorld.gameTime + hardcoreConfig.respawnTimeout * REAL_SECONDS_TO_GAME_TIME
        )

        makePlayerSpectateLimited(player, location)
    }

    /**
     * Делает игрока ограниченным наблюдателем (не дальше своего сундука с лутом).
     */
    fun makePlayerSpectateLimited(player: Player, location: Location) {
        if (player.isValid) {
            setPlayerState(player, PlayerState.LimitedSpectator)
            player.gameMode = GameMode.SPECTATOR
            player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
        } else {
            makeOfflinePlayerSpectateLimited(player.uniqueId, location)
        }
    }

    /**
     * Ставит игрока в очередь на ограниченное наблюдение.
     */
    fun makeOfflinePlayerSpectateLimited(playerId: UUID, location: Location) {
        val player = Bukkit.getOfflinePlayer(playerId)
        assert(player.hasPlayedBefore())

        playerStateChangeQueue[playerId] = PlayerStateChangeRequest(PlayerState.LimitedSpectator, location)
    }

    /**
     * Делает игрока наблюдателем.
     */
    fun makePlayerSpectate(player: Player) {
        if (player.isValid) {
            setPlayerState(player, PlayerState.Spectator)
            player.gameMode = GameMode.SPECTATOR
            player.teleport(player.world.spawnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
        } else {
            makeOfflinePlayerSpectate(player.uniqueId, player.world.spawnLocation)
        }
    }

    /**
     * Ставит игрока в очередь на перманентное наблюдение.
     */
    fun makeOfflinePlayerSpectate(playerId: UUID, location: Location) {
        val player = Bukkit.getOfflinePlayer(playerId)
        assert(player.hasPlayedBefore())

        playerStateChangeQueue[playerId] = PlayerStateChangeRequest(PlayerState.Spectator, location)
    }

    /**
     * Изменяет эпоху развития игрока, а также запоминает момент времени, в который эпоха поменялась.
     */
    fun setWorldEpoch(world: World, epoch: WorldEpoch) {
        world.persistentDataContainer.set(nkEpoch, WorldEpochDataType(), epoch)

        val worldTime = world.gameTime
        world.persistentDataContainer.set(nkEpochTimestamp, PersistentDataType.LONG, worldTime)

        // На случай даунгрейда эпохи, времена достижения последующих эпох обнуляются.
        // val epochOrdinal = epoch.ordinal
        // epochHistory[epochOrdinal] = worldTime
        // for (i in epochOrdinal + 1..<WorldEpoch.entries.size) {
        // epochHistory[i] = -1
        // }
    }

    fun getWorldEpochSince(world: World): Long {
        return world.persistentDataContainer.getOrDefault(
            nkEpochTimestamp, PersistentDataType.LONG, 0
        )
    }

    fun getWorldEpoch(world: World): Pair<WorldEpoch, Long> {
        var epoch = world.persistentDataContainer.get(nkEpoch, WorldEpochDataType())
        if (epoch == null) {
            epoch = WorldEpoch.Coal
            setWorldEpoch(world, epoch)
        }

        return Pair(epoch, getWorldEpochSince(world))
    }

    fun sendCurrentEpoch(sender: CommandSender): Int {
        val epochPair = getWorldEpoch(defaultWorld)
        sender.sendMessage("Текущая эпоха ${epochPair.first.name} длится ${(defaultWorld.gameTime - epochPair.second) / 20} секунд")

        return Command.SINGLE_SUCCESS
    }

    override fun onEnable() {
        saveDefaultConfig()
        ConfigurationSerialization.registerClass(HardcoreExperimentConfig::class.java)
        _hardcoreConfig = getConfig().getSerializable(
            "hardcore-experiment", HardcoreExperimentConfig::class.java, HardcoreExperimentConfig(mapOf())
        )

        _defaultWorld = Bukkit.getServer().worlds[0]
        initializeWorldStorage()

        Bukkit.getPluginManager().addPermission(permissionManualUpgrade)
        Bukkit.getPluginManager().addPermission(permissionResurrect)

        Bukkit.getPluginManager().registerEvents(JoinEventListener(this), this)
        Bukkit.getPluginManager().registerEvents(MortisEventListener(this), this)
        Bukkit.getPluginManager().registerEvents(WorldEventListener(this), this)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, { event ->
            val commands = event.registrar()
            commands.register(Commands.literal("he-upgrade")
                .requires { source -> source.sender.hasPermission(permissionManualUpgrade) }
                .then(Commands.argument("epoch", WorldEpochArgumentType()).executes { ctx ->
                    val epoch = ctx.getArgument("epoch", WorldEpoch::class.java)
                    setWorldEpoch(defaultWorld, epoch)

                    Command.SINGLE_SUCCESS
                }).build(), "Изменить эпоху развития игрока вручную."
            )

            commands.register(
                Commands.literal("he-epoch").executes { ctx ->
                    sendCurrentEpoch(ctx.source.sender)
                }.build(), "Получить эпоху развития всего сервера."
            )

            commands.register(Commands.literal("he-admin-resurrect")
                .requires { source -> source.sender.hasPermission(permissionResurrect) }.executes { ctx ->
                    if (ctx.source.sender is Player) makePlayerAlive(ctx.source.sender as Player, null)

                    Command.SINGLE_SUCCESS
                }.then(Commands.argument("players", ArgumentTypes.player()).executes { ctx ->
                    val playerSelector = ctx.getArgument("players", PlayerSelectorArgumentResolver::class.java)
                    val players = playerSelector.resolve(ctx.source)
                    if (players.isNotEmpty()) {
                        makePlayerAlive(players[0], null)
                    }

                    Command.SINGLE_SUCCESS
                }).build(), "Вручную возродить игрока."
            )

            commands.register(Commands.literal("he-resurrect").executes { ctx ->
                ctx.source.sender.sendMessage(mmListOfDeadPlayers)
                deadPlayers.toList().sortedBy { pair -> pair.second.getTimeLeft(this) }.forEach { pair ->
                    val player = Bukkit.getOfflinePlayer(pair.first)
                    val cost: ItemStack = pair.second.getCost() ?: return@forEach
                    // TODO: возможная уязвимость с инъекцией в сообщение
                    ctx.source.sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                            "<#ff5555>${player.name}</#ff5555>: " + "Осталось <b>${(pair.second.getTimeLeft(this)) / REAL_SECONDS_TO_GAME_TIME}</b> секунд, чтобы возродить за " + (if (cost.amount == 0) "<b>бесплатно</b>"
                            else "<lang:${cost.type.translationKey()}> в количестве ${cost.amount}")
                        )
                    )
                }

                Command.SINGLE_SUCCESS
            }.then(Commands.argument("player", ArgumentTypes.player()).requires { source ->
                // Позволим выполнить команду только живому игроку
                source.sender is Player && getPlayerState(source.sender as Player) == PlayerState.Alive
            }.executes { ctx ->
                // TODO: проверить наличие алтаря под ногами и предметов для возрождения

                Command.SINGLE_SUCCESS
            }).build(), "Список мёртвых игроков, готовых к возрождению."
            )
        })
    }

    override fun onDisable() {
        getConfig().set("hardcore-experiment", hardcoreConfig)
        saveConfig()

        HandlerList.unregisterAll(this);
    }
}
