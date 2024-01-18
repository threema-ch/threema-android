/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu

import androidx.annotation.UiThread
import ch.threema.app.utils.NameUtil
import ch.threema.app.voip.groupcall.sfu.webrtc.LocalCtx
import ch.threema.app.voip.groupcall.sfu.webrtc.RemoteCtx
import ch.threema.app.webrtc.Camera
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.ContactModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

private val logger = LoggingUtil.getThreemaLogger("Participant")

@JvmInline
value class ParticipantId(val id: UInt) {
    init {
        if (id >= MIDS_MAX.toUInt()) {
            throw Error("Not a valid participant id: $id")
        }
    }
}

typealias DetachSinkFn = () -> Unit

interface ParticipantDescription {
    val id: ParticipantId
}

interface NormalParticipantDescription : ParticipantDescription {
    val identity: String
    val nickname: String
}

data class SimpleNormalParticipantDescription(
    override val id: ParticipantId,
    override val identity: String,
    override val nickname: String
) : NormalParticipantDescription {
    override fun toString(): String {
        return "SimpleNormalParticipantDescription(id=$id)"
    }
}

interface GuestParticipantDescription : ParticipantDescription {
    val name: String
}

data class SimpleGuestParticipantDescription(
    override val id: ParticipantId,
    override val name: String
) : GuestParticipantDescription

abstract class Participant(override val id: ParticipantId) : ParticipantDescription {

    open val mirrorRenderer: Boolean = false

    abstract val type: String

    abstract val name: String

    open var microphoneActive: Boolean = false

    open var cameraActive: Boolean = false

    @UiThread
    abstract fun subscribeCamera(
        renderer: SurfaceViewRenderer,
        width: Int,
        height: Int,
        fps: Int = 30
    ): DetachSinkFn

    @UiThread
    abstract fun unsubscribeCamera()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Participant) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

private interface RemoteParticipant {
    var remoteCtx: RemoteCtx?
}

abstract class NormalParticipant(
    id: ParticipantId,
    val contactModel: ContactModel
) : Participant(id), NormalParticipantDescription {

    override val identity: String = contactModel.identity
    override val nickname: String = contactModel.publicNickName ?: contactModel.identity

    override val name: String by lazy {
        NameUtil.getDisplayNameOrNickname(contactModel, true)
    }
}

abstract class NormalRemoteParticipant(
    id: ParticipantId,
    contactModel: ContactModel
) : NormalParticipant(id, contactModel), RemoteParticipant {
    override var remoteCtx: RemoteCtx? = null
}

class LocalParticipant internal constructor(
    id: ParticipantId,
    contactModel: ContactModel,
    private val localCtx: LocalCtx
) : NormalParticipant(id, contactModel) {

    override val type = "LocalParticipant"

    override val mirrorRenderer: Boolean
        get() = localCtx.cameraVideoContext.currentCamera?.facing == Camera.Facing.FRONT

    override var microphoneActive: Boolean = false
        set(value) {
            logger.info("Set microphone active={}", value)
            field = value
            localCtx.microphoneAudioContext.active = field
        }

    override var cameraActive: Boolean = false
        set(value) {
            logger.info("Set camera active={}", value)
            field = value
            CoroutineScope(Dispatchers.Main).launch {
                if (field) {
                    localCtx.cameraVideoContext.startCapturing()
                } else {
                    localCtx.cameraVideoContext.stopCapturing()
                }
            }
        }

    @UiThread
    override fun subscribeCamera(
        renderer: SurfaceViewRenderer,
        width: Int,
        height: Int,
        fps: Int): DetachSinkFn {
        logger.trace("Subscribe local camera")
        return localCtx.cameraVideoContext.renderTo(renderer)
    }

    @UiThread
    override fun unsubscribeCamera() {
        // no-op: Detach is performed in [DetachSinkFn] returned from [#subscribeCamera]
    }

    @UiThread
    suspend fun flipCamera() {
        try {
            localCtx.cameraVideoContext.flipCamera()
        } catch (e: Exception) {
            logger.warn("Could not toggle front/back camera", e)
        }
    }
}

// Not relevant in V1
// abstract class GuestParticipant(
//         id: ParticipantId,
//         override val name: String
// ) : Participant(id), RemoteParticipant, GuestParticipantDescription
