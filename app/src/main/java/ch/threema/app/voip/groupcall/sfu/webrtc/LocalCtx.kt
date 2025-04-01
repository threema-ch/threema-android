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

package ch.threema.app.voip.groupcall.sfu.webrtc

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.webrtc.LocalCameraVideoContext
import ch.threema.app.webrtc.LocalMicrophoneAudioContext
import ch.threema.app.webrtc.VideoCaptureSettings
import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

private val logger = LoggingUtil.getThreemaLogger("LocalCtx")

@WorkerThread
class LocalCtx private constructor(
    val microphoneAudioContext: LocalMicrophoneAudioContext,
    val cameraVideoContext: LocalCameraVideoContext,
    // Note: This is the place to add a screenshare context when desired
) {
    companion object {
        @WorkerThread
        internal fun create(context: Context, factory: FactoryCtx): LocalCtx {
            GroupCallThreadUtil.assertDispatcherThread()

            return LocalCtx(
                microphoneAudioContext = LocalMicrophoneAudioContext.create(factory),
                cameraVideoContext = LocalCameraVideoContext.create(context, factory)
                // TODO(ANDR-1952): Refine parameters
                { VideoCaptureSettings(width = 1280u, height = 960u, fps = 30u) }
            ).also {
                // microphone active by default
                it.microphoneAudioContext.active = true
            }
        }
    }

    @AnyThread
    fun teardown() {
        logger.trace("Teardown: Local")

        logger.trace("Teardown: LocalMicrophoneAudioContext")
        microphoneAudioContext.teardown()

        logger.trace("Teardown: LocalCameraVideoContext")
        cameraVideoContext.teardown()

        logger.trace("Teardown: /Local")
    }
}
