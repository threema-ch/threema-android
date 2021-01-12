/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.app.adapters;

import android.content.Context;
import android.text.Spannable;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Filterable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import ch.threema.app.utils.TextUtil;

/**
 * Meta class for list adapters in RecipientListActivity
 */
public abstract class FilterableListAdapter extends ArrayAdapter<Object> implements Filterable {
	protected HashSet<Integer> checkedItems = new HashSet<>();

	public FilterableListAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<Object> values) {
		super(context, resource, values);
	}

	public Spannable highlightMatches(CharSequence fullText, String filterText) {
		return highlightMatches(fullText, filterText, false);
	}

	public Spannable highlightMatches(CharSequence fullText, String filterText, boolean normalize) {
		return TextUtil.highlightMatches(getContext(), fullText, filterText, false, normalize);
	}

	/* all of the following methods ignore any filtering */

	/**
	 * Get a list of all unfiltered positions of checked elements
	 * @return ArrayList of positions
	 */
	public ArrayList<Integer> getCheckedItemPositions() {
		return new ArrayList<>(checkedItems);
	}

	/**
	 * Get the count of checked items in the dataset
	 * @return number of checked items
	 */
	public int getCheckedItemCount() {
		return checkedItems.size();
	}

	/**
	 * Clear checked items to remove all selections
	 */
	public void clearCheckedItems() {
		checkedItems.clear();
	}

	/**
	 * Get a set of all checked items in the full dataset managed by this adapter
	 * @return HashSet of items, identified by an object, typically a unique id of the element
	 */
	public abstract HashSet<?> getCheckedItems();

	/**
	 * Get data of item in the item identified by its view
	 * @param v
	 * @return object represented by v
	 */
	public abstract Object getClickedItem(View v);

	@Override
	public boolean isEmpty() {
		// hack to make header/footer appear
		return false;
	}
}
