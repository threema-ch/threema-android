package ch.threema.app.voicemessage

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import ch.threema.base.utils.getThreemaLogger
import java.nio.ByteBuffer

private val logger = getThreemaLogger("SamsungQuirkAudioSink")

/**
 * Handle audio sink error on some Samsung devices when changing AudioAttributes by retrying
 */
@UnstableApi
class SamsungQuirkAudioSink(
    context: Context,
    enableFloatOutput: Boolean,
    enableAudioTrackPlaybackParams: Boolean,
    private val delegate: AudioSink = DefaultAudioSink.Builder()
        .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .build(),
) : AudioSink by delegate {
    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
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

class SamsungQuirkRenderersFactory(val context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        return SamsungQuirkAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
    }
}
