package ch.threema.app.availabilitystatus

import ch.threema.app.availabilitystatus.edit.EditAvailabilityStatusViewModel
import ch.threema.app.usecases.availabilitystatus.UpdateUserAvailabilityStatusUseCase
import ch.threema.app.usecases.availabilitystatus.WatchAllContactAvailabilityStatusesUseCase
import ch.threema.storage.factories.ContactAvailabilityStatusModelFactory
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val availabilityStatusFeatureModule = module {
    viewModelOf(::EditAvailabilityStatusViewModel)
    factoryOf(::UpdateUserAvailabilityStatusUseCase)
    factoryOf(::WatchAllContactAvailabilityStatusesUseCase)
    singleOf(::ContactAvailabilityStatusModelFactory)
}
