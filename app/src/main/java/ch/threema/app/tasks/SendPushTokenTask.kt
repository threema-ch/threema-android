/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.tasks

import androidx.preference.PreferenceManager
import ch.threema.app.R
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.PushUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets

private val logger = LoggingUtil.getThreemaLogger("SendPushTokenTask")

class SendPushTokenTask(
    private val token: String,
    private val tokenType: Int,
    private val serviceManager: ServiceManager,
) : ActiveTask<Unit>,
    PersistableTask {
    override val type: String = "SendPushTokenTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val resetPushToken = token.isEmpty()

        // In case the push token should be reset, we must not check whether it needs to be refreshed
        if (!resetPushToken && !PushUtil.pushTokenNeedsRefresh(serviceManager.context)) {
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

        PreferenceManager.getDefaultSharedPreferences(serviceManager.context)
            .edit()
            .putLong(
                serviceManager.context.getString(R.string.preferences__token_sent_date),
                sentTime
            )
            .apply()

        // Used in the Webclient Sessions
        serviceManager.getPreferenceService().setPushToken(token)
    }

    private suspend fun sendPushToken(handle: ActiveTaskCodec) {
        val tokenBytes = token.toByteArray(StandardCharsets.US_ASCII)
        val type = byteArrayOf(tokenType.toByte())
        val tokenData = type + tokenBytes

        // Send regular push token
        handle.write(CspMessage(ProtocolDefines.PLTYPE_PUSH_NOTIFICATION_TOKEN.toUByte(), tokenData))
        // Send voip push token. This is identical to the regular push token.
        handle.write(CspMessage(ProtocolDefines.PLTYPE_VOIP_PUSH_NOTIFICATION_TOKEN.toUByte(), tokenData))

        logger.info("Push token successfully sent to server")
    }

    override fun serialize(): SerializableTaskData = SendPushTokenData(token, tokenType)

    @Serializable
    data class SendPushTokenData(private val token: String, private val tokenType: Int) :
        SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            SendPushTokenTask(token, tokenType, serviceManager)
    }
}
