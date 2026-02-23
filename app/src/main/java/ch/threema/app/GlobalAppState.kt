package ch.threema.app

import java.util.Date

object GlobalAppState {
    @JvmStatic
    var lastLoggedIn: Date? = null

    @JvmStatic
    var isDeviceIdle: Boolean = false

    @JvmStatic
    var isAppResumed: Boolean = false
}
