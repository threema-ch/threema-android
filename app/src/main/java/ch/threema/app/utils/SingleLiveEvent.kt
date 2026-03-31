package ch.threema.app.utils

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.atomic.AtomicBoolean

private val logger = getThreemaLogger("SingleLiveEvent")

/**
 * A LiveData implementation for one-time events that are delivered to only one active observer
 * and are not re-emitted after configuration changes.
 *
 * This class solves the problem with standard LiveData where values are automatically
 * delivered to newly registered observers after configuration changes (e.g., screen rotation).
 * This is undesirable for one-time UI events like toasts, navigation, or snackbars.
 *
 */
class SingleLiveEvent<T> : MutableLiveData<T>() {
    private val pending = AtomicBoolean(false)

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        if (hasActiveObservers()) {
            logger.warn("Multiple observers registered but only one will be notified of changes")
        }
        super.observe(owner) { t ->
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t)
            }
        }
    }

    @MainThread
    override fun setValue(t: T?) {
        pending.set(true)
        super.setValue(t)
    }
}
