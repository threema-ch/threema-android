package ch.threema.android

import android.os.Bundle

inline fun buildBundle(block: Bundle.() -> Unit): Bundle =
    Bundle().apply(block)
