/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.adapters

import android.content.Context
import android.text.Spannable
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Filterable
import androidx.annotation.LayoutRes
import ch.threema.app.utils.highlightMatches

/**
 * Meta class for list adapters in RecipientListActivity
 */
abstract class FilterableListAdapter(
    context: Context,
    @LayoutRes resource: Int,
    values: List<Any?>,
) : ArrayAdapter<Any?>(context, resource, values), Filterable {

    @JvmField
    protected val checkedItems: HashSet<Int> = HashSet()

    fun highlightMatches(fullText: CharSequence, filterText: String?): Spannable =
        highlightMatches(
            fullText = fullText,
            filterText = filterText,
            normalize = false,
        )

    fun highlightMatches(fullText: CharSequence, filterText: String?, normalize: Boolean): Spannable =
        fullText.highlightMatches(
            context = context,
            filterText = filterText,
            drawBackground = false,
            shouldNormalize = normalize,
        )

    // all of the following methods ignore any filtering

    /**
     * Get a list of all unfiltered positions of checked elements
     *
     * @return ArrayList of positions
     */
    val checkedItemPositions: ArrayList<Int>
        get() = ArrayList(checkedItems)

    val checkedItemCount: Int
        get() = checkedItems.size

    /**
     * Clear checked items to remove all selections
     */
    fun clearCheckedItems() {
        checkedItems.clear()
    }

    /**
     * Get a set of all checked items in the full dataset managed by this adapter
     *
     * @return HashSet of items, identified by an object, typically a unique id of the element
     */
    abstract fun getCheckedItems(): HashSet<*>?

    /**
     * Get data of item in the item identified by its view
     *
     * @return object represented by v
     */
    abstract fun getClickedItem(v: View?): Any?

    // hack to make header/footer appear
    override fun isEmpty(): Boolean = false
}
