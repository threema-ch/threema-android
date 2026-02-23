package ch.threema.app.emojis.search

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Emoji::class, SearchTerm::class, SearchTermsLanguageVersion::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(DiversityConverters::class)
abstract class EmojiSearchIndexDatabase : RoomDatabase() {
    abstract fun emojiDao(): EmojiDao
}
