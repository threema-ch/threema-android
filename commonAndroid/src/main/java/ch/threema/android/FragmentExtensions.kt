package ch.threema.android

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

inline fun FragmentManager.runTransaction(allowStateLoss: Boolean = false, block: FragmentTransaction.() -> Unit) {
    beginTransaction()
        .apply(block)
        .run {
            if (allowStateLoss) {
                commitAllowingStateLoss()
            } else {
                commit()
            }
        }
}
