package ch.threema.app.utils

import ch.threema.common.DispatcherProvider as CommonDispatchProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface DispatcherProvider : CommonDispatchProvider {
    val main: CoroutineDispatcher

    companion object {
        val default: DispatcherProvider = object : DispatcherProvider, CommonDispatchProvider by CommonDispatchProvider.default {
            override val main = Dispatchers.Main
        }
    }
}
