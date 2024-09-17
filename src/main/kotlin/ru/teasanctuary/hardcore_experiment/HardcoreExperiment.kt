package ru.teasanctuary.hardcore_experiment

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.*
import org.bukkit.block.Chest
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
import org.bukkit.scheduler.BukkitTask
import ru.teasanctuary.hardcore_experiment.config.ConfigField
import ru.teasanctuary.hardcore_experiment.config.ConfigFieldArgumentType
import ru.teasanctuary.hardcore_experiment.config.HardcoreExperimentConfig
import ru.teasanctuary.hardcore_experiment.listener.*
import ru.teasanctuary.hardcore_experiment.types.*
import java.io.File
import java.util.*
import java.util.logging.Level
import kotlin.reflect.full.createType

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
     * Ключ для хранения момента времени, когда мир перешёл в текущую эпоху.
     *
     * Хранится на стороне мира.
     */
    private val nkEpochTimestamp: NamespacedKey = NamespacedKey(this, "epoch_timestamp")

    /**
     * Ключ для хранения разрешённых эпох.
     *
     * Хранится на стороне мира.
     */
    private val nkEpochBitmap: NamespacedKey = NamespacedKey(this, "epoch_bitmap")

    /**
     * Ключ для хранения момента времени, когда мир должен перейти в следующую эпоху.
     *
     * Хранится на стороне мира.
     */
    private val nkNextEpochTimestamp: NamespacedKey = NamespacedKey(this, "next_epoch_timestamp")

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

    /**
     * Разрешение на размещение алтаря через команду консоли.
     */
    private val permissionBuildAltar = Permission("hardcore_experiment.build_altar", PermissionDefault.OP)

    /**
     * Разрешение на получение внутренних уведомлений.
     */
    private val permissionNotifications = Permission("hardcore_experiment.notifications", PermissionDefault.OP)

    /**
     * Разрешение на изменения конфигурации плагина.
     */
    private val permissionConfig = Permission("hardcore_experiment.config", PermissionDefault.OP)

    private var _epoch: WorldEpoch = WorldEpoch.Invalid

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

    /**
     * Эпохи, разрешённые для перехода.
     */
    private val epochBitmap: MutableList<Boolean> =
        WorldEpoch.entries.map { epoch -> epoch == WorldEpoch.Coal }.toMutableList()

    // TODO: Очень хреновое решение, надо будет потом доработать
    lateinit var defaultWorld: World

    private val playerStateChangeQueue = mutableMapOf<UUID, PlayerStateChangeRequest>()

    /**
     * Список мёртвых игроков, которых можно возродить.
     */
    private val deadPlayers = mutableMapOf<UUID, DeadPlayerStatus>()

    lateinit var hardcoreConfig: HardcoreExperimentConfig

    private var coalEpochTimer: BukkitTask? = null
    private var nextEpochTimer: BukkitTask? = null
    private var deadPlayerTimers = mutableMapOf<UUID, BukkitTask>()

    private lateinit var altar: AltarSchematic

    /**
     * Отправляет уведомление активным администраторам и в консоль сервера.
     */
    fun notifyAdmins(component: Component) {
        Bukkit.broadcast(component, permissionNotifications.name)
        logger.log(Level.INFO, PlainTextComponentSerializer.plainText().serialize(component))
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
     * Удаляет игрока со списка на возрождение, а также останавливает таймер.
     */
    private fun removeFromDead(playerId: UUID) {
        deadPlayers.remove(playerId)
        val timer = deadPlayerTimers.remove(playerId)
        timer?.cancel()
    }

    /**
     * Оживляет игрока, если он "возродился" в наблюдателя и сейчас на сервере. В противном случае ставит в очередь.
     */
    fun makePlayerAlive(player: Player, location: Location?) {
        val newLocation = location ?: player.world.spawnLocation
        if (player.isValid) {
            makeOnlinePlayerAlive(player, newLocation)
        } else {
            makeOfflinePlayerAlive(player.uniqueId, newLocation)
        }
        removeFromDead(player.uniqueId)
    }

    /**
     * Ставит в очередь игрока, не находящегося на сервере. В противном случае сразу же возрождает.
     */
    fun makePlayerAlive(playerId: UUID, location: Location?) {
        val newLocation = location ?: defaultWorld.spawnLocation
        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isValid) {
            makeOnlinePlayerAlive(player, newLocation)
        } else {
            makeOfflinePlayerAlive(playerId, newLocation)
        }
        removeFromDead(playerId)
    }

    /**
     * Воскрешает игрока, находящегося на сервере.
     */
    private fun makeOnlinePlayerAlive(player: Player, location: Location) {
        assert(player.isValid)

        setPlayerState(player, PlayerState.Alive)
        player.gameMode = GameMode.SURVIVAL
        player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

    /**
     * Ставит игрока, вышедшего с сервера, в очередь на воскрешение.
     */
    private fun makeOfflinePlayerAlive(playerId: UUID, location: Location) {
        val player = Bukkit.getOfflinePlayer(playerId)
        assert(player.hasPlayedBefore())

        playerStateChangeQueue[playerId] = PlayerStateChangeRequest(PlayerState.Alive, location)
    }

    /**
     * Убивает игрока, добавляя его в список возрождаемых игроков.
     */
    fun killPlayer(player: Player, location: Location) {
        assert(player.isValid)

        val playerId = player.uniqueId
        val timeout = hardcoreConfig.respawnTimeout * REAL_SECONDS_TO_GAME_TIME
        deadPlayers[playerId] = DeadPlayerStatus(
            defaultWorld.gameTime - epochSince, epoch, defaultWorld.gameTime + timeout
        )
        startDeathTimer(playerId, timeout)

        makePlayerSpectateLimited(player, location)
    }

    /**
     * Делает игрока ограниченным наблюдателем (не дальше своего сундука с лутом).
     */
    fun makePlayerSpectateLimited(player: Player, location: Location) {
        if (player.isValid) {
            makeOnlinePlayerSpectateLimited(player, location)
        } else {
            makeOfflinePlayerSpectateLimited(player.uniqueId, location)
        }
    }

    /**
     * Делает игрока ограниченным наблюдателем (не дальше своего сундука с лутом).
     */
    fun makePlayerSpectateLimited(playerId: UUID, location: Location) {
        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isValid) {
            makeOnlinePlayerSpectateLimited(player, location)
        } else {
            makeOfflinePlayerSpectateLimited(playerId, location)
        }
    }

    private fun makeOnlinePlayerSpectateLimited(player: Player, location: Location) {
        assert(player.isValid)

        setPlayerState(player, PlayerState.LimitedSpectator)
        player.gameMode = GameMode.SPECTATOR
        player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

    /**
     * Ставит игрока в очередь на ограниченное наблюдение.
     */
    private fun makeOfflinePlayerSpectateLimited(playerId: UUID, location: Location) {
        val player = Bukkit.getOfflinePlayer(playerId)
        assert(player.hasPlayedBefore())

        playerStateChangeQueue[playerId] = PlayerStateChangeRequest(PlayerState.LimitedSpectator, location)
    }

    /**
     * Делает игрока наблюдателем.
     */
    fun makePlayerSpectate(player: Player, location: Location? = null) {
        if (player.isValid) {
            makeOnlinePlayerSpectate(player, location ?: player.world.spawnLocation)
        } else {
            makeOfflinePlayerSpectate(player.uniqueId, location ?: defaultWorld.spawnLocation)
        }

        removeFromDead(player.uniqueId)
    }

    /**
     * Делает игрока наблюдателем.
     */
    fun makePlayerSpectate(playerId: UUID, location: Location? = null) {
        val player = Bukkit.getPlayer(playerId)
        if (player != null && player.isValid) {
            makeOnlinePlayerSpectate(player, location ?: player.world.spawnLocation)
        } else {
            makeOfflinePlayerSpectate(playerId, location ?: defaultWorld.spawnLocation)
        }

        removeFromDead(playerId)
    }

    /**
     * Ставит игрока в очередь на перманентное наблюдение.
     */
    private fun makeOnlinePlayerSpectate(player: Player, location: Location) {
        assert(player.isValid)

        setPlayerState(player, PlayerState.Spectator)
        player.gameMode = GameMode.SPECTATOR
        player.teleport(player.world.spawnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

    /**
     * Ставит игрока в очередь на перманентное наблюдение.
     */
    private fun makeOfflinePlayerSpectate(playerId: UUID, location: Location) {
        val player = Bukkit.getOfflinePlayer(playerId)
        assert(player.hasPlayedBefore())

        playerStateChangeQueue[playerId] = PlayerStateChangeRequest(PlayerState.Spectator, location)
    }

    /**
     * Обработать запрос на изменение состояния для живого игрока, если таковой имеется.
     */
    fun processPlayerStateRequest(player: Player) {
        val playerUid = player.uniqueId
        val request = playerStateChangeQueue.remove(playerUid)
        if (request != null) {
            when (request.state) {
                PlayerState.Alive -> makePlayerAlive(player, request.location)
                PlayerState.LimitedSpectator -> makePlayerSpectateLimited(player, request.location)
                PlayerState.Spectator -> makePlayerSpectate(player)
                else -> TODO("Type not supported: ${request.state}")
            }
        }
    }

    /**
     * Запускает таймер для конкретного игрока.
     */
    private fun startDeathTimer(playerId: UUID, timeout: Long) {
        assert(!deadPlayerTimers.contains(playerId))

        deadPlayerTimers[playerId] = server.scheduler.runTaskLater(this, Runnable {
            val player = Bukkit.getPlayer(playerId)
            if (player != null && player.isValid) makeOnlinePlayerSpectate(player, player.location)
            else makePlayerSpectate(playerId)

            Bukkit.broadcast(
                MiniMessage.miniMessage().deserialize(
                    "Игрока: <#55ff55>${Bukkit.getOfflinePlayer(playerId).name}</#55ff55> больше <#ff5555>НЕЛЬЗЯ ВОЗРОДИТЬ</#ff5555>.\n\n" + "Используйте алтари для возрождения тех, у кого ещё не истёк таймер. Список можно получить с помощью команды <#55ff55><click:run_command:'/he-resurrect'>/he-resurrect</click></#55ff55>"
                )
            )
        }, timeout)
    }

    /**
     * Запускает таймер на переход из начальной эпохи в следующую. Работает только для эпохи угля.
     */
    private fun handleCoalEpochTimer() {
        if (epoch != WorldEpoch.Coal || coalEpochTimer != null) return

        val nextEpoch = WorldEpoch.entries[WorldEpoch.Coal.ordinal + 1]
        val epochDuration = defaultWorld.gameTime - epochSince
        // Таймер уже истёк, настало время следующей эпохи
        if (epochDuration >= hardcoreConfig.coalEpochUpgradeTimer * REAL_SECONDS_TO_GAME_TIME) {
            epoch = nextEpoch
            return
        }

        coalEpochTimer = server.scheduler.runTaskLater(this, Runnable {
            if (epoch == WorldEpoch.Coal) epoch = nextEpoch
            coalEpochTimer = null
        }, hardcoreConfig.coalEpochUpgradeTimer * REAL_SECONDS_TO_GAME_TIME - epochDuration)
    }

    /**
     * Запускает таймер на переход в следующую разрешённую эпоху.
     *
     * Работает для всех эпох, кроме самой первой (см. handleCoalEpochTimer) и самой последней (по очевидным причинам).
     */
    private fun handleNextEpochTimer() {
        if (epoch == WorldEpoch.Coal || epoch.ordinal == WorldEpoch.entries.size - 1 || nextEpochTimer != null) return

        val nextEpoch = WorldEpoch.entries[epoch.ordinal + 1]
        // Переход на следующую эпоху пока что не дозволен
        if (!epochBitmap[nextEpoch.ordinal]) return

        val nextEpochDeadline =
            defaultWorld.persistentDataContainer.getOrDefault(nkNextEpochTimestamp, PersistentDataType.LONG, -1)
        // Если таймер был активен до выключения сервера, то восстанавливаемся
        var delay = hardcoreConfig.epochUpgradeTimer * REAL_SECONDS_TO_GAME_TIME
        if (nextEpochDeadline != -1L) {
            delay = nextEpochDeadline - defaultWorld.gameTime
        } else {
            // Запоминаем на случай выключения сервера
            defaultWorld.persistentDataContainer.set(
                nkNextEpochTimestamp, PersistentDataType.LONG, defaultWorld.gameTime + delay
            )
        }

        nextEpochTimer = server.scheduler.runTaskLater(this, Runnable {
            // Сначала сбрасываем таймер
            nextEpochTimer = null
            defaultWorld.persistentDataContainer.set(
                nkNextEpochTimestamp, PersistentDataType.LONG, -1
            )

            // Сеттер эпохи сам вызовет handleNextEpochTimer, если необходимо
            epoch = nextEpoch
        }, delay)
    }

    /**
     * Запускает таймеры для обновления эпохи угля и удаления игроков из списка на возрождение.
     *
     * Выполняется только при запуске сервера.
     */
    private fun initializeDeathTimers() {
        val deadIterator = deadPlayers.iterator()
        while (deadIterator.hasNext()) {
            val (playerId, request) = deadIterator.next()
            if (request.deadline <= defaultWorld.gameTime) {
                deadIterator.remove()
                continue
            }

            startDeathTimer(playerId, request.deadline - defaultWorld.gameTime)
        }
    }

    /**
     * Помечает эпоху как доступную для перехода. Если эпоха в аргументе находится после текущей эпохи,
     * то текущая эпоха улучшится до тех пор, пока её следующий сосед не будет запрещён, либо пока эпохи не закончатся.
     *
     * Возвращает true, если эпоха до этого была запрещена к переходу, и false в противном случае.
     */
    fun allowEpoch(epoch: WorldEpoch): Boolean {
        val ordinal = epoch.ordinal
        val currentOrdinal = this.epoch.ordinal
        // Пропускаем уже пройденные эпохи
        if (ordinal <= currentOrdinal) return false
        // Игнорируем уже разрешённые эпохи
        if (epochBitmap[ordinal]) return false

        epochBitmap[ordinal] = true
        handleNextEpochTimer()
        return true
    }

    /**
     * Изменяет эпоху развития игрока, а также запоминает момент времени, в который эпоха поменялась.
     */
    private fun setWorldEpoch(world: World, epoch: WorldEpoch) {
        if (epoch == WorldEpoch.Invalid) error("Нельзя выбирать эпоху Invalid")

        if (_epoch != epoch) {
            _epoch = epoch
            epochSince = world.gameTime
            var i = 0
            while (i <= epoch.ordinal) {
                epochBitmap[i] = true
                i++
            }

            Bukkit.broadcast(
                MiniMessage.miniMessage().deserialize(
                    "Достигнута новая эпоха: <#5555ff>${epoch.name}</#5555ff>\n\nВозрождение " + (if (epoch.canRespawn()) "<#55ff55>РАЗРЕШЕНО</#55ff55>. Не забудьте обновить ваши <b>алтари</b>!" else "<#ff5555>ЗАПРЕЩЕНО</#ff5555>. Удачи!")
                )
            )

            handleCoalEpochTimer()
            handleNextEpochTimer()
        }
    }

    fun sendCurrentEpoch(sender: CommandSender): Int {
        sender.sendMessage("Текущая эпоха ${epoch.name} длится ${(defaultWorld.gameTime - epochSince) / 20} секунд")

        return Command.SINGLE_SUCCESS
    }

    /**
     * Загружает данные из Permanent Data Container мира или инициализирует их, если необходимо.
     */
    private fun loadWorldStorage() {
        val dataEpoch =
            defaultWorld.persistentDataContainer.getOrDefault(nkEpoch, WorldEpochDataType(), WorldEpoch.Invalid)
        // Отсутствие эпохи принимаем как признак отсутствия остальных полей.
        if (dataEpoch == WorldEpoch.Invalid) {
            epoch = WorldEpoch.Coal

            saveWorldStorage()
            return
        }

        // Загружаем worldEpochBitmap до обработки таймеров
        val worldEpochBitmap = defaultWorld.persistentDataContainer.getOrDefault(nkEpochBitmap,
            PersistentDataType.LIST.listTypeFrom(
                PersistentDataType.BOOLEAN
            ),
            WorldEpoch.entries.map { epoch -> epoch == WorldEpoch.Coal || epoch == WorldEpoch.Invalid } // Разрешаем первые две эпохи по-умолчанию
                .toList())
        worldEpochBitmap.forEachIndexed { index, b -> epochBitmap[index] = b }

        _epoch = dataEpoch
        epochSince = defaultWorld.persistentDataContainer.getOrDefault(nkEpochTimestamp, PersistentDataType.LONG, 0)
        handleCoalEpochTimer()
        handleNextEpochTimer()

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
            nkEpochBitmap, PersistentDataType.LIST.listTypeFrom(PersistentDataType.BOOLEAN), epochBitmap
        )

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

    /**
     * Получает файл из папки настроек плагина. Копирует шаблон из ресурсов Jar-архива, если файл не найден.
     */
    private fun getCustomFile(path: String): File {
        val file = File(dataFolder, path)
        if (!file.exists()) {
            saveResource(path, false)
            return File(dataFolder, path)
        }
        return file
    }

    override fun onEnable() {
        saveDefaultConfig()
        ConfigurationSerialization.registerClass(HardcoreExperimentConfig::class.java)
        hardcoreConfig = getConfig().getSerializable(
            "hardcore-experiment", HardcoreExperimentConfig::class.java, HardcoreExperimentConfig(mapOf())
        ) ?: HardcoreExperimentConfig()

        val altarFile = getCustomFile("altar.nbt")
        altar = AltarSchematic.fromFile(altarFile)

        defaultWorld = Bukkit.getServer().worlds[0]
        loadWorldStorage()
        initializeDeathTimers()

        Bukkit.getPluginManager().addPermission(permissionManualUpgrade)
        Bukkit.getPluginManager().addPermission(permissionResurrect)
        Bukkit.getPluginManager().addPermission(permissionBuildAltar)
        Bukkit.getPluginManager().addPermission(permissionNotifications)
        Bukkit.getPluginManager().addPermission(permissionConfig)

        Bukkit.getPluginManager().registerEvents(JoinEventListener(this), this)
        Bukkit.getPluginManager().registerEvents(MortisEventListener(this), this)
        Bukkit.getPluginManager().registerEvents(WorldEventListener(this), this)
        Bukkit.getPluginManager().registerEvents(EpochEventListener(this), this)
        Bukkit.getPluginManager().registerEvents(TotemEventListener(this), this)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()

            commands.register(
                Commands.literal("he-config").requires { source -> source.sender.hasPermission(permissionConfig) }.then(
                    Commands.argument("property", ConfigFieldArgumentType())
                        // TODO: другие типы кроме Integer
                        .then(Commands.argument("integer", IntegerArgumentType.integer()).executes { ctx ->
                            val property = ctx.getArgument("property", ConfigField::class.java)
                            val value = ctx.getArgument("integer", Int::class.java)
                            val field =
                                HardcoreExperimentConfig.CONFIG_FIELDS.first { it.annotations.contains(property) }
                            if (field.getter.returnType != Int::class.createType()) {
                                ctx.source.sender.sendMessage("Данное поле имеет тип, отличный от Int. (${field.getter.returnType})")

                                return@executes Command.SINGLE_SUCCESS
                            }

                            try {
                                field.setter.call(hardcoreConfig, value)
                                ctx.source.sender.sendMessage("Значение изменено на ${field.getter.call(hardcoreConfig)}.")
                            } catch (exception: Exception) {
                                ctx.source.sender.sendMessage("Возникло исключение: $exception")
                            }

                            Command.SINGLE_SUCCESS
                        })
                ).build(), "Изменить эпоху развития игрока вручную."
            )

            commands.register(Commands.literal("he-epoch-set")
                .requires { source -> source.sender.hasPermission(permissionManualUpgrade) }
                .then(Commands.argument("epoch", WorldEpochArgumentType()).executes { ctx ->
                    val epoch = ctx.getArgument("epoch", WorldEpoch::class.java)

                    nextEpochTimer?.cancel()
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
                }.then(
                    Commands.literal("name").then(Commands.argument("players", ArgumentTypes.player()).executes { ctx ->
                        val playerSelector = ctx.getArgument("players", PlayerSelectorArgumentResolver::class.java)
                        val players = playerSelector.resolve(ctx.source)
                        if (players.isNotEmpty()) {
                            makePlayerAlive(players[0], null)
                        }

                        Command.SINGLE_SUCCESS
                    })
                ).then(
                    Commands.literal("uuid")
                        .then(Commands.argument("player_uuid", ArgumentTypes.uuid()).executes { ctx ->
                            val uuid = ctx.getArgument("player_uuid", UUID::class.java)
                            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
                            if (!offlinePlayer.hasPlayedBefore()) {
                                ctx.source.sender.sendMessage("Данный игрок никогда не играл на этом сервере.")
                                return@executes Command.SINGLE_SUCCESS
                            }

                            makePlayerAlive(uuid, null)

                            Command.SINGLE_SUCCESS
                        })
                ).build(), "Вручную возродить игрока."
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
                val playerSelector = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                val players = playerSelector.resolve(ctx.source)
                if (players.isEmpty()) {
                    return@executes Command.SINGLE_SUCCESS
                }

                val player = players[0]
                val state = getPlayerState(player)
                if (state != PlayerState.LimitedSpectator) {
                    if (state == PlayerState.Alive) {
                        ctx.source.sender.sendMessage("Игрок ${player.name} не найден в списке мёртвых. Пока.")
                    } else if (state == PlayerState.Spectator) {
                        ctx.source.sender.sendMessage("Игрока ${player.name} уже нельзя возродить. Увы.")
                    }

                    return@executes Command.SINGLE_SUCCESS
                }

                // TODO: проверить наличие алтаря под ногами и предметов для возрождения

                Command.SINGLE_SUCCESS
            }).build(),
                "Получить список мёртвых игроков, готовых к возрождению. При указании имени, попытаться возродить игрока."
            )

            commands.register(Commands.literal("he-build-altar")
                .requires { source -> source.sender is Player && source.sender.hasPermission(permissionBuildAltar) }
                .executes { ctx ->
                    val player = ctx.source.sender as Player
                    val result = altar.build(
                        player.world,
                        player.location.blockX,
                        player.location.blockY,
                        player.location.blockZ,
                        player.facing
                    )
                    if (result) ctx.source.sender.sendMessage("Готово. Рекомендую вызвать команду ещё раз, чтобы факелы поставились правильно.")
                    else ctx.source.sender.sendMessage("Ошибка: направь голову строго вдоль блоков, не по диагонали.")

                    Command.SINGLE_SUCCESS
                }.build(), "Для админов: возвести алтарь"
            )

            commands.register(Commands.literal("he-verify-altar")
                .requires { source -> source.sender is Player && source.sender.hasPermission(permissionBuildAltar) }
                .executes { ctx ->
                    val player = ctx.source.sender as Player
                    val block = player.getTargetBlockExact(10)
                    if (block == null || block.type != Material.CHEST) {
                        ctx.source.sender.sendMessage("Вы должны смотреть на блок сундука.")

                        return@executes Command.SINGLE_SUCCESS
                    }
                    val blockState = block.state as Chest
                    val epochs = altar.getEpochBlocks(blockState)
                    ctx.source.sender.sendMessage(if (epochs != null) "Правильно" else "Неправильно")
                    if (epochs != null) ctx.source.sender.sendMessage(epochs.joinToString(" "))

                    Command.SINGLE_SUCCESS
                }.build(), "Для админов: проверить алтарь на корректность"
            )
        }
    }

    override fun onDisable() {
        saveWorldStorage()

        getConfig().set("hardcore-experiment", hardcoreConfig)
        saveConfig()

        HandlerList.unregisterAll(this)
    }
}
