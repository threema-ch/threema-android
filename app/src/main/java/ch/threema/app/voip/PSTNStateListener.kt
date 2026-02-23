@file:Suppress("DEPRECATION")

package ch.threema.app.voip

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat.getSystemService
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("PSTNStateListener")

class PSTNStateListener(context: Context) {
    private val telephonyManager: TelephonyManager? =
        getSystemService(context, TelephonyManager::class.java)
    private var telephonyCallback: TelephonyCallback? = null

    private var callState = TelephonyManager.CALL_STATE_IDLE

    private var lastDeclinedCall: Long = -1

    init {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    telephonyCallback =
                        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                            override fun onCallStateChanged(state: Int) {
                                callStateChanged(state)
                            }
                        }.also {
                            telephonyManager?.registerTelephonyCallback(context.mainExecutor, it)
                        }
                } else {
                    telephonyManager?.listen(
                        object : PhoneStateListener() {
                            @Deprecated("Deprecated in Java")
                            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                                super.onCallStateChanged(state, phoneNumber)
                                callStateChanged(state)
                            }
                        },
                        PhoneStateListener.LISTEN_CALL_STATE,
                    )
                }
            } catch (exception: SecurityException) {
                logger.error("Could not register call state listener", exception)
            }
        }
    }

    private fun callStateChanged(state: Int) {
        logger.info("onCallStateChanged: {}", state)
        if (callState == TelephonyManager.CALL_STATE_RINGING && state == TelephonyManager.CALL_STATE_IDLE) {
            // The time a call has been declined
            lastDeclinedCall = System.currentTimeMillis()
            logger.info("lastDeclinedCall: {}", lastDeclinedCall)
        }
        callState = state
    }

    fun isIdle() = callState == TelephonyManager.CALL_STATE_IDLE

    fun isRinging() = callState == TelephonyManager.CALL_STATE_RINGING

    fun lastDeclinedCall() = lastDeclinedCall

    fun destroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let {
                telephonyManager?.unregisterTelephonyCallback(it)
            }
        }
    }
}
