package ch.threema.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.annotation.MainThread
import androidx.annotation.RawRes
import androidx.core.content.getSystemService
import ch.threema.android.Destroyable
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("SoundEffectPlayer")

class SoundEffectPlayer(
    private val appContext: Context,
) : Destroyable {
    private val audioManager = appContext.getSystemService<AudioManager>()

    private var mediaPlayer: MediaPlayerStateWrapper? = null

    @MainThread
    fun play(@RawRes soundResourceId: Int) {
        if (!shouldPlaySound()) {
            return
        }
        val mediaPlayer = getOrCreateMediaPlayer()
        try {
            appContext.resources.openRawResourceFd(soundResourceId)?.use { afd ->
                mediaPlayer.setDataSource(afd)
                mediaPlayer.prepare()
                mediaPlayer.start()
            }
        } catch (e: Exception) {
            // TODO(ANDR-4545): This error should never happen, but if it does, it should be reported to Sentry
            logger.error("Failed to play play sound effect", e)
        }
    }

    private fun shouldPlaySound() = audioManager?.getRingerMode() == AudioManager.RINGER_MODE_NORMAL

    private fun getOrCreateMediaPlayer(): MediaPlayerStateWrapper {
        mediaPlayer?.let { mediaPlayer ->
            return mediaPlayer
        }
        val mediaPlayer = MediaPlayerStateWrapper()
        mediaPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        mediaPlayer.setVolume(0.1f, 0.1f)
        mediaPlayer.setCompletionListener { mediaPlayer ->
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
        }
        this.mediaPlayer = mediaPlayer
        return mediaPlayer
    }

    override fun destroy() {
        mediaPlayer?.reset()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
