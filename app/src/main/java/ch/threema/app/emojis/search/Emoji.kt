package ch.threema.app.emojis.search

import androidx.room.*

@Entity
data class Emoji(
    @PrimaryKey val sequence: String,
    val order: Long,
    @TypeConverters(DiversityConverters::class)
    val diversities: List<String>?,
)

data class EmojiOrder(
    val sequence: String,
    val order: Long = 9999,
)

data class EmojiDiversities(
    val sequence: String,
    val diversities: List<String>,
)
