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

package ch.threema.app.voip.groupcall.sfu.webrtc

import android.content.Context
import androidx.annotation.WorkerThread
import ch.threema.base.utils.LoggingUtil
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class FactoryCtx(
        context: Context,
        val parameters: Parameters,
) {
    data class Parameters(
        val acousticEchoCancelerMode: AecMode,
        val hardwareVideoCodecs: Set<HardwareVideoCodec>,
    ) {
        enum class AecMode { HARDWARE, SOFTWARE }

        /**
         * Supported hardware video codecs.
         *
         * Note: We only use VP8 for group calls right now.
         */
        enum class HardwareVideoCodec { VP8 }
    }

    private var _eglBase: EglBase? = EglBase.create()
    private var _surfaceTextureHelper: SurfaceTextureHelper? =
        SurfaceTextureHelper.create("GroupCallVideoCapture", eglBase.eglBaseContext, false)
    private var _factory: PeerConnectionFactory?

    private val logger = LoggingUtil.getThreemaLogger("GroupCall.FactoryCtx")
    internal val eglBase: EglBase
        get() = checkNotNull(_eglBase) { "EglBase already released" }
    internal val surfaceTextureHelper: SurfaceTextureHelper
        get() = checkNotNull(_surfaceTextureHelper) { "SurfaceTextureHelper already disposed" }
    internal val factory: PeerConnectionFactory
        get() = checkNotNull(_factory) { "PeerConnectionFactory already disposed" }

    init {
        // Apply audio device settings
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(parameters.acousticEchoCancelerMode == Parameters.AecMode.HARDWARE)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()

        // Determine video encoder/decoder factories
        val (encoder, decoder) = parameters.hardwareVideoCodecs.let { hardwareVideoCodecs ->
            if (hardwareVideoCodecs.isEmpty()) {
                logger.trace("Using software-based video codecs")
                Pair(SoftwareVideoEncoderFactory(), SoftwareVideoDecoderFactory())
            } else {
                val eglBaseContext = eglBase.eglBaseContext
                logger.trace("Using hardware-based video codecs ({})", hardwareVideoCodecs)
                Pair(
                    // Note: VP8 hardware encoder does not seem to support simulcast, so we are
                    //       left with software. :( Leaving the code in case it works at some point
                    //       in the future.
//                    DefaultVideoEncoderFactory(
//                        eglBaseContext,
//                        hardwareVideoCodecs.contains(Parameters.HardwareVideoCodec.VP8),
//                        false,
//                    ),
                    SoftwareVideoEncoderFactory(),
                    DefaultVideoDecoderFactory(eglBaseContext),
                )
            }
        }
        logger.trace("Creating peer connection factory")
        _factory = checkNotNull(PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()
        )
        audioDeviceModule.release()

        // Done
        logger.trace("Initialised")
    }

    /**
     * IMPORTANT: Make sure this is executed in the ConnectionCtx-Worker
     */
    @WorkerThread
    internal fun teardown() {
        logger.trace("Teardown: FactoryCtx")

        logger.trace("Teardown: Dispose PeerConnectionFactory")
        _factory?.dispose()
        _factory = null

        logger.trace("Teardown: Dispose SurfaceTextureViewHelper")
        _surfaceTextureHelper?.dispose()
        _surfaceTextureHelper = null

        logger.trace("Teardown: Release EglBase")
        _eglBase?.release()
        _eglBase = null

        logger.trace("Teardown: /FactoryCtx")
    }
}
