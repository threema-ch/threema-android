package ch.threema.storage

import android.content.Context
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.storage.factories.AppTaskPersistenceFactory
import ch.threema.storage.factories.BallotChoiceModelFactory
import ch.threema.storage.factories.BallotModelFactory
import ch.threema.storage.factories.BallotVoteModelFactory
import ch.threema.storage.factories.ContactEditHistoryEntryModelFactory
import ch.threema.storage.factories.ContactEmojiReactionModelFactory
import ch.threema.storage.factories.ContactModelFactory
import ch.threema.storage.factories.ConversationTagFactory
import ch.threema.storage.factories.DistributionListMemberModelFactory
import ch.threema.storage.factories.DistributionListMessageModelFactory
import ch.threema.storage.factories.DistributionListModelFactory
import ch.threema.storage.factories.GroupBallotModelFactory
import ch.threema.storage.factories.GroupCallModelFactory
import ch.threema.storage.factories.GroupEditHistoryEntryModelFactory
import ch.threema.storage.factories.GroupEmojiReactionModelFactory
import ch.threema.storage.factories.GroupMemberModelFactory
import ch.threema.storage.factories.GroupMessageModelFactory
import ch.threema.storage.factories.GroupModelFactory
import ch.threema.storage.factories.IdentityBallotModelFactory
import ch.threema.storage.factories.IncomingGroupSyncRequestLogModelFactory
import ch.threema.storage.factories.MessageModelFactory
import ch.threema.storage.factories.OutgoingGroupSyncRequestLogModelFactory
import ch.threema.storage.factories.RejectedGroupMessageFactory
import ch.threema.storage.factories.ServerMessageModelFactory
import ch.threema.storage.factories.TaskArchiveFactory
import ch.threema.storage.factories.WebClientSessionModelFactory
import kotlin.system.exitProcess
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val storageModule = module {
    single {
        DatabaseProviderImpl(
            databaseOpenHelperFactory = { masterKey ->
                val appContext = get<Context>()
                DatabaseOpenHelper(
                    appContext = appContext,
                    password = masterKey.deriveDatabasePassword(),
                    onDatabaseCorrupted = {
                        appContext.showToast("Database corrupted. Please restart your device and try again.", ToastDuration.LONG)
                        exitProcess(2)
                    },
                )
            },
        )
    } bind DatabaseProvider::class

    singleOf(::AppTaskPersistenceFactory)
    singleOf(::ContactEditHistoryEntryModelFactory)
    singleOf(::ConversationTagFactory)
    singleOf(::GroupEditHistoryEntryModelFactory)
    singleOf(::ContactEmojiReactionModelFactory)
    singleOf(::GroupEmojiReactionModelFactory)
    singleOf(::ServerMessageModelFactory)
    singleOf(::TaskArchiveFactory)
    singleOf(::WebClientSessionModelFactory)

    singleOf(::DatabaseService)
    modelFactory<ContactModelFactory> { contactModelFactory }
    modelFactory<MessageModelFactory> { messageModelFactory }
    modelFactory<GroupModelFactory> { groupModelFactory }
    modelFactory<GroupMemberModelFactory> { groupMemberModelFactory }
    modelFactory<GroupMessageModelFactory> { groupMessageModelFactory }
    modelFactory<DistributionListModelFactory> { distributionListModelFactory }
    modelFactory<DistributionListMemberModelFactory> { distributionListMemberModelFactory }
    modelFactory<DistributionListMessageModelFactory> { distributionListMessageModelFactory }
    modelFactory<OutgoingGroupSyncRequestLogModelFactory> { outgoingGroupSyncRequestLogModelFactory }
    modelFactory<IncomingGroupSyncRequestLogModelFactory> { incomingGroupSyncRequestLogModelFactory }
    modelFactory<BallotModelFactory> { ballotModelFactory }
    modelFactory<BallotChoiceModelFactory> { ballotChoiceModelFactory }
    modelFactory<BallotVoteModelFactory> { ballotVoteModelFactory }
    modelFactory<IdentityBallotModelFactory> { identityBallotModelFactory }
    modelFactory<GroupBallotModelFactory> { groupBallotModelFactory }
    modelFactory<GroupCallModelFactory> { groupCallModelFactory }
    modelFactory<RejectedGroupMessageFactory> { rejectedGroupMessageFactory }
}

@Deprecated("Model factories are singletons and should be managed directly by Koin, not by DatabaseService")
private inline fun <reified T : Any> Module.modelFactory(crossinline getModelFactory: DatabaseService.() -> T) {
    factory<T> { get<DatabaseService>().getModelFactory() }
}
