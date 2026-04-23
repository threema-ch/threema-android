package ch.threema.storage

import android.content.Context
import ch.threema.storage.databaseupdate.*
import kotlin.collections.asReversed
import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdater(
    private val appContext: Context,
    private val database: SQLiteDatabase,
) {
    fun getUpdates(oldVersion: Int): List<DatabaseUpdate> {
        if (oldVersion == VERSION) {
            return emptyList()
        }
        return updates
            .asReversed()
            .asSequence()
            .map { updateConstructor ->
                updateConstructor.invoke(database, appContext)
            }
            .takeWhile { update ->
                oldVersion < update.version
            }
            .toList()
            .reversed()
    }

    companion object {
        const val VERSION = 119

        private val updates: List<(SQLiteDatabase, Context) -> DatabaseUpdate>
            get() = listOf<(SQLiteDatabase, Context) -> DatabaseUpdate>(
                update(::DatabaseUpdateToVersion4),
                update(::DatabaseUpdateToVersion6),
                update(::DatabaseUpdateToVersion7),
                update(::DatabaseUpdateToVersion8),
                update(::DatabaseUpdateToVersion9),
                update(::DatabaseUpdateToVersion10),
                update(::DatabaseUpdateToVersion11),
                update(::DatabaseUpdateToVersion12),
                update(::DatabaseUpdateToVersion13),
                update(::DatabaseUpdateToVersion15),
                update(::DatabaseUpdateToVersion16),
                update(::DatabaseUpdateToVersion17),
                update(::DatabaseUpdateToVersion19),
                update(::DatabaseUpdateToVersion20),
                update(::DatabaseUpdateToVersion21),
                update(::DatabaseUpdateToVersion24),
                update(::DatabaseUpdateToVersion25),
                update(::DatabaseUpdateToVersion27),
                update(::DatabaseUpdateToVersion28),
                update(::DatabaseUpdateToVersion32),
                update(::DatabaseUpdateToVersion33),
                update(::DatabaseUpdateToVersion34),
                update(::DatabaseUpdateToVersion35),
                update(::DatabaseUpdateToVersion36),
                update(::DatabaseUpdateToVersion37),
                update(::DatabaseUpdateToVersion38),
                update(::DatabaseUpdateToVersion40),
                update(::DatabaseUpdateToVersion41),
                update(::DatabaseUpdateToVersion44),
                update(::DatabaseUpdateToVersion45),
                update(::DatabaseUpdateToVersion47),
                update(::DatabaseUpdateToVersion49),
                update(::DatabaseUpdateToVersion50),
                update(::DatabaseUpdateToVersion51),
                update(::DatabaseUpdateToVersion52),
                update(::DatabaseUpdateToVersion56),
                update(::DatabaseUpdateToVersion58),
                update(::DatabaseUpdateToVersion59),
                update(::DatabaseUpdateToVersion60),
                update(::DatabaseUpdateToVersion61),
                update(::DatabaseUpdateToVersion62),
                update(::DatabaseUpdateToVersion65),
                update(::DatabaseUpdateToVersion67),
                update(::DatabaseUpdateToVersion68),
                update(::DatabaseUpdateToVersion69),
                update(::DatabaseUpdateToVersion70),
                update(::DatabaseUpdateToVersion71),
                update(::DatabaseUpdateToVersion72),
                update(::DatabaseUpdateToVersion73),
                update(::DatabaseUpdateToVersion74),
                update(::DatabaseUpdateToVersion75),
                update(::DatabaseUpdateToVersion76),
                update(::DatabaseUpdateToVersion77),
                update(::DatabaseUpdateToVersion78),
                update(::DatabaseUpdateToVersion79),
                update(::DatabaseUpdateToVersion80),
                update(::DatabaseUpdateToVersion81),
                update(::DatabaseUpdateToVersion82),
                update(::DatabaseUpdateToVersion83),
                update(::DatabaseUpdateToVersion84),
                update(::DatabaseUpdateToVersion85),
                update(::DatabaseUpdateToVersion86),
                update(::DatabaseUpdateToVersion87),
                update(::DatabaseUpdateToVersion88),
                update(::DatabaseUpdateToVersion89),
                update(::DatabaseUpdateToVersion90),
                update(::DatabaseUpdateToVersion92),
                update(::DatabaseUpdateToVersion93),
                update(::DatabaseUpdateToVersion94),
                update(::DatabaseUpdateToVersion95),
                update(::DatabaseUpdateToVersion96),
                update(::DatabaseUpdateToVersion97),
                update(::DatabaseUpdateToVersion98),
                update(::DatabaseUpdateToVersion99),
                update(::DatabaseUpdateToVersion100),
                update(::DatabaseUpdateToVersion101),
                update(::DatabaseUpdateToVersion102),
                update(::DatabaseUpdateToVersion103),
                update(::DatabaseUpdateToVersion104),
                update(::DatabaseUpdateToVersion105),
                update(::DatabaseUpdateToVersion106),
                update(::DatabaseUpdateToVersion107),
                update(::DatabaseUpdateToVersion108),
                update(::DatabaseUpdateToVersion109),
                update(::DatabaseUpdateToVersion110),
                update(::DatabaseUpdateToVersion111),
                update(::DatabaseUpdateToVersion112),
                update(::DatabaseUpdateToVersion113),
                update(::DatabaseUpdateToVersion114),
                update(::DatabaseUpdateToVersion115),
                update(::DatabaseUpdateToVersion116),
                update(::DatabaseUpdateToVersion117),
                update(::DatabaseUpdateToVersion118),
                update(::DatabaseUpdateToVersion119),
            )

        private fun update(databaseUpdate: () -> DatabaseUpdate): (SQLiteDatabase, Context) -> DatabaseUpdate =
            { _, _ -> databaseUpdate() }

        private fun update(databaseUpdate: (SQLiteDatabase) -> DatabaseUpdate): (SQLiteDatabase, Context) -> DatabaseUpdate =
            { database, _ -> databaseUpdate(database) }

        private fun update(databaseUpdate: (SQLiteDatabase, Context) -> DatabaseUpdate) = databaseUpdate
    }
}
