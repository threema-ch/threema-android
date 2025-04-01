/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ui.BottomSheetItem;
import ch.threema.app.ui.listitemholder.AbstractListItemHolder;

public class BottomSheetGridAdapter extends ArrayAdapter<BottomSheetItem> {
    private List<BottomSheetItem> items;
    private LayoutInflater layoutInflater;

    private class BottomSheetGridDialogHolder extends AbstractListItemHolder {
        AppCompatImageView imageView;
        TextView textView;
    }

    public BottomSheetGridAdapter(Context context, List<BottomSheetItem> items) {
        super(context, R.layout.item_dialog_bottomsheet_grid, items);

        this.items = items;
        this.layoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        View itemView = convertView;
        BottomSheetGridDialogHolder holder;

        if (convertView == null) {
            holder = new BottomSheetGridDialogHolder();

            // This a new view we inflate the new layout
            itemView = layoutInflater.inflate(R.layout.item_dialog_bottomsheet_grid, parent, false);

            holder.imageView = itemView.findViewById(R.id.icon);
            holder.textView = itemView.findViewById(R.id.text);

            itemView.setTag(holder);
        } else {
            holder = (BottomSheetGridDialogHolder) itemView.getTag();
        }

        final BottomSheetItem item = items.get(position);

        if (item.getBitmap() != null) {
            holder.imageView.setImageBitmap(item.getBitmap());
        } else {
            holder.imageView.setImageResource(item.getResource());
        }
        holder.textView.setText(item.getTitle());

        return itemView;
    }
}
