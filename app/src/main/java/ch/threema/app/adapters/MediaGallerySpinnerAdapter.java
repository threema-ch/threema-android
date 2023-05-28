/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import ch.threema.app.R;

public class MediaGallerySpinnerAdapter extends ArrayAdapter<String> {

	Context context;
	String[] values;
	String titleText;
	LayoutInflater inflater;
	String subtitle;

	public MediaGallerySpinnerAdapter(Context context, String[] values, String titleText) {
		super(context, R.layout.spinner_media_gallery, values);

		this.context = context;
		this.values = values;
		this.titleText = titleText;
		this.inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.subtitle = "";
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = inflater.inflate(R.layout.spinner_media_gallery, null);
		}
		TextView title = convertView.findViewById(R.id.title);
		TextView subtitleView = convertView.findViewById(R.id.subtitle_text);
		title.setText(this.titleText);
		subtitleView.setText(subtitle);
		return convertView;

	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
		}
		((TextView) convertView).setText(this.values[position]);
		return convertView;
	}

	@Override
	public int getCount() {
		return this.values.length;
	}

	@Override
	public String getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	public void setSubtitle(String subtitle) {
		this.subtitle = subtitle;
	}
}
