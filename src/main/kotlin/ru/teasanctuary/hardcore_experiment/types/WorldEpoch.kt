package ru.teasanctuary.hardcore_experiment.types

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.math.ceil
import kotlin.math.pow

enum class WorldEpoch(val items: List<Material>) {
    /**
     * Эпоха угля. Бесплатное возрождение, но длится ограниченное время.
     */
    Coal(listOf(Material.COAL, Material.COAL_BLOCK, Material.COAL_ORE)) {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return ItemStack.empty() // Бесплатный респаун
        }

    },
    Copper(
        listOf(
            Material.RAW_COPPER,
            Material.COPPER_INGOT,
            Material.COPPER_ORE,
            Material.COPPER_BLOCK,
            Material.EXPOSED_COPPER,
            Material.WEATHERED_COPPER,
            Material.OXIDIZED_COPPER,
            Material.WAXED_COPPER_BLOCK,
            Material.WAXED_EXPOSED_COPPER,
            Material.WAXED_WEATHERED_COPPER,
            Material.WAXED_OXIDIZED_COPPER
        )
    ) {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            val days = maxOf(1, epochDuration / dayLength)
            val count = ceil(days.toDouble().pow(1.2)).toInt()
            return ItemStack.of(Material.COPPER_INGOT, count)
        }
    },
    Iron(
        listOf(
            Material.RAW_IRON,
            // Причина: может быть найден в сундуках данжей
            // Material.IRON_NUGGET,
            // Material.IRON_INGOT,
            Material.IRON_BLOCK,
            // Инструменты
            Material.SHIELD,
            Material.IRON_AXE,
            Material.IRON_PICKAXE,
            Material.IRON_HOE,
            Material.IRON_SHOVEL,
            Material.IRON_SWORD,
            // Броня
            Material.IRON_BOOTS,
            Material.IRON_CHESTPLATE,
            Material.IRON_HELMET,
            Material.IRON_LEGGINGS
        )
    ) {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    },
    Gold(
        listOf(
            Material.RAW_GOLD,
            // Причина: может быть найден в сундуках данжей
            // Material.GOLD_NUGGET,
            // Material.GOLD_INGOT,
            Material.GOLD_BLOCK,
            // Еда
            Material.GOLDEN_APPLE,
            Material.GOLDEN_CARROT,
            Material.GLISTERING_MELON_SLICE,
            // Инструменты
            Material.CLOCK,
            Material.GOLDEN_AXE,
            Material.GOLDEN_PICKAXE,
            Material.GOLDEN_HOE,
            Material.GOLDEN_SHOVEL,
            Material.GOLDEN_SWORD,
            // Броня
            Material.GOLDEN_BOOTS,
            Material.GOLDEN_CHESTPLATE,
            Material.GOLDEN_HELMET,
            Material.GOLDEN_LEGGINGS,
            // Прочее
            Material.POWERED_RAIL
        )
    ) {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    },
    Diamond(
        listOf(
            // Причина: может быть найден в сундуках данжей
            // Material.DIAMOND,
            Material.DIAMOND_BLOCK,
            // Инструменты
            Material.DIAMOND_AXE,
            Material.DIAMOND_HOE,
            Material.DIAMOND_PICKAXE,
            Material.DIAMOND_SHOVEL,
            Material.DIAMOND_SWORD,
            // Броня
            Material.DIAMOND_BOOTS,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_HELMET,
            Material.DIAMOND_LEGGINGS
        )
    ) {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    },
    Obsidian(
        listOf(
            Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
            // Инструменты
            Material.ENCHANTING_TABLE, Material.BEACON, Material.ENDER_CHEST, Material.RESPAWN_ANCHOR,
            // Прочее
            Material.ENDER_EYE
        )
    ) {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    },

    /**
     * Эпоха незерита. Возрождение невозможно.
     */
    Netherite(
        listOf(
            Material.ANCIENT_DEBRIS,
            Material.NETHERITE_SCRAP,
            Material.NETHERITE_INGOT,
            Material.NETHERITE_BLOCK,
            // Инструменты
            Material.NETHERITE_AXE,
            Material.NETHERITE_HOE,
            Material.NETHERITE_PICKAXE,
            Material.NETHERITE_SHOVEL,
            Material.NETHERITE_SWORD,
            // Броня
            Material.NETHERITE_BOOTS,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_HELMET,
            Material.NETHERITE_LEGGINGS
        )
    ) {
        override fun canRespawn(): Boolean = false

        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    };

    companion object {
        private val dayLength = 24000L

        /**
         * Таблица принадлежности каждого предмета к эпохе. Формируется автоматически на основе всех эпох.
         *
         * @see WorldEpoch
         */
        val itemToEpoch =
            EnumMap(WorldEpoch.entries.map { epoch -> epoch.items.associateWith { _ -> epoch } }.flatMap { it.entries }
                .associate { it.toPair() })
    }

    open fun canRespawn(): Boolean = true

    /**
     * @return ItemStack, если можно возродиться. null, если стоимость возрождения слишком высока.
     */
    abstract fun getRespawnCost(epochDuration: Long): ItemStack?
}