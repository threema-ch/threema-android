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

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import ch.threema.data.models.EmojiReactionData
import ch.threema.storage.models.AbstractMessageModel
import kotlin.collections.eachCount
import kotlin.collections.groupingBy
import kotlin.collections.sortedByDescending
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class EmojiReactionsOverviewAdapter(
    fragmentActivity: FragmentActivity,
    private val viewModel: EmojiReactionsViewModel,
    private val messageModel: AbstractMessageModel,
) :
    FragmentStateAdapter(fragmentActivity) {
    private val items = mutableListOf<Map.Entry<String, Int>>()

    init {
        fragmentActivity.lifecycleScope.launch {
            fragmentActivity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.filterNotNull().collect { viewState ->
                    items.clear()
                    val reactions: MutableList<EmojiReactionData> =
                        viewState.emojiReactions.toMutableList()
                    if (reactions.isNotEmpty()) {
                        val sortedList = reactions.groupingBy { it.emojiSequence }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }

                        items.addAll(sortedList)
                    }
                    notifyDataSetChanged()
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun createFragment(position: Int): Fragment {
        return EmojiReactionsOverviewFragment(items[position].key, messageModel = messageModel)
    }
}
