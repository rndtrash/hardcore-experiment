package ru.teasanctuary.hardcore_experiment.types

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataType
import java.nio.ByteBuffer
import java.util.*

// TODO: Ну и хуета! ComplexType не хочет принимать Pair<UUID, PlayerStateChangeRequest>
data class StateChangePair(val player: UUID, val state: PlayerStateChangeRequest)

class StateChangeQueueDataType : PersistentDataType<ByteArray, StateChangePair> {
    override fun getPrimitiveType(): Class<ByteArray> {
        // TODO: почему это не работает??????
        // return Byte::class.java
        return PersistentDataType.BYTE_ARRAY.primitiveType
    }

    override fun getComplexType(): Class<StateChangePair> {
        return StateChangePair::class.java
    }

    override fun fromPrimitive(primitive: ByteArray, context: PersistentDataAdapterContext): StateChangePair {
        val bb = ByteBuffer.wrap(primitive)

        return StateChangePair(
            UUID(bb.getLong(), bb.getLong()), PlayerStateChangeRequest(
                PlayerStateDataType().fromPrimitive(bb.get(), context), Location(
                    Bukkit.getWorld(UUID(bb.getLong(), bb.getLong())),
                    bb.getInt().toDouble(),
                    bb.getInt().toDouble(),
                    bb.getInt().toDouble()
                )
            )
        )
    }

    override fun toPrimitive(complex: StateChangePair, context: PersistentDataAdapterContext): ByteArray {
        val bb = ByteBuffer.wrap(ByteArray(16 + 1 + 4 + 4 + 4 + 16))

        bb.putLong(complex.player.mostSignificantBits)
        bb.putLong(complex.player.leastSignificantBits)

        bb.put(PlayerStateDataType().toPrimitive(complex.state.state, context))

        val location = complex.state.location

        val worldUid = location.world.uid
        bb.putLong(worldUid.mostSignificantBits)
        bb.putLong(worldUid.leastSignificantBits)

        bb.putInt(location.blockX)
        bb.putInt(location.blockY)
        bb.putInt(location.blockZ)

        return bb.array()
    }
}