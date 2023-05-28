/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.emojis.search

import androidx.room.*

@Dao
interface EmojiDao {
	@Transaction
	@Insert(
		entity = Emoji::class,
		onConflict = OnConflictStrategy.IGNORE
	)
	fun insertEmojis(emojis: List<EmojiOrder>)

	@Transaction
	@Update(entity = Emoji::class)
	fun updateEmojiDiversities(diversities: List<EmojiDiversities>)

	@Transaction
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	fun insertSearchTerms(terms: List<SearchTerm>)

	@Query("SELECT version FROM SearchTermsLanguageVersion WHERE language LIKE :language")
	fun getLanguageVersion(language: String): Int?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun insertLanguageVersion(languageVersion: SearchTermsLanguageVersion)

	@Query(
		"SELECT DISTINCT e.* FROM Emoji e " +
			"INNER JOIN SearchTerm s ON e.sequence = s.emoji_sequence " +
			"WHERE s.language LIKE :language " +
				"AND s.term LIKE :searchTerm || '%' " +
			"ORDER by `order` ASC")
	fun search(language: String, searchTerm: String): List<Emoji>

	@Query("DELETE FROM Emoji")
	fun deleteEmojis()

	@Query("DELETE FROM SearchTermsLanguageVersion")
	fun deleteSearchTermLanguageVersions()

	@Query("DELETE FROM SearchTerm WHERE language LIKE :language")
	fun deleteSearchTermsForLanguage(language: String)
}
