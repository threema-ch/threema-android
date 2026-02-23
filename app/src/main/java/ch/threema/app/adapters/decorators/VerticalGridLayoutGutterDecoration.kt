package ch.threema.app.adapters.decorators

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * A decoration that adds a margin to RecyclerView items.
 *
 * This decorator can be used for RecyclerViews that uses a GridLayoutManager or LinearLayoutManager
 * with vertical orientation.
 */
class VerticalGridLayoutGutterDecoration(gutterPx: Int) : RecyclerView.ItemDecoration() {
    private val gutterPx = gutterPx / 2
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        outRect.top = gutterPx
        outRect.left = gutterPx
        outRect.right = gutterPx
        outRect.bottom = gutterPx
    }
}
