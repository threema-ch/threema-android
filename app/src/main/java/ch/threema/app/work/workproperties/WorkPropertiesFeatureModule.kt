package ch.threema.app.work.workproperties

import ch.threema.domain.libthreema.LibthreemaHttpClient
import org.koin.dsl.module

val workPropertiesFeatureModule = module {
    factory {
        WorkPropertiesClient(
            clientInfo = get(),
            httpClient = LibthreemaHttpClient(
                okHttpClient = get(),
            ),
            serverAddressProvider = get(),
            userService = get(),
            licenseService = get(),
        )
    }
}
