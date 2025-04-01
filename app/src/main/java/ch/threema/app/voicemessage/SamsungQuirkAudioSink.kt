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

package ch.threema.app.voicemessage

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import ch.threema.base.utils.LoggingUtil
import java.nio.ByteBuffer

/**
 * Handle audio sink error on some Samsung devices when changing AudioAttributes by retrying
 */
@UnstableApi
class SamsungQuirkAudioSink(
    context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean,
    private val delegate: AudioSink = DefaultAudioSink.Builder()
        .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .build()
) : AudioSink by delegate {
    private val logger = LoggingUtil.getThreemaLogger("SamsungQuirkAudioSink")

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        for (i in 0..4) {
            try {
                return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
            } catch (e: AudioSink.InitializationException) {
                logger.info("Unable to initialize audio sink. Try {}", i)
                if (i >= 4) {
                    // give up
                    throw e
                }
            }
        }
        return false
    }
}
