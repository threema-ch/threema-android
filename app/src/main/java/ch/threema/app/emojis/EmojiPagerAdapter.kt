package ch.threema.app.emojis

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.GridView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.PagerAdapter
import ch.threema.android.getCurrentInsets
import ch.threema.app.R
import ch.threema.app.emojireactions.EmojiReactionsGridAdapter
import ch.threema.app.utils.DispatcherProvider
import ch.threema.data.models.EmojiReactionData
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EmojiPagerAdapter(
    private val activity: AppCompatActivity,
    private val emojiPicker: EmojiPicker,
    private val emojiService: EmojiService,
    private val listener: EmojiGridAdapter.KeyClickListener,
    private val reactionsListener: EmojiReactionsGridAdapter.KeyClickListener?,
    private val emojiReactions: List<EmojiReactionData>?,
) : PagerAdapter(), KoinComponent {

    private val dispatcherProvider: DispatcherProvider by inject()

    private val layoutInflater: LayoutInflater = LayoutInflater.from(activity)

    override fun getCount() = emojiPicker.numberOfPages

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout: View
        val recentsGridView: GridView

        if (emojiReactions != null && position == 0) {
            layout = layoutInflater.inflate(R.layout.emoji_reactions_picker_gridview, null)
            recentsGridView = layout.findViewById(R.id.emoji_gridview)
        } else {
            layout = layoutInflater.inflate(R.layout.emoji_picker_gridview, null)
            recentsGridView = layout as GridView
        }

        @SuppressLint("WrongConstant")
        val insets = activity.getCurrentInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        layout.setPadding(0, 0, 0, insets.bottom)

        activity.lifecycleScope.launch {
            val adapter = withContext(dispatcherProvider.worker) {
                EmojiGridAdapter(
                    activity,
                    position,
                    emojiService,
                    listener,
                )
            }
            container.addView(layout)
            recentsGridView.setAdapter(adapter)
        }

        // tag this view for efficient refreshing
        recentsGridView.tag = position.toString()
        recentsGridView.setOnItemClickListener { adapterView: AdapterView<*>, _, i: Int, _ ->
            // this listener is used for hardware keyboards only.
            val item = adapterView.getAdapter().getItem(i) as EmojiInfo
            listener.onEmojiKeyClicked(item.emojiSequence)
        }

        if (position == 0 && emojiReactions != null) {
            setupEmojiReactions(layout, emojiReactions)
        }

        return layout
    }

    private fun setupEmojiReactions(layout: View, emojiReactions: List<EmojiReactionData>) {
        val reactionsGridView = layout.findViewById<GridView>(R.id.reactions_gridview)
        val reactionsTitle = layout.findViewById<TextView>(R.id.reactions_title)
        val recentsTitle = layout.findViewById<TextView>(R.id.recents_title)

        if (emojiReactions.isEmpty()) {
            reactionsTitle.isVisible = false
            recentsTitle.isVisible = false
            reactionsGridView.isVisible = false
        } else {
            emojiService.syncRecentEmojis()
            recentsTitle.isVisible = !emojiService.hasNoRecentEmojis()

            activity.lifecycleScope.launch {
                val adapter = withContext(dispatcherProvider.worker) {
                    EmojiReactionsGridAdapter(
                        activity,
                        emojiReactions,
                        reactionsListener,
                    )
                }
                reactionsGridView.setAdapter(adapter)
            }
        }
    }

    override fun destroyItem(container: ViewGroup, position: Int, view: Any) {
        container.removeView(view as View)
    }

    override fun isViewFromObject(view: View, `object`: Any) = view === `object`

    override fun getPageTitle(position: Int): CharSequence? = emojiPicker.getGroupTitle(position)
}
