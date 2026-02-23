package ch.threema.app.emojis.search

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SearchTermsLanguageVersion(
    @PrimaryKey val language: String,
    val version: Int,
)
