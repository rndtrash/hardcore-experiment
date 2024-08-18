package ru.teasanctuary.hardcore_experiment.types

import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType

class PlayerStateDataType : PersistentDataType<Byte, PlayerState> {
    override fun getPrimitiveType(): Class<Byte> {
        // TODO: почему это не работает??????
        // return Byte::class.java
        return PersistentDataType.BYTE.primitiveType
    }

    override fun getComplexType(): Class<PlayerState> {
        return PlayerState::class.java
    }

    override fun fromPrimitive(primitive: Byte, context: PersistentDataAdapterContext): PlayerState {
        return PlayerState.entries[primitive.toInt()]
    }

    override fun toPrimitive(complex: PlayerState, context: PersistentDataAdapterContext): Byte {
        return complex.ordinal.toByte()
    }
}