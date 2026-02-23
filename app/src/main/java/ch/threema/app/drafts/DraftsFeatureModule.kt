package ch.threema.app.drafts

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val draftsFeatureModule = module {
    singleOf(::DraftManagerImpl) bind DraftManager::class
}
