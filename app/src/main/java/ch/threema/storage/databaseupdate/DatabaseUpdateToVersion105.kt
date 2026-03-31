package ch.threema.storage.databaseupdate

internal class DatabaseUpdateToVersion105 : DatabaseUpdate {

    override fun run() {
        // In a previous version of this migration there was also an update of the message uid
        // indices.
        // - `messageUidIdx` was dropped and a unique index `message_uid_idx` on
        //   `message.uid` was created
        // - `groupMessageUidIdx` was dropped and a unique index `m_group_message_uid_idx` on
        //   `m_group_message.uid` was created
        //
        // This yielded problems in the db migration on some devices where the `uid` was not unique
        // and an app crash was the result. Therefore this index change was removed from this
        // system update.
        // For devices where the migration succeeded without problems, the index uniqueness will be
        // fixed in the DatabaseUpdateToVersion106
        //
        // Additionally the original database scheme for reactions does not work if the foreign key
        // references a non-unique field (which is the case if the index is not unique anymore).
        // Therefore the original creation of the tables was also removed from this migration.
        // The _correct_ scheme will be created in migration to version 106 where also data migration
        // will be taken care of if the migration to 105 was already executed.
        //
        // With indices and table creations removed this migration is now empty, but kept to ensure
        // consistency in database versioning on devices where the migration was already successfully
        // executed.
    }

    override fun getDescription() = "empty reaction migration"

    override val version = 105
}
