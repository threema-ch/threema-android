/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2020 Threema GmbH
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
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import ch.threema.app.R;
import ch.threema.app.ui.BottomSheetItem;
import ch.threema.app.ui.listitemholder.AbstractListItemHolder;
import ch.threema.app.utils.ConfigUtils;

public class BottomSheetListAdapter extends ArrayAdapter<BottomSheetItem> {
	private List<BottomSheetItem> items;
	private int selectedItem;
	private LayoutInflater layoutInflater;
	private int regularColor;

	private class BottomSheetListHolder extends AbstractListItemHolder {
		AppCompatImageView imageView;
		TextView textView;
	}

	public BottomSheetListAdapter(Context context, List<BottomSheetItem> items, int selectedItem) {
		super(context, R.layout.item_dialog_bottomsheet_list, items);

		this.items = items;
		this.selectedItem = selectedItem;
		this.layoutInflater = LayoutInflater.from(context);

		TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[]{R.attr.textColorSecondary});
		this.regularColor = typedArray.getColor(0, 0);
		typedArray.recycle();
	}

	@NonNull
	@Override
	public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
		View itemView = convertView;
		BottomSheetListHolder holder;

		if (convertView == null) {
			holder = new BottomSheetListHolder();

			// This a new view we inflate the new layout
			itemView = layoutInflater.inflate(R.layout.item_dialog_bottomsheet_list, parent, false);

			holder.imageView = itemView.findViewById(R.id.icon);
			holder.textView = itemView.findViewById(R.id.text);

			itemView.setTag(holder);
		} else {
			holder = (BottomSheetListHolder) itemView.getTag();
		}

		final BottomSheetItem item = items.get(position);

		if (item.getBitmap() != null) {
			holder.imageView.setImageBitmap(item.getBitmap());
		} else {
			holder.imageView.setImageResource(item.getResource());
		}
		holder.textView.setText(item.getTitle());

		if (position == selectedItem) {
			holder.textView.setTextColor(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorAccent));
			holder.imageView.setColorFilter(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorAccent), PorterDuff.Mode.SRC_IN);
		} else {
			holder.textView.setTextColor(regularColor);
			holder.imageView.setColorFilter(regularColor);
		}

		return itemView;
	}
}
