package ch.threema.app.voip.groupcall.sfu

import ch.threema.app.voip.groupcall.GroupCallException
import ch.threema.base.utils.generateRandomProtobufPadding
import ch.threema.domain.types.Identity
import ch.threema.protobuf.group_call.CallState

data class GroupCallState(
    val createdBy: ParticipantId,
    val createdAt: ULong,
    val participants: Set<ParticipantDescription>,
) {
    companion object {
        fun fromProtobufBytes(bytes: ByteArray): GroupCallState {
            val state = CallState.parseFrom(bytes)
            val participants = state.participantsMap
                .map { (id, participant) ->
                    mapParticipant(
                        ParticipantId(id.toUInt()),
                        participant,
                    )
                }
                .toSet()
            return GroupCallState(
                ParticipantId(state.stateCreatedBy.toUInt()),
                state.stateCreatedAt.toULong(),
                participants,
            )
        }

        private fun mapParticipant(
            id: ParticipantId,
            participant: CallState.Participant,
        ): ParticipantDescription {
            return when {
                participant.hasThreema() -> mapNormalParticipant(id, participant)
                participant.hasGuest() -> mapGuestParticipant(id, participant)
                else -> throw GroupCallException("Cannot map state participant")
            }
        }

        private fun mapNormalParticipant(
            id: ParticipantId,
            participant: CallState.Participant,
        ): NormalParticipantDescription {
            return SimpleNormalParticipantDescription(
                id,
                Identity(participant.threema.identity),
                participant.threema.nickname,
            )
        }

        private fun mapGuestParticipant(
            id: ParticipantId,
            participant: CallState.Participant,
        ): GuestParticipantDescription {
            return SimpleGuestParticipantDescription(
                id,
                participant.guest.name,
            )
        }
    }

    fun toProtobuf(): CallState {
        val builder = CallState.newBuilder()
            .setPadding(generateRandomProtobufPadding())
            .setStateCreatedAt(createdAt.toLong())
            .setStateCreatedBy(createdBy.id.toInt())

        participants.forEach {
            val participant = CallState.Participant.newBuilder()

            if (it is NormalParticipantDescription) {
                val threema = CallState.Participant.Normal.newBuilder()
                    .setIdentity(it.identity.value)
                    .setNickname(it.nickname)
                    .build()
                participant.threema = threema
            } else if (it is GuestParticipantDescription) {
                val guest = CallState.Participant.Guest.newBuilder()
                    .setName(it.name)
                    .build()
                participant.guest = guest
            }
            builder.putParticipants(it.id.id.toInt(), participant.build())
        }
        return builder.build()
    }

    override fun toString(): String {
        return "GroupCallState(createdBy=$createdBy, createdAt=$createdAt, participants(${participants.size})=${participants.map { it.id }})"
    }
}
