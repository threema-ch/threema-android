package ch.threema.app.home

import ch.threema.app.home.usecases.CheckBackupsFeatureEnabledUseCase
import ch.threema.app.home.usecases.CheckServerMessagesUseCase
import ch.threema.app.home.usecases.GetStarredMessagesCountUseCase
import ch.threema.app.home.usecases.GetUnreadConversationCountUseCase
import ch.threema.app.home.usecases.SetUpThreemaChannelUseCase
import ch.threema.app.home.usecases.ShouldShowWorkIntroScreenUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeFeatureModule = module {
    viewModelOf(::HomeViewModel)
    factoryOf(::CheckBackupsFeatureEnabledUseCase)
    factoryOf(::ErrorReportingDialog)
    factoryOf(::GetUnreadConversationCountUseCase)
    factoryOf(::GetStarredMessagesCountUseCase)
    factoryOf(::CheckServerMessagesUseCase)
    factoryOf(::SetUpThreemaChannelUseCase)
    factoryOf(::ShouldShowWorkIntroScreenUseCase)
}
