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
         * Пример: World.getGameTime / REAL_SECONDS_TO_GAME_TIME
         */
        const val REAL_SECONDS_TO_GAME_TIME: Long = 20
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
     * Ключ для хранения списка игроков, готовых к воскрешению или умерщвлению, но ещё не присутствующих на сервере.
     *
     * Хранится на стороне мира.
     */
    private val nkStateChangeQueue: NamespacedKey = NamespacedKey(this, "state_change_queue")

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

    /**
     * Разрешение на ручное изменение эпохи.
     */
    private val permissionManualUpgrade = Permission("hardcore_experiment.manual_upgrade", PermissionDefault.OP)

    /**
     * Разрешение на бесплатное возрождение любого игрока вне зависимости от статуса окончательной смерти.
     */
    private val permissionResurrect = Permission("hardcore_experiment.resurrect", PermissionDefault.OP)

    private var _epoch: WorldEpoch = WorldEpoch.Coal

    /**
     * Текущая эпоха мира.
     */
    var epoch: WorldEpoch
        get() = _epoch
        set(e) = setWorldEpoch(defaultWorld, e)

    /**
     * Момент абсолютного игрового времени в тиках, когда наступила текущая эпоха.
     */
    var epochSince: Long = 0
        private set

    // TODO: Очень хреновое решение, надо будет потом доработать
    private var _defaultWorld: World? = null
    val defaultWorld: World
        get() = _defaultWorld!!

    val playerStateChangeQueue = mutableMapOf<UUID, PlayerStateChangeRequest>()

    /**
     * Список мёртвых игроков, которых можно возродить.
     */
    val deadPlayers = mutableMapOf<UUID, DeadPlayerStatus>()

    private var _hardcoreConfig: HardcoreExperimentConfig? = null
    val hardcoreConfig
        get() = _hardcoreConfig!!

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
        deadPlayers[player.uniqueId] = DeadPlayerStatus(
            defaultWorld.gameTime - epochSince,
            epoch,
            defaultWorld.gameTime + hardcoreConfig.respawnTimeout * REAL_SECONDS_TO_GAME_TIME
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
     * Обработать запрос на изменение состояния для живого игрока, если таковой имеется.
     */
    fun processPlayerStateRequest(player: Player) {
        val playerUid = player.uniqueId
        val request = playerStateChangeQueue[playerUid]
        if (request != null) {
            when (request.state) {
                PlayerState.Alive -> makePlayerAlive(player, request.location)
                PlayerState.LimitedSpectator -> makePlayerSpectateLimited(player, request.location)
                PlayerState.Spectator -> makePlayerSpectate(player)
                else -> TODO("Type not supported: ${request.state}")
            }
            playerStateChangeQueue.remove(playerUid)
        }
    }

    /**
     * Изменяет эпоху развития игрока, а также запоминает момент времени, в который эпоха поменялась.
     */
    private fun setWorldEpoch(world: World, epoch: WorldEpoch) {
        _epoch = epoch
        epochSince = world.gameTime
    }

    fun sendCurrentEpoch(sender: CommandSender): Int {
        sender.sendMessage("Текущая эпоха ${epoch.name} длится ${(defaultWorld.gameTime - epochSince) / 20} секунд")

        return Command.SINGLE_SUCCESS
    }

    /**
     * Загружает данные из Permanent Data Container мира или инициализирует их, если необходимо.
     */
    private fun loadWorldStorage() {
        val dataEpoch = defaultWorld.persistentDataContainer.get(nkEpoch, WorldEpochDataType())
        // Отсутствие эпохи принимаем как признак отсутствия остальных полей.
        if (dataEpoch == null) {
            epoch = WorldEpoch.Coal

            saveWorldStorage()
            return
        }

        _epoch = dataEpoch
        epochSince = defaultWorld.persistentDataContainer.getOrDefault(nkEpochTimestamp, PersistentDataType.LONG, 0)

        val deadPlayersList: List<DeadPlayerPair> = defaultWorld.persistentDataContainer.getOrDefault(
            nkDeadPlayers, PersistentDataType.LIST.listTypeFrom(DeadPlayersListDataType()), listOf()
        )
        deadPlayersList.forEach { pair ->
            deadPlayers[pair.player] = pair.status
        }

        val stateChangeQueue: List<StateChangePair> = defaultWorld.persistentDataContainer.getOrDefault(
            nkStateChangeQueue, PersistentDataType.LIST.listTypeFrom(StateChangeQueueDataType()), listOf()
        )
        stateChangeQueue.forEach { pair ->
            playerStateChangeQueue[pair.player] = pair.state
        }
    }

    /**
     * Сохраняет данные в Permanent Data Container мира.
     */
    fun saveWorldStorage() {
        defaultWorld.persistentDataContainer.set(nkEpoch, WorldEpochDataType(), epoch)
        defaultWorld.persistentDataContainer.set(nkEpochTimestamp, PersistentDataType.LONG, epochSince)

        defaultWorld.persistentDataContainer.set(
            nkDeadPlayers,
            PersistentDataType.LIST.listTypeFrom(DeadPlayersListDataType()),
            deadPlayers.map { pair -> DeadPlayerPair(pair.key, pair.value) }.toList()
        )

        defaultWorld.persistentDataContainer.set(
            nkStateChangeQueue,
            PersistentDataType.LIST.listTypeFrom(StateChangeQueueDataType()),
            playerStateChangeQueue.map { pair -> StateChangePair(pair.key, pair.value) }.toList()
        )
    }

    override fun onEnable() {
        saveDefaultConfig()
        ConfigurationSerialization.registerClass(HardcoreExperimentConfig::class.java)
        _hardcoreConfig = getConfig().getSerializable(
            "hardcore-experiment", HardcoreExperimentConfig::class.java, HardcoreExperimentConfig(mapOf())
        )

        _defaultWorld = Bukkit.getServer().worlds[0]
        loadWorldStorage()

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
                    this.epoch = epoch

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
        saveWorldStorage()

        getConfig().set("hardcore-experiment", hardcoreConfig)
        saveConfig()

        HandlerList.unregisterAll(this)
    }
}
