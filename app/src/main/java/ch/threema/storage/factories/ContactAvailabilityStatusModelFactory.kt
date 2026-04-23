package ch.threema.storage.factories

import ch.threema.storage.DatabaseCreationProvider
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.DbAvailabilityStatus
import ch.threema.storage.models.ContactModel

class ContactAvailabilityStatusModelFactory(
    databaseProvider: DatabaseProvider,
) : ModelFactory(
    databaseProvider = databaseProvider,
    tableName = DbAvailabilityStatus.TABLE,
) {

    object Creator : DatabaseCreationProvider {
        override fun getCreationStatements() = arrayOf(
            """
                CREATE TABLE `${DbAvailabilityStatus.TABLE}` (
                    `${DbAvailabilityStatus.COLUMN_IDENTITY}` VARCHAR NOT NULL PRIMARY KEY,
                    `${DbAvailabilityStatus.COLUMN_CATEGORY}` INTEGER NOT NULL,
                    `${DbAvailabilityStatus.COLUMN_DESCRIPTION}` TEXT NOT NULL,
                    CONSTRAINT `fk_contacts_identity`
                        FOREIGN KEY(`${DbAvailabilityStatus.COLUMN_IDENTITY}`)
                        REFERENCES `${ContactModel.TABLE}` (`${ContactModel.COLUMN_IDENTITY}`)
                        ON UPDATE CASCADE
                        ON DELETE CASCADE
                )
            """.trimIndent(),
        )
    }
}
