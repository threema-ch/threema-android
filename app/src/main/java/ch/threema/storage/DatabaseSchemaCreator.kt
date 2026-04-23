package ch.threema.storage

import ch.threema.storage.factories.AppTaskPersistenceFactory
import ch.threema.storage.factories.BallotChoiceModelFactory
import ch.threema.storage.factories.BallotModelFactory
import ch.threema.storage.factories.BallotVoteModelFactory
import ch.threema.storage.factories.ContactAvailabilityStatusModelFactory
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

object DatabaseSchemaCreator {
    /**
     * @return All SQL statements needed to bootstrap the database
     */
    fun getCreationStatements(): Sequence<String> =
        getDatabaseCreationProviders().asSequence().flatMap { creationProvider ->
            creationProvider.getCreationStatements().asSequence()
        }

    @Suppress("RemoveExplicitTypeArguments")
    private fun getDatabaseCreationProviders() =
        arrayOf<DatabaseCreationProvider>(
            ContactModelFactory.Creator(),
            MessageModelFactory.Creator(),
            GroupModelFactory.Creator(),
            GroupMemberModelFactory.Creator(),
            GroupMessageModelFactory.Creator(),
            DistributionListModelFactory.Creator(),
            DistributionListMemberModelFactory.Creator(),
            DistributionListMessageModelFactory.Creator(),
            OutgoingGroupSyncRequestLogModelFactory.Creator(),
            IncomingGroupSyncRequestLogModelFactory.Creator,
            BallotModelFactory.Creator(),
            BallotChoiceModelFactory.Creator(),
            BallotVoteModelFactory.Creator(),
            IdentityBallotModelFactory.Creator(),
            GroupBallotModelFactory.Creator(),
            WebClientSessionModelFactory.Creator(),
            ConversationTagFactory.Creator(),
            GroupCallModelFactory.Creator(),
            ServerMessageModelFactory.Creator,
            TaskArchiveFactory.Creator,
            AppTaskPersistenceFactory.Creator,
            RejectedGroupMessageFactory.Creator,
            ContactEditHistoryEntryModelFactory.Creator,
            GroupEditHistoryEntryModelFactory.Creator,
            ContactEmojiReactionModelFactory.Creator,
            GroupEmojiReactionModelFactory.Creator,
            ContactAvailabilityStatusModelFactory.Creator,
        )
}
