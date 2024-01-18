/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import java.io.IOException;

import androidx.annotation.NonNull;
import ch.threema.app.R;

public class StickerSelectorAdapter extends ArrayAdapter<String> {
	private String[] items;
	private LayoutInflater layoutInflater;

	public StickerSelectorAdapter(Context context, String[] items) {
		super(context, R.layout.item_sticker_selector, items);

		this.items = items;
		this.layoutInflater = LayoutInflater.from(context);
	}

	private class StickerSelectorHolder {
		ImageView imageView;
		int position;
	}

	@NonNull
	@Override
	public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
		View itemView = convertView;
		final StickerSelectorHolder holder;

		if (convertView == null) {
			holder = new StickerSelectorHolder();

			// This a new view we inflate the new layout
			itemView = layoutInflater.inflate(R.layout.item_sticker_selector, parent, false);

			holder.imageView = itemView.findViewById(R.id.sticker);

			itemView.setTag(holder);
		} else {
			holder = (StickerSelectorHolder) itemView.getTag();
			holder.imageView.setImageBitmap(null);
		}

		final String item = items[position];
		holder.position = position;

		new AsyncTask<Void, Void, Bitmap>() {
			@Override
			protected Bitmap doInBackground(Void... params) {
				try {
					return BitmapFactory.decodeStream(getContext().getAssets().open(item));
				} catch (IOException e) {
					return null;
				}
			}

			@Override
			protected void onPostExecute(Bitmap bitmap) {
				if (bitmap != null) {
					if (holder.position == position) {
						holder.imageView.setImageBitmap(bitmap);
					}
				}
			}

		}.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);

		return itemView;
	}

	public String getItem(int index) {
		return items[index];
	}
}
