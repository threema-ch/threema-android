package ch.threema.android

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Provides helper functions for subscribing to flows from Java.
 */
object FlowJavaCompat {
    fun interface ItemHandler<T> {
        fun onItem(item: T)
    }

    @JvmStatic
    fun <T : Any> collect(lifecycleOwner: LifecycleOwner, state: Lifecycle.State, flow: Flow<T>, handler: ItemHandler<T>) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(state) {
                collectItems(flow, handler)
            }
        }
    }

    private suspend fun <T : Any> collectItems(flow: Flow<T>, handler: ItemHandler<T>) {
        flow.collect { item ->
            handler.onItem(item)
        }
    }
}
