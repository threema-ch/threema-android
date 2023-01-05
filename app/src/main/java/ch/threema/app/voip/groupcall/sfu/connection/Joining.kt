/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu.connection

import android.content.Context
import ch.threema.app.ThreemaApplication
import ch.threema.app.utils.WebRTCUtil
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.app.voip.groupcall.sfu.GroupCall
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.ContactModel
import org.webrtc.RtcCertificatePem
import java.security.MessageDigest
import javax.security.cert.X509Certificate

private val logger = LoggingUtil.getThreemaLogger("GroupCallConnectionState.Joining")

class Joining internal constructor(
    call: GroupCall,
    private val sfuBaseUrl: String,
    private val context: Context,
    private val me: ContactModel,
    private val sfuConnection: SfuConnection
) : GroupCallConnectionState(StateName.JOINING, call) {
    init {
        // Initialize and create peer connection factory
        WebRTCUtil.initializePeerConnectionFactory(
            ThreemaApplication.getAppContext(), WebRTCUtil.Scope.CALL_OR_GROUP_CALL_OR_WEB_CLIENT)
    }

    override fun getStateProviders() = listOf(
        this::observeCallEnd,
        this::getNextState
    )

    private suspend fun getNextState(): GroupCallConnectionState {
        val certificate = RtcCertificatePem.generateCertificate()

        // Extract fingerprint from PEM certificate
        val x509Certificate = X509Certificate.getInstance(certificate.certificate.toByteArray(Charsets.UTF_8))
        val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(x509Certificate.encoded)

        val joinResponse = join(fingerprint, 2)
        return if (!joinResponse.isHttpOk || joinResponse.body == null) {
            Failed(call, SfuException(
                "Join failed with status code ${joinResponse.statusCode}",
                joinResponse.statusCode,
                call.description
            ))
        } else {
            Connecting(call, me, context, certificate, joinResponse.body)
        }
    }

    private suspend fun join(fingerprint: ByteArray, retriesOnInvalidToken: Int, forceTokenRefresh: Boolean = false): JoinResponse {
        val response = sfuConnection.join(sfuConnection.obtainSfuToken(forceTokenRefresh), sfuBaseUrl, call.description, fingerprint)
        return if (response.statusCode == HTTP_STATUS_TOKEN_INVALID && retriesOnInvalidToken > 0) {
            logger.info("Retry joining with refreshed token")
            join(fingerprint, retriesOnInvalidToken - 1, true)
        } else {
            response
        }
    }
}
