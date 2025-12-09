/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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
import ch.threema.base.utils.Utils.hexStringToByteArray
import ch.threema.base.utils.getThreemaLogger
import ch.threema.storage.models.ContactModel
import org.webrtc.RtcCertificatePem

private val FINGERPRINT_REGEX = Regex("^sha-256 (([0-9a-zA-Z]{2}:?){32})\$")
private val logger = getThreemaLogger("GroupCallConnectionState.Joining")

class Joining internal constructor(
    call: GroupCall,
    private val sfuBaseUrl: String,
    private val context: Context,
    private val me: ContactModel,
    private val sfuConnection: SfuConnection,
) : GroupCallConnectionState(StateName.JOINING, call) {
    init {
        // Initialize and create peer connection factory
        WebRTCUtil.initializePeerConnectionFactory(
            ThreemaApplication.getAppContext(),
            WebRTCUtil.Scope.CALL_OR_GROUP_CALL_OR_WEB_CLIENT,
        )
    }

    override fun getStateProviders() = listOf(
        this::observeCallEnd,
        this::getNextState,
    )

    private suspend fun getNextState(): GroupCallConnectionState {
        val certificate = RtcCertificatePem.generateCertificate()
        logger.debug("Generated certificate with fingerprint {}", certificate.fingerprint)
        val fingerprint = FINGERPRINT_REGEX.find(certificate.fingerprint)?.groups?.get(1).let {
            if (it == null) {
                throw Error("Expected fingerprint to be a SHA-256 digest")
            }
            hexStringToByteArray(it.value.replace(":", ""))
        }

        val joinResponse = join(fingerprint, 2)
        return if (!joinResponse.isHttpOk || joinResponse.body == null) {
            Failed(
                call,
                SfuException(
                    "Join failed with status code ${joinResponse.statusCode}",
                    joinResponse.statusCode,
                    call.description,
                ),
            )
        } else {
            Connecting(call, me, context, certificate, joinResponse.body)
        }
    }

    private suspend fun join(
        fingerprint: ByteArray,
        retriesOnInvalidToken: Int,
        forceTokenRefresh: Boolean = false,
    ): JoinResponse {
        val response = sfuConnection.join(
            sfuConnection.obtainSfuToken(forceTokenRefresh),
            sfuBaseUrl,
            call.description,
            fingerprint,
        )
        return if (response.statusCode == HTTP_STATUS_TOKEN_INVALID && retriesOnInvalidToken > 0) {
            logger.info("Retry joining with refreshed token")
            join(fingerprint, retriesOnInvalidToken - 1, true)
        } else {
            response
        }
    }
}
