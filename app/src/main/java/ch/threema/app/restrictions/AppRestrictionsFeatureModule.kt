package ch.threema.app.restrictions

import ch.threema.app.utils.ConfigUtils
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appRestrictionsFeatureModule = module {
    singleOf(::AppRestrictions)
    if (ConfigUtils.isWorkBuild()) {
        single<AppRestrictionProvider> {
            WorkAppRestrictionProvider(
                getRestrictions = {
                    AppRestrictionService.getInstance()
                        .appRestrictions
                        ?.takeUnless { it.isEmpty }
                },
            )
        }
    } else {
        single<AppRestrictionProvider> {
            NullAppRestrictionProvider()
        }
    }
}
