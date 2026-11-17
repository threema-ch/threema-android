package ch.threema.app.voicemessage

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.Format
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioOutputProvider
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

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        delegate.configure(audioSinkConfig)
    }

    override fun getAudioCapabilities(): AudioCapabilities? =
        delegate.audioCapabilities

    override fun setPreferredDevice(audioDeviceInfo: AudioDeviceInfo?) {
        delegate.setPreferredDevice(audioDeviceInfo)
    }

    override fun setVirtualDeviceId(virtualDeviceId: Int) {
        delegate.setVirtualDeviceId(virtualDeviceId)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        delegate.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun setOffloadMode(offloadMode: Int) {
        delegate.setOffloadMode(offloadMode)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun setOffloadDelayPadding(delayInFrames: Int, paddingInFrames: Int) {
        delegate.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    }

    override fun setAudioOutputProvider(audioOutputProvider: AudioOutputProvider) {
        delegate.setAudioOutputProvider(audioOutputProvider)
    }

    override fun release() {
        delegate.release()
    }

    override fun setPlayerId(playerId: PlayerId?) {
        delegate.setPlayerId(playerId)
    }

    override fun setClock(clock: Clock) {
        delegate.setClock(clock)
    }

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport {
        return delegate.getFormatOffloadSupport(format)
    }

    @Deprecated("Deprecated in Java")
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
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
