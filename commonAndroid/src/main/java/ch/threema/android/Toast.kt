package ch.threema.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import java.lang.ref.WeakReference

@AnyThread
@JvmOverloads
fun Context.showToast(@StringRes message: Int, duration: ToastDuration = ToastDuration.SHORT) {
    showToastOnMainThread(this, getString(message), duration)
}

@AnyThread
@JvmOverloads
fun Context.showToast(message: String, duration: ToastDuration = ToastDuration.SHORT) {
    showToastOnMainThread(this, message, duration)
}

@AnyThread
@JvmOverloads
fun Fragment.showToast(@StringRes message: Int, duration: ToastDuration = ToastDuration.SHORT) {
    context?.showToast(message, duration)
}

@AnyThread
@JvmOverloads
fun Fragment.showToast(message: String, duration: ToastDuration = ToastDuration.SHORT) {
    context?.showToast(message, duration)
}

enum class ToastDuration(val toastLength: Int) {
    SHORT(Toast.LENGTH_SHORT),
    LONG(Toast.LENGTH_LONG),
}

private val toastHandler = Handler(Looper.getMainLooper())

@AnyThread
private fun showToastOnMainThread(context: Context, message: String, duration: ToastDuration = ToastDuration.SHORT) {
    if (isMainThread()) {
        createAndShowToast(context, message, duration)
    } else {
        val contextReference = WeakReference(context)
        toastHandler.post {
            val context = contextReference.get() ?: return@post
            createAndShowToast(context, message, duration)
        }
    }
}

private fun createAndShowToast(context: Context, message: String, duration: ToastDuration) {
    Toast.makeText(context, message, duration.toastLength).show()
}
