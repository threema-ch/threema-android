package ch.threema.app.di

import android.content.Context
import ch.threema.app.di.modules.featuresModule
import ch.threema.app.di.modules.sessionScopedModule
import ch.threema.app.di.modules.singletonsModule
import ch.threema.app.di.modules.utilsModule
import ch.threema.app.protocolsteps.protocolStepsModule
import ch.threema.app.usecases.useCasesModule
import ch.threema.common.commonModule
import ch.threema.storage.storageModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

fun initDependencyInjection(appContext: Context) {
    startKoin {
        logger(ThreemaKoinLogger)
        androidContext(appContext)

        modules(
            commonModule,
            utilsModule,
            singletonsModule,
            storageModule,
            sessionScopedModule,
            useCasesModule,
            featuresModule,
            protocolStepsModule,
        )
    }
}
