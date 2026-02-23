package ch.threema.storage.databaseupdate

import ch.threema.base.utils.getThreemaLogger
import ch.threema.storage.runDelete
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("DatabaseUpdateToVersion111")

class DatabaseUpdateToVersion111(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        val orphanedBallotIdQuery = "SELECT ib.id FROM identity_ballot AS ib WHERE NOT EXISTS (SELECT 1 FROM contacts WHERE identity = ib.identity)"

        logger.info("Deleting orphaned ballots")
        val ballotCount = sqLiteDatabase.runDelete(
            table = "ballot",
            whereClause = "id IN ($orphanedBallotIdQuery)",
        )
        logger.info("Deleted {} orphaned ballots", ballotCount)

        logger.info("Deleting choices from orphaned ballots")
        val ballotChoiceCount = sqLiteDatabase.runDelete(
            table = "ballot_choice",
            whereClause = "ballotId IN ($orphanedBallotIdQuery)",
        )
        logger.info("Deleted {} choices from orphaned ballots", ballotChoiceCount)

        logger.info("Deleting votes from orphaned ballots")
        val ballotVoteCount = sqLiteDatabase.runDelete(
            table = "ballot_vote",
            whereClause = "ballotId IN ($orphanedBallotIdQuery)",
        )
        logger.info("Deleted {} votes from orphaned ballots", ballotVoteCount)

        logger.info("Deleting identity links to orphaned ballots")
        val identityLinkCount = sqLiteDatabase.runDelete(
            table = "identity_ballot",
            whereClause = "identity NOT IN (SELECT identity FROM contacts)",
        )
        logger.info("Deleted {} identity links to orphaned ballots", identityLinkCount)
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "remove polls from deleted contacts"

    companion object {
        const val VERSION = 111
    }
}
