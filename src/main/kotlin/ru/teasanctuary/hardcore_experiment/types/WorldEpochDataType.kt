package ru.teasanctuary.hardcore_experiment.types

import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType

class WorldEpochDataType : PersistentDataType<Byte, WorldEpoch> {
    override fun getPrimitiveType(): Class<Byte> {
        // TODO: почему это не работает??????
        // return Byte::class.java
        return PersistentDataType.BYTE.primitiveType
    }

    override fun getComplexType(): Class<WorldEpoch> {
        return WorldEpoch::class.java
    }

    override fun fromPrimitive(primitive: Byte, context: PersistentDataAdapterContext): WorldEpoch {
        return WorldEpoch.entries[primitive.toInt()]
    }

    override fun toPrimitive(complex: WorldEpoch, context: PersistentDataAdapterContext): Byte {
        return complex.ordinal.toByte()
    }
}