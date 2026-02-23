package ch.threema.android

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import ch.threema.annotation.SameThread

/**
 * A [Destroyer] can be used to collect all the code that needs to run when
 * the host component (e.g. an activity or fragment) is destroyed.
 */
@SameThread
class Destroyer private constructor() : DefaultLifecycleObserver {
    private val destroyables = mutableListOf<Destroyable>()

    fun <T> register(create: () -> T, destroy: Destroyable): T {
        val item = create()
        own(destroy)
        return item
    }

    /**
     * Registers a [Destroyable] callback, to be run when the Destroyer itself is destroyed.
     */
    fun own(destroyable: Destroyable) {
        destroyables.add(destroyable)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        destroyables.forEach { destroyable ->
            destroyable.destroy()
        }
        destroyables.clear()
    }

    companion object {
        @JvmStatic
        fun LifecycleOwner.createDestroyer(): Destroyer =
            lifecycle.createDestroyer()

        @JvmStatic
        fun Lifecycle.createDestroyer(): Destroyer {
            val destroyer = Destroyer()
            addObserver(destroyer)
            return destroyer
        }
    }
}

fun interface Destroyable {
    fun destroy()
}

fun <T : Destroyable> T.ownedBy(destroyer: Destroyer) = also {
    destroyer.own(this)
}
