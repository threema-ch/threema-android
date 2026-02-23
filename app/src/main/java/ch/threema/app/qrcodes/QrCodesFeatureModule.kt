package ch.threema.app.qrcodes

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val qrCodesFeatureModule = module {
    factoryOf(::ContactUrlUtil)
    factoryOf(::QrCodeGenerator)
}
