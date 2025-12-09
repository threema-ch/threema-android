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

package ch.threema.app.voip.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.services.ContactService
import ch.threema.app.voip.activities.CallActivity
import ch.threema.app.voip.util.VoipUtil
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val KEY_CALL_ID = "call_id"
const val KEY_CONTACT_IDENTITY = "contact_identity"
const val KEY_REJECT_REASON = "reject_reason"

private val logger = getThreemaLogger("CallRejectWorker")

/**
 * Takes a call id, identity and reject reason as arguments and rejects the incoming call.
 */
class RejectIntentServiceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val voipStateService: VoipStateService by inject()
    private val contactService: ContactService by inject()

    /**
     * Performs the call reject.
     */
    override suspend fun doWork(): Result {
        awaitAppFullyReadyWithTimeout(20.seconds)
            ?: return Result.failure()

        // Check call id
        val callId = inputData.keyValueMap[KEY_CALL_ID]
        if (callId !is Long) {
            logger.error("Reject failed: (callId is not a Long: {})", callId)
            return Result.failure()
        }

        // Set logging prefix
        VoipUtil.setLoggerPrefix(logger, callId)

        // Check identity
        val identity = inputData.getString(KEY_CONTACT_IDENTITY) ?: run {
            logger.info("Reject failed for call id {} (contact identity is null)", callId)
            return Result.failure()
        }

        // Get reject reason
        val rejectReason =
            inputData.getByte(KEY_REJECT_REASON, VoipCallAnswerData.RejectReason.UNKNOWN)

        // Reject call
        return if (rejectReason == VoipCallAnswerData.RejectReason.TIMEOUT) {
            rejectCallTimeout(voipStateService, contactService, callId, identity, rejectReason)
        } else {
            rejectCall(voipStateService, contactService, callId, identity, rejectReason)
        }
    }

    /**
     * Rejects the call because of the ringing timeout. In this case perform some additional checks
     * because the state could have been changed in the meanwhile.
     */
    private fun rejectCallTimeout(
        voipStateService: VoipStateService,
        contactService: ContactService,
        callId: Long,
        contactIdentity: Identity,
        rejectReason: Byte,
    ): Result {
        val currentCallState = voipStateService.callState

        if (!currentCallState.isRinging) {
            logger.info(
                "Ignoring ringer timeout for call {} (state is {}, not RINGING)",
                callId,
                currentCallState.name,
            )
            return Result.success()
        } else if (currentCallState.callId != callId) {
            logger.info(
                "Ignoring ringer timeout for call {} (current: {})",
                callId,
                currentCallState.callId,
            )
            return Result.success()
        } else if (!voipStateService.isTimeoutReject) {
            logger.info(
                "Ignoring ringer timeout for call {} (timeout reject is disabled)",
                callId,
            )
            return Result.success()
        }

        logger.info("Ringer timeout for call {} reached", callId)

        return rejectCall(voipStateService, contactService, callId, contactIdentity, rejectReason)
    }
}

/**
 * Rejects the current call.
 */
fun rejectCall(
    voipStateService: VoipStateService,
    contactService: ContactService,
    callId: Long,
    contactIdentity: Identity,
    rejectReason: Byte,
): ListenableWorker.Result {
    // Cancel current notification
    voipStateService.cancelCallNotification(contactIdentity, CallActivity.ACTION_CANCELLED)

    // Get contact
    val contact: ContactModel =
        contactService.getByIdentity(contactIdentity) ?: run {
            logger.error("Could not get contact model for \"{}\"", contactIdentity)
            return ListenableWorker.Result.failure()
        }
    try {
        // Reject call
        logger.debug(
            "Rejecting call from {} (reason {})",
            contactIdentity,
            rejectReason,
        )
        voipStateService.sendRejectCallAnswerMessage(contact, callId, rejectReason)
    } catch (e: ThreemaException) {
        logger.error("Could not send reject answer message", e)
    }

    // Reset state
    voipStateService.setStateIdle()

    // Clear the candidates cache
    voipStateService.clearCandidatesCache(contactIdentity)

    return ListenableWorker.Result.success()
}
