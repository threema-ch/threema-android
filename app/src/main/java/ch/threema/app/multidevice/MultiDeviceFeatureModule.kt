package ch.threema.app.multidevice

import ch.threema.app.multidevice.wizard.LinkNewDeviceWizardViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val multiDeviceFeatureModule = module {
    viewModelOf(::LinkedDevicesViewModel)
    viewModelOf(::LinkNewDeviceWizardViewModel)
}
