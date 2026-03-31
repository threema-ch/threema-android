package ch.threema.app.storagemanagement

import ch.threema.app.storagemanagement.usecases.GetStorageSizeUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val storageManagementFeatureModule = module {
    factoryOf(::GetStorageSizeUseCase)
}
