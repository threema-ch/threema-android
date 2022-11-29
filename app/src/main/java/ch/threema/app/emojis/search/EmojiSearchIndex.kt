/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.room.Room
import au.com.bytecode.opencsv.CSVReader
import ch.threema.app.services.PreferenceService
import ch.threema.base.utils.LoggingUtil
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

private val logger = LoggingUtil.getThreemaLogger("EmojiSearchIndex")

class EmojiSearchIndex(
	private val context: Context,
	private val preferenceService: PreferenceService
) {
	private val db by lazy { buildDatabase() }
	private val languageSupport = mutableMapOf<String, Boolean>()
	private val languageVersion = mutableMapOf<String, Int>()
	private var searchIndexVersion = preferenceService.emojiSearchIndexVersion

	private companion object {
		const val SEARCH_INDEX_VERSION = 6
		const val INDEX_FILE_EXTENSION = ".csv"
		const val EMOJI_ORDERS_FILE = "orders.csv"
		const val EMOJI_DIVERSITIES_FILE = "diversities.csv"
		const val DATABASE_NAME = "emoji-search-index.db"
		const val CSV_SEPARATOR = '|'
		val ASSETS_PATH = joinPath("emojis", "search-index")

		private fun joinPath(vararg parts: String): String {
			return parts.joinToString(File.separator)
		}
	}

	fun supportsLanguage(language: String): Boolean {
		if (!languageSupport.containsKey(language)) {
			logger.debug("Evaluate emoji search support for language '{}'", language)
			val searchTermsFileName = getSearchTermsFileName(language)
			val indexFiles = context.assets.list(ASSETS_PATH) ?: arrayOf()
			languageSupport[language] = searchTermsFileName in indexFiles
		}
		return languageSupport[language] == true
	}

	@WorkerThread
	fun search(language: String, searchTerm: String): List<Emoji> {
		prepareSearchIndex(language)
		return db.emojiDao().search(language, searchTerm)
	}

	private fun getSearchTermsFileName(language: String): String {
		return "${language}${INDEX_FILE_EXTENSION}"
	}

	private fun getSearchTermsFilePath(language: String): String {
		return joinPath(ASSETS_PATH, getSearchTermsFileName(language))
	}

	@WorkerThread
	fun prepareSearchIndex(language: String) {
		synchronized(db) {
			val dao = db.emojiDao()
			if (searchIndexVersion != SEARCH_INDEX_VERSION) {
				logger.info("Prepare emoji search index for version {}", SEARCH_INDEX_VERSION)
				resetDatabase(dao)
				val emojis = readEmojisOrdersFromAssets()
				dao.insertEmojis(emojis)
				dao.updateEmojiDiversities(readEmojiDiversitiesFromAssets())
				preferenceService.emojiSearchIndexVersion = SEARCH_INDEX_VERSION
				searchIndexVersion = SEARCH_INDEX_VERSION
			}
			prepareSearchTerms(language, dao)
		}
	}

	private fun prepareSearchTerms(language: String, dao: EmojiDao) {
		if (getLanguageVersion(language, dao) != SEARCH_INDEX_VERSION) {
			logger.info("Prepare emoji search terms for language '{}' and version {}", language, SEARCH_INDEX_VERSION)
			dao.deleteSearchTermsForLanguage(language)
			val terms = readSearchTermsFromAssets(language)
			dao.insertSearchTerms(terms)
			dao.insertLanguageVersion(SearchTermsLanguageVersion(language, SEARCH_INDEX_VERSION))
			languageVersion[language] = SEARCH_INDEX_VERSION
		}
	}

	private fun getLanguageVersion(language: String, dao: EmojiDao): Int? {
		if (!languageVersion.containsKey(language)) {
			dao.getLanguageVersion(language)?.let {
				languageVersion[language] = it
			}
		}
		return languageVersion[language]
	}

	private fun resetDatabase(dao: EmojiDao) {
		dao.deleteSearchTermLanguageVersions()
		dao.deleteEmojis()
	}

	private fun readEmojisOrdersFromAssets(): List<EmojiOrder> {
		return readCsvRows(joinPath(ASSETS_PATH, EMOJI_ORDERS_FILE))
			.filter { it.size > 1 }
			.map { EmojiOrder(it[0], it[1].toLong()) }
	}

	private fun readEmojiDiversitiesFromAssets(): List<EmojiDiversities> {
		return readCsvRows(joinPath(ASSETS_PATH, EMOJI_DIVERSITIES_FILE))
			.filter { it.size > 1 }
			.map { EmojiDiversities(it[0], it.subList(1, it.size)) }
	}

	private fun readSearchTermsFromAssets(language: String): List<SearchTerm> {
		return readCsvRows(getSearchTermsFilePath(language))
			.filter { it.size > 1 }
			.flatMap { it.subList(1, it.size).map { term -> SearchTerm(it[0], language, term) } }
	}

	private fun buildDatabase(): EmojiSearchIndexDatabase {
		return Room.databaseBuilder(context, EmojiSearchIndexDatabase::class.java, DATABASE_NAME)
			.build()
	}

	private fun readCsvRows(file: String): List<List<String>> {
		return try {
			val reader = InputStreamReader(context.assets.open(file))
			CSVReader(reader, CSV_SEPARATOR).readAll().map { it.asList() }
		} catch (e: IOException) {
			logger.warn("Could not read search terms", e)
			listOf()
		}
	}
}
