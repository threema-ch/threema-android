package ch.threema.app.activities.referral

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val referralFeatureModule = module {
    viewModelOf(::ReferralViewModel)
}
