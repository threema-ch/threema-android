package ch.threema.app.startup

import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.services.RemoteSecretMonitorService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.RuntimeUtil
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val startupFeatureModule = module {
    viewModelOf(::RemoteSecretProtectionUpdateViewModel)
    factoryOf(::MasterKeyEventMonitor)

    factory<AppProcessLifecycleObserver> {
        AppProcessLifecycleObserver(
            dispatcherProvider = get(),
            reloadAppRestrictions = {
                if (ConfigUtils.isWorkBuild()) {
                    RuntimeUtil.runOnWorkerThread {
                        AppRestrictionService.getInstance().reload()
                    }
                }
            },
        )
    }

    factoryOf(RemoteSecretMonitorService::Scheduler)

    factory<RemoteSecretProtectionStateMonitor> {
        if (ConfigUtils.isOnPremBuild()) {
            RemoteSecretProtectionStateMonitorImpl(
                remoteSecretMonitorServiceScheduler = get(),
                masterKeyManager = get(),
            )
        } else {
            NoOpRemoteSecretProtectionStateMonitorImpl()
        }
    }

    singleOf(::RemoteSecretMonitorRetryController)
}
