/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.emojireactions

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.data.models.EmojiReactionData
import ch.threema.storage.models.AbstractMessageModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val RECYCLER_VIEW_STATE = "recyclerViewState"

class EmojiReactionsOverviewFragment(
    val emojiSequence: String? = null,
    val messageModel: AbstractMessageModel,
) : Fragment(), EmojiReactionsOverviewListAdapter.OnItemClickListener {
    private val emojiReactionsViewModel: EmojiReactionsViewModel by activityViewModels()
    private lateinit var emojiReactionsOverviewListAdapter: EmojiReactionsOverviewListAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_emojireactions_overview, container, false)
        recyclerView = view.findViewById(R.id.emoji_reactions_list)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emojiReactionsOverviewListAdapter = EmojiReactionsOverviewListAdapter(
            messageModel = messageModel,
            onItemClickListener = this,
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = emojiReactionsOverviewListAdapter
        recyclerView.setHasFixedSize(true)
        recyclerView.isNestedScrollingEnabled = true

        val targetEmojiSequence = emojiSequence
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                emojiReactionsViewModel.emojiReactionsUiState.collect { uiState ->
                    val emojiReactionsForSequence =
                        uiState.emojiReactions.filter { it.emojiSequence == targetEmojiSequence }
                    emojiReactionsOverviewListAdapter.submitList(emojiReactionsForSequence)
                }
            }
        }
    }

    override fun onRemoveClick(data: EmojiReactionData, position: Int) {
        val messageService = ThreemaApplication.requireServiceManager().messageService
        CoroutineScope(Dispatchers.Default).launch {
            messageService.sendEmojiReaction(
                messageModel,
                data.emojiSequence,
                messageService.getMessageReceiver(messageModel),
                false,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(
            RECYCLER_VIEW_STATE,
            recyclerView.layoutManager?.onSaveInstanceState(),
        )
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        @Suppress("DEPRECATION")
        savedInstanceState?.getParcelable<Parcelable>(RECYCLER_VIEW_STATE)?.let {
            recyclerView.layoutManager?.onRestoreInstanceState(it)
        }
    }
}
