package ch.threema.app.di.modules

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import ch.threema.android.Toaster
import ch.threema.app.utils.AndroidContactUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DeviceIdProvider
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.DoNotDisturbUtil
import ch.threema.app.utils.PrivateDoNotDisturbUtil
import ch.threema.app.utils.WorkDoNotDisturbUtil
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Provides access to utility classes.
 * Note that some of these may be functionally singletons, but that should be treated as an implementation detail only, i.e., they should
 * not hold global state.
 */
val utilsModule = module {
    factory<AndroidContactUtil> { AndroidContactUtil.getInstance() }
    factoryOf(::DeviceIdProvider)
    factory<DispatcherProvider> { DispatcherProvider.default }
    factoryOf(::Toaster)
    factory<SharedPreferences> { PreferenceManager.getDefaultSharedPreferences(get()) }

    if (ConfigUtils.isWorkBuild()) {
        factoryOf(::WorkDoNotDisturbUtil)
    } else {
        factoryOf(::PrivateDoNotDisturbUtil)
    } bind DoNotDisturbUtil::class
}
