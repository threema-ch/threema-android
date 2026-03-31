package ch.threema.app.di.modules

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.work.WorkManager
import ch.threema.app.utils.AndroidContactUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DeviceIdProvider
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.DoNotDisturbUtil
import ch.threema.app.utils.HibernationUtil
import ch.threema.app.utils.PrivateDoNotDisturbUtil
import ch.threema.app.utils.SoundEffectPlayer
import ch.threema.app.utils.WorkDoNotDisturbUtil
import ch.threema.app.utils.executor.CoroutineBackgroundExecutor
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
    factory<SharedPreferences> { PreferenceManager.getDefaultSharedPreferences(get()) }
    factoryOf(::HibernationUtil)
    factoryOf(::CoroutineBackgroundExecutor)
    factory<WorkManager> { WorkManager.getInstance(get()) }
    factoryOf(::SoundEffectPlayer)

    if (ConfigUtils.isWorkBuild()) {
        factoryOf(::WorkDoNotDisturbUtil)
    } else {
        factoryOf(::PrivateDoNotDisturbUtil)
    } bind DoNotDisturbUtil::class
}
