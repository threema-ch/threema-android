package ch.threema.app.onprem

import ch.threema.app.files.AppDirectoryProvider
import ch.threema.domain.onprem.OnPremConfigParser
import ch.threema.domain.onprem.OnPremConfigStore
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val onPremFeatureModule = module {
    factory<OnPremConfigStore> {
        OnPremConfigStore(
            baseDirectory = get<AppDirectoryProvider>().appDataDirectory,
            timeProvider = get(),
            onPremConfigParser = get(),
        )
    }

    factoryOf(::OnPremConfigParser)
}
