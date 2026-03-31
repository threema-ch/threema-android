package ch.threema.app.voip.util

import androidx.annotation.RawRes
import ch.threema.app.R

enum class VoipSound(@JvmField @RawRes val resourceId: Int) {
    INITIALIZING(resourceId = R.raw.call_initialization),
    BUSY(resourceId = R.raw.busy_tone),
    RINGING(resourceId = R.raw.ringing_tone),
    PICKUP(resourceId = R.raw.threema_pickup),
    HANGUP(resourceId = R.raw.threema_hangup),
    PROBLEM(resourceId = R.raw.threema_problem),
}
