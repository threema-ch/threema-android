package ch.threema.android

import android.os.Looper

fun isMainThread(): Boolean =
    Looper.myLooper() == Looper.getMainLooper()
