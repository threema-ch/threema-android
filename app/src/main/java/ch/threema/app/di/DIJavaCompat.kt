package ch.threema.app.di

import android.content.Context
import ch.threema.localcrypto.MasterKeyManager
import org.koin.mp.KoinPlatformTools

/**
 * Provides convenient access to dependencies in the singletons scope from Java.
 */
object DIJavaCompat {
    @JvmStatic
    fun getAppContext(): Context =
        getKoin().get()

    @JvmStatic
    fun getMasterKeyManager(): MasterKeyManager =
        getKoin().get()

    @JvmStatic
    fun isSessionScopeReady(): Boolean =
        getKoin().isSessionScopeReady()

    private fun getKoin() =
        KoinPlatformTools.defaultContext().get()
}
