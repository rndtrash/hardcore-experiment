package ru.teasanctuary.hardcore_experiment.types

import org.bukkit.inventory.ItemStack

enum class WorldEpoch {
    /**
     * Эпоха угля. Бесплатное возрождение, но длится ограниченное время.
     */
    Coal {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return ItemStack.empty() // Бесплатный респаун
        }

    },
    Copper {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            //return ItemStack(Material.COPPER_INGOT, maxOf(1, epochDuration))
            return null
        }
    },
    Iron {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    },
    Gold {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    },
    Diamond {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    },
    Obsidian {
        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    },

    /**
     * Эпоха незерита. Возрождение невозможно.
     */
    Netherite {
        override fun canRespawn(): Boolean = false

        override fun getRespawnCost(epochDuration: Long): ItemStack? {
            return null
        }
    };

    open fun canRespawn(): Boolean = true

    /**
     * @return ItemStack, если можно возродиться. null, если стоимость возрождения слишком высока.
     */
    abstract fun getRespawnCost(epochDuration: Long): ItemStack?
}