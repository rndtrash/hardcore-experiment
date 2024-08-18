package ru.teasanctuary.hardcore_experiment.types

import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import java.nio.ByteBuffer

class DeadPlayerStatusDataType : PersistentDataType<ByteArray, DeadPlayerStatus> {
    override fun getPrimitiveType(): Class<ByteArray> {
        // TODO: почему это не работает??????
        // return Byte::class.java
        return PersistentDataType.BYTE_ARRAY.primitiveType
    }

    override fun getComplexType(): Class<DeadPlayerStatus> {
        return DeadPlayerStatus::class.java
    }

    override fun fromPrimitive(primitive: ByteArray, context: PersistentDataAdapterContext): DeadPlayerStatus {
        val bb = ByteBuffer.wrap(primitive)

        return DeadPlayerStatus(bb.getLong(), WorldEpochDataType().fromPrimitive(bb.get(), context), bb.getLong())
    }

    override fun toPrimitive(complex: DeadPlayerStatus, context: PersistentDataAdapterContext): ByteArray {
        val bb = ByteBuffer.wrap(ByteArray(8 + 1 + 8))

        bb.putLong(complex.epochTimeStamp)
        bb.put(WorldEpochDataType().toPrimitive(complex.epoch, context))
        bb.putLong(complex.deadline)

        return bb.array()
    }
}