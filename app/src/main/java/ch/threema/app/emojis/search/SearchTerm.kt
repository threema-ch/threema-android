package ch.threema.app.emojis.search

import androidx.room.*

@Entity(
    primaryKeys = ["emoji_sequence", "language", "term"],
    foreignKeys = [
        ForeignKey(
            entity = Emoji::class,
            parentColumns = ["sequence"],
            childColumns = ["emoji_sequence"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SearchTerm(
    @ColumnInfo(name = "emoji_sequence") val emojiSequence: String,
    val language: String,
    val term: String,
)
