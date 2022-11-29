/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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

package ch.threema.app.filepicker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IconUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.TestUtil;

public class FilePickerAdapter extends ArrayAdapter<FileInfo> {

	private Context context;
	private int resourceID;
	private List<FileInfo> items;
	private boolean isDirectoryMode;
	private @ColorInt int enabledColor = ConfigUtils.getColorFromAttribute(getContext(), R.attr.textColorSecondary);
	private @ColorInt int disabledColor = ConfigUtils.getColorFromAttribute(getContext(), R.attr.textColorTertiary);

	FilePickerAdapter(Context context, int textViewResourceId,
	                  List<FileInfo> objects, boolean directoryMode) {
		super(context, textViewResourceId, objects);
		this.context = context;
		this.resourceID = textViewResourceId;
		this.items = objects;
		this.isDirectoryMode = directoryMode;
	}

	public FileInfo getItem(int i) {
		return items.get(i);
	}

	@NonNull
	@Override
	public View getView(int position, View convertView, @NonNull ViewGroup parent) {
		ViewHolder viewHolder;
		if (convertView == null) {
			LayoutInflater layoutInflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = layoutInflater.inflate(resourceID, null);
			viewHolder = new ViewHolder();
			viewHolder.item = convertView;
			viewHolder.icon = convertView.findViewById(android.R.id.icon);
			viewHolder.name = convertView.findViewById(R.id.name);
			viewHolder.date = convertView.findViewById(R.id.date);
			viewHolder.size = convertView.findViewById(R.id.size);
			viewHolder.extra = convertView.findViewById(R.id.extra);
			convertView.setTag(viewHolder);
		} else {
			viewHolder = (ViewHolder) convertView.getTag();
		}

		FileInfo fileInfo = items.get(position);
		if (fileInfo != null) {

			viewHolder.name.setText(fileInfo.getName());
			viewHolder.size.setText("");
			viewHolder.date.setText("");
			viewHolder.extra.setVisibility(View.GONE);

			if (fileInfo.getData().equalsIgnoreCase(Constants.FOLDER)) {
				viewHolder.icon.setImageResource(R.drawable.ic_folder);
				viewHolder.extra.setVisibility(View.GONE);
				tintItem(viewHolder, true);
			} else if (fileInfo.getData().equalsIgnoreCase(
					Constants.PARENT_FOLDER)) {
				viewHolder.icon.setImageResource(R.drawable.ic_doc_parent);
				viewHolder.date.setText(R.string.parent_directory);
				viewHolder.size.setVisibility(View.GONE);
				viewHolder.extra.setVisibility(View.GONE);
				tintItem(viewHolder, true);
			} else {
				String mimeType = FileUtil.getMimeTypeFromPath(fileInfo.getPath());

				viewHolder.icon.setImageResource(IconUtil.getMimeIcon(mimeType));
				viewHolder.size.setText(fileInfo.getData());
				viewHolder.size.setVisibility(View.VISIBLE);

				if (mimeType != null && mimeType.equals(MimeUtil.MIME_TYPE_ZIP)) {
					String id = getBackupID(fileInfo.getName());

					if (!TestUtil.empty(id)) {
						viewHolder.extra.setText(id);
						viewHolder.extra.setVisibility(View.VISIBLE);
					}
				}

				tintItem(viewHolder, !isDirectoryMode);
			}

			long date = fileInfo.getLastModified();
			if (date != 0L) {
				viewHolder.date.setText(LocaleUtil.formatTimeStampString(context, date, false));
			}

		}
		return convertView;
	}

	private String getBackupID(final String filename) {
		if (!TestUtil.empty(filename)) {
			String[] pieces = filename.split("_");
			if (pieces.length > 2 && pieces[0].equals("threema-backup")) {
				if (!TestUtil.empty(pieces[1]) && !TestUtil.empty(pieces[2])) {
					final String identity = pieces[1];
					final Date time = new Date();

					try {
						time.setTime(Long.valueOf(pieces[2]));
					} catch (NumberFormatException e) {
						return null;
					}

					return identity;
				}
			}
		}
		return null;
	}

	private void tintItem(ViewHolder holder, boolean enabled) {
		int color = enabled ? enabledColor : disabledColor;

		holder.icon.setColorFilter(color);
		holder.name.setTextColor(color);
		holder.date.setTextColor(color);
		holder.size.setTextColor(color);
		holder.extra.setTextColor(color);
	}

	@Override
	public boolean areAllItemsEnabled() {
		return false;
	}

	@Override
	public boolean isEnabled(int position) {
		if (isDirectoryMode) {
			FileInfo fileInfo = items.get(position);
			return fileInfo == null || fileInfo.isFolder() || fileInfo.isParent();
		}
		return true;
	}

	class ViewHolder {
		View item;
		ImageView icon;
		TextView name;
		TextView date;
		TextView size;
		TextView extra;
	}
}
