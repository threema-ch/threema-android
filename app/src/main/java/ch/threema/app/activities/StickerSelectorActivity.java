/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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

package ch.threema.app.activities;

import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.adapters.StickerSelectorAdapter;
import ch.threema.base.utils.LoggingUtil;

public class StickerSelectorActivity extends ThreemaToolbarActivity implements LoaderManager.LoaderCallbacks<String[]> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("StickerSelectorActivity");

	private static final String STICKER_DIRECTORY = "emojione";
	private static final String STICKER_INDEX = STICKER_DIRECTORY + "/contents.txt";
	public static final String EXTRA_STICKER_PATH = "spath";
	private GridView gridView;
	private StickerSelectorAdapter adapter;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		gridView = findViewById(R.id.grid_view);
		gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				if (adapter != null) {
					Intent intent = new Intent();
					intent.putExtra(EXTRA_STICKER_PATH, adapter.getItem(i));
					setResult(RESULT_OK, intent);
					finish();
				}
			}
		});

		getLoaderManager().initLoader(0, null, this).forceLoad();
	}

	private static class StickerLoader extends AsyncTaskLoader<String[]> {
		StickerLoader(Context context) {
			super(context);
		}

		@Override
		public String[] loadInBackground() {
			// AssetManager.getAssets().list is notoriously slow on some phones, so we use a list file to get the filenames quickly
			try {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader((getContext().getAssets().open(STICKER_INDEX))))) {
					List<String> files = new ArrayList<>();

					String line;
					while((line =reader.readLine()) != null) {
						files.add(STICKER_DIRECTORY + "/" + line);
					}

					return files.toArray(new String[0]);
				}
			} catch (IOException e) {
				logger.error("Exception", e);
			}
			return new String[0];
		}
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_sticker_selector;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				break;
		}
		return false;
	}

	@Override
	public Loader<String[]> onCreateLoader(int id, Bundle args) {
		return new StickerLoader(getApplicationContext());
	}

	@Override
	public void onLoadFinished(Loader<String[]> loader, String[] data) {
		adapter = new StickerSelectorAdapter(this, data);
		gridView.setAdapter(adapter);
	}

	@Override
	public void onLoaderReset(Loader<String[]> loader) {
		gridView.setAdapter(null);
	}
}
