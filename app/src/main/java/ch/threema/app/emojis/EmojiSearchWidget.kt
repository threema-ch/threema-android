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

package ch.threema.app.emojis

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.utils.EditTextUtil
import ch.threema.app.utils.ViewUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class EmojiSearchWidget : ConstraintLayout {
	lateinit var searchInput: EditText
	lateinit var clearSearchButton: AppCompatImageButton

	private lateinit var listener: EmojiSearchListener
	private lateinit var resultsList: RecyclerView
	private lateinit var noResultText: TextView
	private lateinit var resultAdapter: EmojiListAdapter
	private lateinit var emojiService: EmojiService
	private lateinit var diverseEmojiPopup: DiverseEmojiPopup

	constructor(context: Context) : super(context)
	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
		context,
		attrs,
		defStyleAttr
	)

	fun init(listener: EmojiSearchListener, emojiService: EmojiService) {
		this.listener = listener
		this.emojiService = emojiService

		inflate(context, R.layout.emoji_search_widget, this)

		searchInput = findViewById(R.id.search_term)!!
		clearSearchButton = findViewById(R.id.button_clear_search)!!
		clearSearchButton.setOnClickListener { searchInput.setText("") }

		searchInput.setOnFocusChangeListener { v, hasFocus -> if (v == searchInput && !hasFocus) {
			listener.onHideEmojiSearch()
			hide()
		}  }

		findViewById<AppCompatImageButton>(R.id.button_show_picker).setOnClickListener { listener.onShowPicker() }
        configureEmojiSearch()
		configureSearchResults()

		diverseEmojiPopup = DiverseEmojiPopup(context, this)
		diverseEmojiPopup.setListener(object : DiverseEmojiPopup.DiverseEmojiPopupListener {
			override fun onDiverseEmojiClick(
				parentEmojiSequence: String?,
				emojiSequence: String?
			) {
				onEmojiClick(emojiSequence)
				if (parentEmojiSequence != null && emojiSequence != null) {
					emojiService.setDiverseEmojiPreference(parentEmojiSequence, emojiSequence)
				}
			}

			override fun onOpen() {
				// noop
			}

			override fun onClose() {
				// noop
			}
		})
	}

	override fun isShown(): Boolean {
		return visibility == VISIBLE
	}

	fun show() {
		visibility = VISIBLE
	}

	fun hide() {
		emojiService.saveRecentEmojis()
		searchInput.setText("")
		visibility = GONE
	}

    private fun configureEmojiSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
	        private var ongoingJob: Job? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // noop
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // noop
            }

            override fun afterTextChanged(s: Editable?) {
	            ongoingJob?.cancel()
	            ongoingJob = MainScope().launch {
		            setSearchResults(emojiService.search(s.toString()))
	            }
	            clearSearchButton.visibility = if (s.toString().isEmpty()) {
	            	View.GONE
	            } else {
	            	View.VISIBLE
	            }
            }
        })
	    searchInput.setOnEditorActionListener { v, i, _ ->
		    if (i == EditorInfo.IME_ACTION_SEARCH) {
		    	EditTextUtil.hideSoftKeyboard(v)
		    	true
	        } else {
	        	false
		    }
	    }
    }

	private fun configureSearchResults() {
		resultAdapter = EmojiListAdapter(context, object : EmojiListAdapter.KeyClickListener {
			override fun onEmojiKeyClicked(emojiCodeString: String?) = onEmojiClick(emojiCodeString)

			override fun onEmojiKeyLongClicked(view: View?, emojiCodeString: String?) {
				val emojiInfo = EmojiUtil.getEmojiInfo(emojiCodeString)
				if (emojiInfo != null && emojiInfo.diversityFlag == EmojiSpritemap.DIVERSITY_PARENT) {
					diverseEmojiPopup.show(view, emojiCodeString)
				}
			}
		}, emojiService)

		noResultText = findViewById(R.id.no_search_results)!!
		noResultText.height = resultAdapter.getItemHeight()
		resultsList = findViewById(R.id.search_results)
		resultsList.minimumHeight = resultAdapter.getItemHeight()
		resultsList.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
		resultsList.adapter = resultAdapter

		setSearchResults(emojiService.getRecentEmojis())
	}

	private fun onEmojiClick(emojiSequence: String?) {
		emojiSequence?.let {
			listener.onEmojiClick(it)
			emojiService.addToRecentEmojis(it)
		}
	}

	private fun setSearchResults(results: List<EmojiInfo>) {
		resultsList.scrollToPosition(0)
		resultAdapter.setEmojis(results)
		if (results.isEmpty()) {
			resultsList.visibility = View.GONE
			noResultText.visibility = View.VISIBLE
		} else {
			resultsList.visibility = View.VISIBLE
			noResultText.visibility = View.GONE
		}
	}

	interface EmojiSearchListener {
		fun onHideEmojiSearch()
		fun onShowPicker()
		fun onEmojiClick(emojiSequence: String)
	}
}
