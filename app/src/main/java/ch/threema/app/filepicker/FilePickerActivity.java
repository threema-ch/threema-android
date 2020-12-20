/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2020 Threema GmbH
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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.StorageUtil;
import ch.threema.app.utils.TestUtil;

public class FilePickerActivity extends ThreemaToolbarActivity implements ListView.OnItemClickListener {
	private static final Logger logger = LoggerFactory.getLogger(FilePickerActivity.class);

	private static final int PERMISSION_STORAGE = 1;

	public static final String INTENT_DATA_DEFAULT_PATH = "defpath";
	public static final String INTENT_DATA_SELECT_DIRECTORY = "directory";
	public static final String EXTRA_DIRECTORY = "dir";

	private String currentFolder;
	private FilePickerAdapter fileArrayListAdapter;
	private FileFilter fileFilter;
	private File fileSelected;
	private ListView listView;
	private ArrayList<String> extensions;
	private ArrayList<String> rootPaths = new ArrayList<>(2);
	private ActionBar actionBar;
	private DrawerLayout drawerLayout;
	private Comparator<FileInfo> comparator;
	private ExtendedFloatingActionButton floatingActionButton;
	private int currentRoot = 0;

	private boolean isDirectoriesOnly = false, isExternal = false;

	@Override
	public int getLayoutResource() {
		return R.layout.activity_filepicker;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		boolean result = super.initActivity(savedInstanceState);

		if (getConnectionIndicator() != null) {
			getConnectionIndicator().setVisibility(View.INVISIBLE);
		}

		String defaultPath = null;

		actionBar = getSupportActionBar();

		floatingActionButton = findViewById(R.id.floating);

		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			if (extras.getStringArrayList(Constants.KEY_FILTER_FILES_EXTENSIONS) != null) {
				extensions = extras
					.getStringArrayList(Constants.KEY_FILTER_FILES_EXTENSIONS);
				fileFilter = new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return ((pathname.isDirectory()) ||
							(pathname.getName().contains(".") &&
								extensions.contains(pathname.getName().substring(pathname.getName().lastIndexOf(".")))));
					}
				};
			}

			defaultPath = extras.getString(INTENT_DATA_DEFAULT_PATH, null);
			if (defaultPath != null && !(new File(defaultPath)).exists()) {
				defaultPath = null;
			}

			if (extras.getBoolean(INTENT_DATA_SELECT_DIRECTORY, false)) {
				floatingActionButton.setText(R.string.select_directory_for_backup);
				floatingActionButton.setIconResource(R.drawable.ic_check);
				floatingActionButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent intent = new Intent();
						intent.putExtra(EXTRA_DIRECTORY, currentFolder);
						setResult(Activity.RESULT_OK, intent);
						finish();
					}
				});
				isDirectoriesOnly = true;
			}
		}

		listView = findViewById(android.R.id.list);
		if (listView == null) {
			Toast.makeText(this, "Unable to inflate layout", Toast.LENGTH_LONG).show();
			finish();
			return false;
		}

		listView.setOnItemClickListener(this);
		if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
			listView.setDivider(getResources().getDrawable(R.drawable.divider_listview_dark));
		} else {
			listView.setDivider(getResources().getDrawable(R.drawable.divider_listview));
		}
		if (isDirectoriesOnly) {
			listView.setOnScrollListener(new AbsListView.OnScrollListener() {
				@Override
				public void onScrollStateChanged(AbsListView view, int scrollState) {
				}

				@Override
				public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
					if (floatingActionButton != null) {
						if (firstVisibleItem == 0) {
							floatingActionButton.extend();
						} else {
							floatingActionButton.shrink();
						}
					}
				}
			});
		}
		listView.setDividerHeight(getResources().getDimensionPixelSize(R.dimen.list_divider_height));

		if (getRootPaths() == 0) {
			Toast.makeText(this, "No storage found", Toast.LENGTH_LONG).show();
			finish();
			return false;
		};

		drawerLayout = findViewById(R.id.drawer_layout);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this,
			drawerLayout, getToolbar(), R.string.open_navdrawer, R.string.close);
		toggle.setDrawerIndicatorEnabled(true);
		toggle.setDrawerSlideAnimationEnabled(true);
		toggle.syncState();
		drawerLayout.addDrawerListener(toggle);


		if (defaultPath != null) {
			currentRoot = 0;
			currentFolder = defaultPath;
			if (currentFolder != null) {
				for (int i=0; i < rootPaths.size(); i++) {
					if (currentFolder.startsWith(rootPaths.get(i))) {
						currentRoot = i;
						break;
					}
				}
			}

			// sort by date (most recent first)
			comparator = new Comparator<FileInfo>() {
				@Override
				public int compare(FileInfo f1, FileInfo f2) {
					return f1.getLastModified() == f2.getLastModified() ? 0 :
						f1.getLastModified() < f2.getLastModified() ? 1 :
							-1;
				}
			};
		} else {
			currentFolder = rootPaths.get(0);
			currentRoot = 0;
			// sort by filename
			comparator = new Comparator<FileInfo>() {
				@Override
				public int compare(FileInfo f1, FileInfo f2) {
					return f1.getName().compareTo(f2.getName());
				}
			};
		}

		NavigationView navigationView = findViewById(R.id.nav_view);
		if (navigationView != null) {
			setupDrawerContent(navigationView);
		}

		setResult(RESULT_CANCELED);

		if (ConfigUtils.requestStoragePermissions(this, null, PERMISSION_STORAGE)) {
			scanFiles(currentFolder);
		}

		return result;
	}

	private int getRootPaths() {
		// Internal storage - should always be around
		rootPaths.addAll(Arrays.asList(StorageUtil.getStorageDirectories(this)));
		return rootPaths.size();
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
		return super.onKeyDown(keyCode, event);
	}

	private void scanFiles(String path) {
		File f = new File(path);
		File[] folders;
		if (fileFilter != null)
			folders = f.listFiles(fileFilter);
		else
			folders = f.listFiles();

		if (f.getName().equalsIgnoreCase(
				Environment.getExternalStorageDirectory().getName())) {
			actionBar.setTitle(R.string.internal_storage);
		}
		else {
			actionBar.setTitle(f.getName());
		}

		List<FileInfo> dirs = new ArrayList<FileInfo>();
		List<FileInfo> files = new ArrayList<FileInfo>();
		try {
			for (File file : folders) {
				if (file.isDirectory() && !file.isHidden())
					dirs.add(new FileInfo(file.getName(),
							Constants.FOLDER, file.getAbsolutePath(),
							file.lastModified(),
							true, false));
//				else if (!isDirectoriesOnly) {
				else {
					if (!file.isHidden())
						files.add(new FileInfo(file.getName(),
								Formatter.formatFileSize(this, file.length()),
								file.getAbsolutePath(),
								file.lastModified(), false, false));
				}
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		Collections.sort(dirs);
		Collections.sort(files, comparator);
		dirs.addAll(files);

		String canonicalFilePath = null;
		try {
			canonicalFilePath = f.getCanonicalPath();
		} catch (IOException e) {
			logger.error("Exception", e);
		}

		if (!TestUtil.empty(canonicalFilePath) && !isTop(canonicalFilePath)) {
			if (f.getParentFile() != null)
				dirs.add(0, new FileInfo("..",
						Constants.PARENT_FOLDER, f.getParent(), 0,
						false, true));
		}

		fileArrayListAdapter = new FilePickerAdapter(FilePickerActivity.this,
				R.layout.item_filepicker, dirs, isDirectoriesOnly);

		listView.setAdapter(fileArrayListAdapter);

		if (isDirectoriesOnly) {
			floatingActionButton.setVisibility(f.canWrite() ? View.VISIBLE : View.GONE);
		}
	}

	private boolean isTop(String path) {
		for(String rootPath : rootPaths) {
			File file = new File(rootPath);
			try {
				if (file.getCanonicalPath().equalsIgnoreCase(path)) {
					return true;
				}
			} catch (IOException e) {
				logger.error("Exception", e);
			}
		}
		return false;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		FileInfo fileDescriptor = fileArrayListAdapter.getItem(position);
		if (fileDescriptor.isFolder() || fileDescriptor.isParent()) {
			currentFolder = fileDescriptor.getPath();
			scanFiles(currentFolder);
		} else {
			fileSelected = new File(fileDescriptor.getPath());

			Intent intent = new Intent();
			intent.setData(Uri.fromFile(fileSelected));
			setResult(Activity.RESULT_OK, intent);
			finish();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				if (drawerLayout != null) {
					drawerLayout.openDrawer(GravityCompat.START);
				}
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void setupDrawerContent(final NavigationView navigationView) {
		Menu menu = navigationView.getMenu();
		if (menu != null) {
			if (rootPaths.size() > 1) {
				for (int i = 1; i < rootPaths.size(); i++) {
					File file = new File(rootPaths.get(i));
					MenuItem item = menu.add(R.id.main_group, Menu.NONE, i, file.getName()).setIcon(R.drawable.ic_sd_card_black_24dp);
					if (i == currentRoot) {
						item.setChecked(true);
					}
				}
			}
			menu.setGroupCheckable(R.id.main_group, true, true);

			if (currentRoot == 0) {
				MenuItem menuItem = menu.findItem(R.id.internal_storage);
				menuItem.setChecked(true);
			}
		}

		navigationView.setNavigationItemSelectedListener(
				new NavigationView.OnNavigationItemSelectedListener() {
					@Override
					public boolean onNavigationItemSelected(MenuItem menuItem) {
						currentFolder = rootPaths.get(menuItem.getOrder());
						currentRoot = menuItem.getOrder();
						scanFiles(currentFolder);
						drawerLayout.closeDrawers();
						menuItem.setChecked(true);
						return true;
					}
				});
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   @NonNull String permissions[], @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_STORAGE:
				/* From the docs: It is possible that the permissions request interaction with the user is
				 * interrupted. In this case you will receive empty permissions and results arrays which
				 * should be treated as a cancellation.
				 */
				if (grantResults.length >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					scanFiles(currentFolder);
				} else {
					finish();
				}
		}
	}
}
