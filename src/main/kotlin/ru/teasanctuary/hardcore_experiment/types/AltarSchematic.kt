package ru.teasanctuary.hardcore_experiment.types

import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.type.Chest
import java.io.File
import java.util.logging.Level

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
     * Все возможные позиции блоков эпох относительно блока сундука.
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
            val epochLocations = Array(WorldEpoch.entries.count()) { Vec3i(0, 0, 0) }
            for (tag in blocksNbt) {
                val compoundTag = tag as CompoundTag
                val state = compoundTag.getInt("state")
                val material = palette[state]

                val pos = getNbtVector(compoundTag, "pos")
                // Если предмет принадлежит эпохе, то записываем позицию на будущее и исключаем из постройки
                val epoch = WorldEpoch.itemToEpoch[material]
                if (epoch != null) {
                    epochLocations[epoch.ordinal] = pos
                } else {
                    blocks[pos.x][pos.y][pos.z] = material
                    // Запоминаем позицию сундука
                    if (material == Material.CHEST) chestPosition = pos
                }
            }

            // В постройке должен быть хотя бы один сундук
            if (chestPosition == null) TODO("chestPosition is null")

            var transposedBlocks = blocks
            // Приводим структуру к единому формату, где сундук смотрит на юг
            if (chestDirection != Direction.SOUTH) {
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

                    else -> TODO()
                }

                if (isPerpendicular) size = Vec3i(size.z, size.y, size.x)
            }

            // Переводим координаты в относительные
            epochLocations.map { loc -> loc.subtract(chestPosition) }

            return AltarSchematic(chestPosition, size, transposedBlocks, epochLocations)
        }

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

            // Разрешаем строить в пустых местах
            if (schematic == Material.AIR) return true

            // Разрешаем ставить факела где угодно
            if (schematic == Material.TORCH) return true

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
     * Итерация сначала по +X, затем по +Z
     */
    private fun nextSouth(i: Vec3i): Vec3i {
        if (i.x < size.x - 1) return Vec3i(i.x + 1, i.y, i.z)

        if (i.z < size.z - 1) return Vec3i(0, i.y, i.z + 1)

        return Vec3i(0, i.y + 1, 0)
    }

    /**
     * Итерация сначала по -X, затем по -Z
     */
    private fun nextNorth(i: Vec3i): Vec3i {
        if (i.x > 0) return Vec3i(i.x - 1, i.y, i.z)

        if (i.z > 0) return Vec3i(size.x - 1, i.y, i.z - 1)

        return Vec3i(size.x - 1, i.y + 1, size.z - 1)
    }

    /**
     * Итерация сначала по +X, затем по +Z
     */
    private fun nextWest(i: Vec3i): Vec3i {
        TODO()
    }

    /**
     * Итерация сначала по +X, затем по +Z
     */
    private fun nextEast(i: Vec3i): Vec3i {
        TODO()
    }

    fun isCorrect(chest: org.bukkit.block.Chest): Boolean {
        val chestData = chest.blockData as Chest
        val chestFacing = chestData.facing
        Bukkit.getLogger().log(Level.INFO, "$chestFacing")
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

        val chestOrigin = Vec3i(chest.x, chest.y, chest.z)
        val structureOrigin = chestOrigin.subtract(center)
//            when (chestFacing) {
//                BlockFace.SOUTH -> Vec3i(center.x, center.y, center.z)
//                BlockFace.NORTH -> Vec3i(-center.x, center.y, -center.z)
//                BlockFace.WEST -> Vec3i(-center.z, center.y, center.x)
//                BlockFace.EAST -> Vec3i(center.z, center.y, -center.x)
//                else -> TODO()
//            }
        var relativePos = when (chestFacing) {
            BlockFace.SOUTH -> Vec3i(0, 0, 0)
            BlockFace.NORTH -> Vec3i(size.x - 1, 0, size.z - 1)
            BlockFace.WEST -> Vec3i(size.x - 1, 0, 0)
            BlockFace.EAST -> Vec3i(0, 0, size.z - 1)
            else -> TODO()
        }
        val iterator: (Vec3i) -> Vec3i = when (chestFacing) {
            BlockFace.SOUTH -> { i -> nextSouth(i) }
            BlockFace.NORTH -> { i -> nextNorth(i) }
            BlockFace.WEST -> { i -> nextWest(i) }
            BlockFace.EAST -> { i -> nextEast(i) }
            else -> TODO()
        }
        val world = chest.world
        var y = 0
        while (y < size.y) {
            var x = 0
            while (x < size.x) {
                var z = 0
                while (z < size.z) {
                    val material = blocks[x][y][z]

                    // TODO:
                    world.getBlockAt(
                        structureOrigin.x + relativePos.x,
                        structureOrigin.y + relativePos.y,
                        structureOrigin.z + relativePos.z
                    ).type = material

                    val worldMaterial = world.getBlockAt(
                        structureOrigin.x + relativePos.x,
                        structureOrigin.y + relativePos.y,
                        structureOrigin.z + relativePos.z
                    ).type

                    Bukkit.getLogger().log(
                        Level.INFO, "(${relativePos.toShortString()}) ($x $y $z): $material VS $worldMaterial"
                    )
                    if (!isMaterialEqual(material, worldMaterial)) return false

                    z++
                    relativePos = iterator(relativePos)
                }
                x++
            }
            y++
        }

        return true
    }

    fun build(location: Location) {
        val world = location.world
        val origin = Vec3i(location.x.toInt(), location.y.toInt(), location.z.toInt()).subtract(center)
        var y = 0
        while (y < size.y) {
            var x = 0
            while (x < size.x) {
                var z = 0
                while (z < size.z) {
                    val material = blocks[x][y][z]
                    world.getBlockAt(origin.x + x, origin.y + y, origin.z + z).type = material

                    z++
                }
                x++
            }
            y++
        }
        world.getBlockAt(location).type = Material.CHEST
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