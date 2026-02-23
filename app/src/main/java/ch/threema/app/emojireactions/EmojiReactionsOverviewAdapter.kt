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
