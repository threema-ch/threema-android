package ch.threema.domain.protocol.csp.messages.ballot

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.IncomingMessage
import ch.threema.protobuf.d2d.OutgoingMessage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONException

private val logger = getThreemaLogger("PollVoteMessage")

/**
 * A poll vote message.
 */
open class PollVoteMessage : AbstractMessage(), BallotVoteInterface {
    override var ballotId: BallotId? = null
    override var ballotCreatorIdentity: IdentityString? = null
    override val votes: MutableList<BallotVote> = mutableListOf()

    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_0

    override fun allowUserProfileDistribution(): Boolean = true

    override fun exemptFromBlocking(): Boolean = false

    override fun createImplicitlyDirectContact(): Boolean = false

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = true

    override fun reflectSentUpdate(): Boolean = false

    override fun sendAutomaticDeliveryReceipt(): Boolean = false

    override fun bumpLastUpdate(): Boolean = false

    override fun addVotes(votes: List<BallotVote>) {
        this.votes.addAll(votes)
    }

    @Throws(BadMessageException::class)
    fun parseVotes(votesJsonArrayString: String?) {
        try {
            val votesJsonArray = JSONArray(votesJsonArrayString)
            for (n in 0 until votesJsonArray.length()) {
                votes.add(BallotVote.parse(votesJsonArray.getJSONArray(n)))
            }
        } catch (jsonException: JSONException) {
            throw BadMessageException("TM035", jsonException)
        }
    }

    override fun getBody(): ByteArray? {
        if (ballotCreatorIdentity == null || ballotId == null) {
            return null
        }
        try {
            val bos = ByteArrayOutputStream()
            bos.write(ballotCreatorIdentity!!.toByteArray(StandardCharsets.US_ASCII))
            bos.write(ballotId!!.ballotId)
            val ballotVotesJsonArray = JSONArray()
            for (ballotVote in votes) {
                ballotVotesJsonArray.put(ballotVote.jsonArray)
            }
            bos.write(ballotVotesJsonArray.toString().toByteArray(StandardCharsets.US_ASCII))
            return bos.toByteArray()
        } catch (exception: Exception) {
            logger.error(exception.message)
            return null
        }
    }

    override fun getType(): Int = ProtocolDefines.MSGTYPE_BALLOT_VOTE

    companion object {
        @JvmStatic
        fun fromReflected(message: IncomingMessage): PollVoteMessage = fromByteArray(
            data = message.body.toByteArray(),
        ).apply {
            initializeCommonProperties(message)
        }

        @JvmStatic
        fun fromReflected(message: OutgoingMessage): PollVoteMessage = fromByteArray(
            data = message.body.toByteArray(),
        ).apply {
            initializeCommonProperties(message)
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray): PollVoteMessage = fromByteArray(
            data = data,
            offset = 0,
            length = data.size,
        )

        /**
         * Get the poll-vote message from the given array.
         *
         * @param data   the data that represents the message
         * @param offset the offset where the data starts
         * @param length the length of the data (needed to ignore the padding)
         * @return the poll-vote message
         * @throws BadMessageException if the length is invalid
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, offset: Int, length: Int): PollVoteMessage {
            if (length < 1) {
                throw BadMessageException("Bad length ($length) for poll vote message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for poll vote message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }

            return PollVoteMessage().apply {
                var positionIndex = offset

                ballotCreatorIdentity = String(
                    data,
                    positionIndex,
                    ProtocolDefines.IDENTITY_LEN,
                    StandardCharsets.US_ASCII,
                )
                positionIndex += ProtocolDefines.IDENTITY_LEN

                ballotId = BallotId(data, positionIndex)
                positionIndex += ProtocolDefines.BALLOT_ID_LEN

                parseVotes(
                    String(
                        data,
                        positionIndex,
                        length + offset - positionIndex,
                        StandardCharsets.UTF_8,
                    ),
                )
            }
        }
    }
}
