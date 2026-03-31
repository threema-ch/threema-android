package ch.threema.app.tasks

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.utils.PushUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("SendPushTokenTask")

class SendPushTokenTask(
    private val token: String,
    private val tokenType: Int,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val context: Context by inject()
    private val preferenceService: PreferenceService by inject()

    override val type: String = "SendPushTokenTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val resetPushToken = token.isEmpty()

        // In case the push token should be reset, we must not check whether it needs to be refreshed
        if (!resetPushToken && !PushUtil.pushTokenNeedsRefresh(context)) {
            logger.warn("Push token does not need to be sent; aborting this task")
            return
        }

        sendPushToken(handle)

        // Reset token update timestamp if it was reset; set current update time otherwise
        val sentTime = if (resetPushToken) {
            // If the token has been reset, then set the timestamp to 0 so that the new token will
            // be sent again as soon as possible
            0L
        } else {
            // If the token has been set, then use the current timestamp
            System.currentTimeMillis()
        }

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit {
                putLong(
                    context.getString(R.string.preferences__token_sent_date),
                    sentTime,
                )
            }

        // Used in the Webclient Sessions
        preferenceService.setPushToken(token)
    }

    private suspend fun sendPushToken(handle: ActiveTaskCodec) {
        val tokenBytes = token.toByteArray(StandardCharsets.US_ASCII)
        val type = byteArrayOf(tokenType.toByte())
        val tokenData = type + tokenBytes

        // Send regular push token
        handle.write(
            CspMessage(
                ProtocolDefines.PLTYPE_PUSH_NOTIFICATION_TOKEN.toUByte(),
                tokenData,
            ),
        )
        // Send voip push token. This is identical to the regular push token.
        handle.write(
            CspMessage(
                ProtocolDefines.PLTYPE_VOIP_PUSH_NOTIFICATION_TOKEN.toUByte(),
                tokenData,
            ),
        )

        logger.info("Push token successfully sent to server")
    }

    override fun serialize(): SerializableTaskData = SendPushTokenData(token, tokenType)

    @Serializable
    data class SendPushTokenData(private val token: String, private val tokenType: Int) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            SendPushTokenTask(token, tokenType)
    }
}
