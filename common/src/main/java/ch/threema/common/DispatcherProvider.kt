package ch.threema.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface DispatcherProvider {
    val worker: CoroutineDispatcher
    val io: CoroutineDispatcher

    companion object {
        val default = object : DispatcherProvider {
            override val worker = Dispatchers.Default
            override val io = Dispatchers.IO
        }
    }
}
