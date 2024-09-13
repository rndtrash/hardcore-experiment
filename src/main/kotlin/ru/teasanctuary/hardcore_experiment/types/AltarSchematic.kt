package ru.teasanctuary.hardcore_experiment.types

import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.nbt.*
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Chest
import java.io.File

data class AltarSchematic(
    /**
     * Координаты блока сундука
     */
    val center: Vec3i,
    /**
     * Размер структуры
     */
    val size: Vec3i,
    /**
     * Трёхмерный массив блоков, из которых состоит алтарь. Y - вторая координата - вверх
     */
    val blocks: Array<Array<Array<Material>>>,
    /**
     * Неупорядоченный массив всех возможных позиций блоков эпох относительно края структуры.
     */
    val epochBlockLocations: Array<Vec3i>
) {
    companion object {
        private fun getNbtVector(rootTag: CompoundTag, name: String): Vec3i {
            val vecNbt = rootTag.getList(name, Tag.TAG_INT.toInt())
            assert(vecNbt.count() == 3)
            return Vec3i((vecNbt[0] as IntTag).asInt, (vecNbt[1] as IntTag).asInt, (vecNbt[2] as IntTag).asInt)
        }

        /**
         * Вращает вектор вокруг оси Y.
         */
        private fun rotate(v: Vec3i, size: Vec3i, dFrom: Direction, dTo: Direction): Vec3i {
            val dDifference = Direction.from2DDataValue((4 + dTo.get2DDataValue() - dFrom.get2DDataValue()) % 4)
            return when (dDifference) {
                Direction.NORTH -> Vec3i(size.x - v.x - 1, v.y, size.z - v.z - 1)
                Direction.WEST -> Vec3i(size.z - v.z - 1, v.y, v.x)
                Direction.EAST -> Vec3i(v.z, v.y, size.x - v.x - 1)
                Direction.SOUTH -> Vec3i(v.x, v.y, v.z)
                else -> error("Unreachable")
            }
        }

        /**
         * Преобразовывает направление типа BlockFace в направление типа Direction.
         *
         * Не работает с неортогональными направлениями.
         */
        private fun blockFaceToDirection(facing: BlockFace): Direction = when (facing) {
            BlockFace.NORTH -> Direction.NORTH
            BlockFace.SOUTH -> Direction.SOUTH
            BlockFace.WEST -> Direction.WEST
            BlockFace.EAST -> Direction.EAST
            else -> error("Unknown")
        }

        private fun rotate(v: Vec3i, size: Vec3i, fFrom: BlockFace, fTo: BlockFace): Vec3i =
            rotate(v, size, blockFaceToDirection(fFrom), blockFaceToDirection(fTo))

        /**
         * Получить AltarSchematic из файла.
         *
         * Требования к файлу:
         * 1. Формат NBT (Vanilla Structure)
         * 2. Ровно один сундук по центру строения
         *
         * @see AltarSchematic
         */
        fun fromFile(file: File): AltarSchematic {
            val s = file.inputStream()
            val tagSizeTracker = NbtAccounter.unlimitedHeap()
            val nbt = NbtIo.readCompressed(s, tagSizeTracker)

            // Заполняем "палитру" блоков. Для сундука особый случай: вытаскиваем сторону света.
            val paletteNbt = nbt.getList("palette", Tag.TAG_COMPOUND.toInt())
            val palette = Array(paletteNbt.size) { Material.AIR }
            // Направление сундука определяется его замком (маленькой железной деталью спереди).
            var chestDirection = Direction.NORTH
            for ((i, tag) in paletteNbt.withIndex()) {
                val compoundTag = tag as CompoundTag
                val name = compoundTag.getString("Name")
                val material = Material.matchMaterial(name)
                material as Material
                palette[i] = material

                if (material == Material.CHEST) {
                    val direction = Direction.byName(
                        compoundTag.getCompound("Properties").getString("facing")
                    ) as Direction
                    chestDirection = direction
                }
            }

            var size = getNbtVector(nbt, "size")
            val blocksNbt = nbt.getList("blocks", Tag.TAG_COMPOUND.toInt())
            val blocks = Array(size.x) { Array(size.y) { Array(size.z) { Material.AIR } } }

            var chestPosition: Vec3i? = null
            val epochLocations = mutableListOf<Vec3i>()
            for (tag in blocksNbt) {
                val compoundTag = tag as CompoundTag
                val state = compoundTag.getInt("state")
                val material = palette[state]

                val pos = getNbtVector(compoundTag, "pos")
                // Если предмет принадлежит эпохе, то записываем позицию на будущее и исключаем из постройки
                val epoch = WorldEpoch.itemToEpoch[material]
                if (epoch != null) {
                    epochLocations.addLast(pos)
                } else {
                    blocks[pos.x][pos.y][pos.z] = material
                    // Запоминаем позицию сундука
                    if (material == Material.CHEST) chestPosition = pos
                }
            }

            // В постройке должен быть хотя бы один сундук
            if (chestPosition == null) error("chestPosition is null")

            var transposedBlocks = blocks
            // Приводим структуру к единому формату, где сундук смотрит на юг
            if (chestDirection != Direction.SOUTH) {
                // Меняем координаты блоков эпох местами
                var i = 0
                while (i < epochLocations.size) {
                    val loc = epochLocations[i]
                    epochLocations[i] = rotate(loc, size, chestDirection, Direction.SOUTH)
                    i++
                }

                // Нужно ли менять размер матрицы
                val isPerpendicular = chestDirection != Direction.NORTH
                transposedBlocks =
                    if (isPerpendicular) Array(size.z) { Array(size.y) { Array(size.x) { Material.AIR } } }
                    else Array(size.x) { Array(size.y) { Array(size.z) { Material.AIR } } }

                when (chestDirection) {
                    Direction.NORTH -> {
                        var y = 0
                        while (y < size.y) {
                            var x = 0
                            while (x < size.x) {
                                var z = 0
                                while (z < size.z) {
                                    transposedBlocks[size.x - x - 1][y][size.z - z - 1] = blocks[x][y][z]
                                    z++
                                }
                                x++
                            }
                            y++
                        }
                    }

                    Direction.WEST -> {
                        var y = 0
                        while (y < size.y) {
                            var x = 0
                            while (x < size.x) {
                                var z = 0
                                while (z < size.z) {
                                    transposedBlocks[size.z - z - 1][y][x] = blocks[x][y][z]
                                    z++
                                }
                                x++
                            }
                            y++
                        }
                    }

                    Direction.EAST -> {
                        var y = 0
                        while (y < size.y) {
                            var x = 0
                            while (x < size.x) {
                                var z = 0
                                while (z < size.z) {
                                    transposedBlocks[z][y][size.x - x - 1] = blocks[x][y][z]
                                    z++
                                }
                                x++
                            }
                            y++
                        }
                    }

                    else -> error("Unreachable")
                }

                if (isPerpendicular) size = Vec3i(size.z, size.y, size.x)
            }

            return AltarSchematic(chestPosition, size, transposedBlocks, epochLocations.toTypedArray())
        }

        private val airs = setOf(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR
        )
        private val stones = setOf(
            Material.COBBLESTONE,
            Material.ANDESITE,
            Material.DIORITE,
            Material.GRANITE,
            Material.DEEPSLATE,
            Material.BLACKSTONE
        )
        private val stoneSlabs = setOf(
            Material.COBBLESTONE_SLAB,
            Material.ANDESITE_SLAB,
            Material.DIORITE_SLAB,
            Material.GRANITE_SLAB,
            Material.COBBLED_DEEPSLATE_SLAB,
            Material.BLACKSTONE_SLAB
        )
        private val stoneWalls = setOf(
            Material.COBBLESTONE_WALL,
            Material.ANDESITE_WALL,
            Material.DIORITE_WALL,
            Material.GRANITE_WALL,
            Material.COBBLED_DEEPSLATE_WALL,
            Material.BLACKSTONE_WALL
        )
        private val copperStates = setOf(
            Material.COPPER_BLOCK,
            Material.EXPOSED_COPPER,
            Material.WEATHERED_COPPER,
            Material.OXIDIZED_COPPER,
            Material.WAXED_COPPER_BLOCK,
            Material.WAXED_EXPOSED_COPPER,
            Material.WAXED_WEATHERED_COPPER,
            Material.WAXED_OXIDIZED_COPPER
        )

        /**
         * Проверяет равенство двух блоков. Игнорирует некоторые блоки, например воздух.
         */
        private fun isMaterialEqual(schematic: Material, world: Material): Boolean {
            // Наиболее вероятный случай: разрешаем точные совпадения
            // Это позволит нам реже вызывать дорогие методы Set.contains()
            if (schematic == world) return true

            // Разрешаем ставить факела где угодно
            if (schematic == Material.TORCH) return true

            // Разрешаем строить в пустых местах (да, есть несколько видов воздуха)
            if (airs.contains(schematic)) return true

            // Разрешаем заменять виды булыжника
            if (stones.contains(schematic)) return stones.contains(world)
            if (stoneSlabs.contains(schematic)) return stoneSlabs.contains(world)
            if (stoneWalls.contains(schematic)) return stoneWalls.contains(world)

            // Разрешаем оксидацию блока меди
            if (copperStates.contains(schematic)) return copperStates.contains(world)

            return false
        }
    }

    /**
     * Итерация сначала по -X, затем по -Z
     */
    private fun nextSouth(i: Vec3i): Vec3i {
        if (i.x > 0) return Vec3i(i.x - 1, i.y, i.z)

        if (i.z > 0) return Vec3i(size.x - 1, i.y, i.z - 1)

        return Vec3i(size.x - 1, i.y + 1, size.z - 1)
    }

    /**
     * Итерация сначала по +X, затем по +Z
     */
    private fun nextNorth(i: Vec3i): Vec3i {
        if (i.x < size.x - 1) return Vec3i(i.x + 1, i.y, i.z)

        if (i.z < size.z - 1) return Vec3i(0, i.y, i.z + 1)

        return Vec3i(0, i.y + 1, 0)
    }

    /**
     * Итерация сначала по -Z, затем по +X
     */
    private fun nextWest(i: Vec3i): Vec3i {
        // Конструкция повёрнута на бок, поэтому меняем size.x и size.z местами
        if (i.z > 0) return Vec3i(i.x, i.y, i.z - 1)

        if (i.x < size.z - 1) return Vec3i(i.x + 1, i.y, size.x - 1)

        return Vec3i(0, i.y + 1, size.x - 1)
    }

    /**
     * Итерация сначала по +Z, затем по -X
     */
    private fun nextEast(i: Vec3i): Vec3i {
        // Конструкция повёрнута на бок, поэтому меняем size.x и size.z местами
        if (i.z < size.x - 1) return Vec3i(i.x, i.y, i.z + 1)

        if (i.x > 0) return Vec3i(i.x - 1, i.y, 0)

        return Vec3i(size.z - 1, i.y + 1, 0)
    }

    fun getEpochBlocks(chest: org.bukkit.block.Chest): Array<Boolean>? {
        val chestData = chest.blockData as Chest
        val chestFacing = chestData.facing
        assert(chestFacing.isCartesian && chestFacing != BlockFace.UP && chestFacing != BlockFace.DOWN)

        /*
        Направление в Minecraft задаётся по часовой стрелке от юга.

           N
        W     E
           S

           2
        1     3
           0
         */

        val isPerpendicular = chestFacing == BlockFace.EAST || chestFacing == BlockFace.WEST
        // TODO: захардкодил смещение, ибо заебало
        val chestOrigin =
            if (isPerpendicular) Vec3i(chest.x + 1, chest.y, chest.z - 1) else Vec3i(chest.x - 1, chest.y, chest.z + 1)
        // Конструкция повёрнута на бок, поэтому меняем size.x и size.z местами
        val structureCenter =
            if (isPerpendicular) Vec3i(center.z, center.y, center.x) else Vec3i(center.x, center.y, center.z)
        val structureOrigin = chestOrigin.subtract(structureCenter)
        var relativePos = when (chestFacing) {
            BlockFace.NORTH -> Vec3i(0, 0, 0)
            BlockFace.SOUTH -> Vec3i(size.x - 1, 0, size.z - 1)
            BlockFace.EAST -> Vec3i(size.z - 1, 0, 0)
            BlockFace.WEST -> Vec3i(0, 0, size.x - 1)
            else -> error("Unreachable")
        }
        val iterator: (Vec3i) -> Vec3i = when (chestFacing) {
            BlockFace.SOUTH -> { i -> nextSouth(i) }
            BlockFace.NORTH -> { i -> nextNorth(i) }
            BlockFace.WEST -> { i -> nextWest(i) }
            BlockFace.EAST -> { i -> nextEast(i) }
            else -> error("Unreachable")
        }

        val world = chest.world
        var y = 0
        while (y < size.y) {
            var z = 0
            while (z < size.z) {
                var x = 0
                while (x < size.x) {
                    val material = blocks[x][y][z]

                    val worldMaterial = world.getBlockAt(
                        structureOrigin.x + relativePos.x,
                        structureOrigin.y + relativePos.y,
                        structureOrigin.z + relativePos.z
                    ).type
                    if (!isMaterialEqual(material, worldMaterial)) return null

                    x++
                    relativePos = iterator(relativePos)
                }
                z++
            }
            y++
        }

        val epochList = Array(WorldEpoch.entries.size - 1) { _ -> false }
        for (ebl in epochBlockLocations) {
            val pos = rotate(ebl, size, BlockFace.SOUTH, chestFacing)

            val worldMaterial = world.getBlockAt(
                structureOrigin.x + pos.x, structureOrigin.y + pos.y, structureOrigin.z + pos.z
            ).type
            val epoch = WorldEpoch.itemToEpoch[worldMaterial]
            if (epoch != null) epochList[epoch.ordinal - 1] = true
        }

        return epochList
    }

    fun build(world: World, bX: Int, bY: Int, bZ: Int, direction: BlockFace): Boolean {
        if (!direction.isCartesian) return false

        val isPerpendicular = direction == BlockFace.EAST || direction == BlockFace.WEST
        // TODO: захардкодил смещение, ибо заебало
        val chestOrigin = if (isPerpendicular) Vec3i(bX + 1, bY, bZ - 1) else Vec3i(bX - 1, bY, bZ + 1)
        // Конструкция повёрнута на бок, поэтому меняем size.x и size.z местами
        val structureCenter =
            if (isPerpendicular) Vec3i(center.z, center.y, center.x) else Vec3i(center.x, center.y, center.z)
        val structureOrigin = chestOrigin.subtract(structureCenter)
        var relativePos = when (direction) {
            BlockFace.NORTH -> Vec3i(0, 0, 0)
            BlockFace.SOUTH -> Vec3i(size.x - 1, 0, size.z - 1)
            BlockFace.EAST -> Vec3i(size.z - 1, 0, 0)
            BlockFace.WEST -> Vec3i(0, 0, size.x - 1)
            else -> error("Unreachable")
        }
        val iterator: (Vec3i) -> Vec3i = when (direction) {
            BlockFace.SOUTH -> { i -> nextSouth(i) }
            BlockFace.NORTH -> { i -> nextNorth(i) }
            BlockFace.WEST -> { i -> nextWest(i) }
            BlockFace.EAST -> { i -> nextEast(i) }
            else -> error("Unreachable")
        }

        var y = 0
        while (y < size.y) {
            var z = 0
            while (z < size.z) {
                var x = 0
                while (x < size.x) {
                    val material = blocks[x][y][z]

                    world.getBlockAt(
                        structureOrigin.x + relativePos.x,
                        structureOrigin.y + relativePos.y,
                        structureOrigin.z + relativePos.z
                    ).type = material

                    x++
                    relativePos = iterator(relativePos)
                }
                z++
            }
            y++
        }

        val chestBlock = world.getBlockAt(bX, bY, bZ)
        if (chestBlock.type != Material.CHEST) return false
        val chestData = chestBlock.blockData as Chest
        chestData.facing = direction
        chestBlock.blockData = chestData

        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AltarSchematic

        if (center != other.center) return false
        if (size != other.size) return false
        if (!blocks.contentDeepEquals(other.blocks)) return false
        if (!epochBlockLocations.contentEquals(other.epochBlockLocations)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = center.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + blocks.contentDeepHashCode()
        result = 31 * result + epochBlockLocations.contentHashCode()
        return result
    }
}