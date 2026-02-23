package ch.threema.app.emojis.search

import androidx.room.TypeConverter

class DiversityConverters {
    private companion object {
        const val DIVERSITY_SEPARATOR = ";"
    }

    @TypeConverter
    fun fromString(value: String?): List<String>? {
        return value?.split(DIVERSITY_SEPARATOR)
    }

    @TypeConverter
    fun toString(value: List<String>?): String? {
        return value?.joinToString(DIVERSITY_SEPARATOR)
    }
}
