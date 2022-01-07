/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app.emojis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import ch.threema.app.R;

public class EmojiPagerAdapter extends PagerAdapter {

	private Context context;
	private EmojiGridAdapter.KeyClickListener listener;
	private EmojiPicker emojiPicker;
	private EmojiRecent emojiRecent;
	private HashMap<String, String> diverseEmojiPrefs;
	private LayoutInflater layoutInflater;

	EmojiPagerAdapter(Context context,
	                  EmojiPicker emojiPicker,
	                  EmojiRecent emojiRecent,
	                  HashMap<String, String> diverseEmojiPrefs,
	                  EmojiGridAdapter.KeyClickListener listener) {
		this.context = context;
		this.listener = listener;
		this.emojiPicker = emojiPicker;
		this.emojiRecent = emojiRecent;
		this.diverseEmojiPrefs = diverseEmojiPrefs;
		this.layoutInflater = LayoutInflater.from(context);
	}

	@Override
	public int getCount() {
		return emojiPicker.getNumberOfPages();
	}

	@NonNull
	@SuppressLint("StaticFieldLeak")
	@Override
	public Object instantiateItem(@NonNull final ViewGroup container, final int position) {

		final View layout = layoutInflater.inflate(R.layout.emoji_picker_gridview, null);
		final GridView gridView = (GridView) layout;

		new AsyncTask<Void, Void, EmojiGridAdapter>() {
			@Override
			protected EmojiGridAdapter doInBackground(Void... params) {
				return new EmojiGridAdapter(
						context,
						position,
						emojiRecent,
						diverseEmojiPrefs,
						listener);
			}

			@Override
			protected void onPostExecute(EmojiGridAdapter adapter) {
				container.addView(layout);
				gridView.setAdapter(adapter);
			}
		}.execute();

		// tag this view for efficient refreshing
		gridView.setTag(Integer.toString(position));

		((GridView) layout).setOnItemClickListener((adapterView, view, i, l) -> {
			// this listener is used for hardware keyboards only.
			EmojiInfo item = (EmojiInfo) adapterView.getAdapter().getItem(i);
			listener.onEmojiKeyClicked(item.emojiSequence);
		});

		return layout;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, @NonNull Object view) {
		container.removeView((View) view);
	}

	@Override
	public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
		return view == object;
	}

	@Override
	public CharSequence getPageTitle(int position) {
		return emojiPicker.getGroupTitle(position);
	}

}
