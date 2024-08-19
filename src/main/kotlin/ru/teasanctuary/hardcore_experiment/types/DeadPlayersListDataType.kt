package ru.teasanctuary.hardcore_experiment.types

import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import java.nio.ByteBuffer
import java.util.*

// TODO: Ну и хуета! ComplexType не хочет принимать Pair<UUID, DeadPlayerStatus>
data class DeadPlayerPair(val player: UUID, val status: DeadPlayerStatus)

class DeadPlayersListDataType : PersistentDataType<ByteArray, DeadPlayerPair> {
    override fun getPrimitiveType(): Class<ByteArray> {
        // TODO: почему это не работает??????
        // return Byte::class.java
        return PersistentDataType.BYTE_ARRAY.primitiveType
    }

    override fun getComplexType(): Class<DeadPlayerPair> {
        return DeadPlayerPair::class.java
    }

    override fun fromPrimitive(primitive: ByteArray, context: PersistentDataAdapterContext): DeadPlayerPair {
        val bb = ByteBuffer.wrap(primitive)

        return DeadPlayerPair(
            UUID(bb.getLong(), bb.getLong()),
            DeadPlayerStatus(bb.getLong(), WorldEpochDataType().fromPrimitive(bb.get(), context), bb.getLong())
        )
    }

    override fun toPrimitive(complex: DeadPlayerPair, context: PersistentDataAdapterContext): ByteArray {
        val bb = ByteBuffer.wrap(ByteArray(16 + 8 + 1 + 8))

        bb.putLong(complex.player.mostSignificantBits)
        bb.putLong(complex.player.leastSignificantBits)

        bb.putLong(complex.status.epochTimeStamp)
        bb.put(WorldEpochDataType().toPrimitive(complex.status.epoch, context))
        bb.putLong(complex.status.deadline)

        return bb.array()
    }
}