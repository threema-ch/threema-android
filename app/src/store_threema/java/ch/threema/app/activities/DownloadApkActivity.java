/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.widget.Toast;

import org.slf4j.Logger;

import java.io.File;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.DownloadUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.base.utils.LoggingUtil;

public class DownloadApkActivity extends AppCompatActivity implements GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DownloadApkActivity");

	private static final String DIALOG_TAG_DOWNLOAD_UPDATE = "cfu";
	private static final String DIALOG_TAG_DOWNLOADING = "dtd";

	private static final String PREF_STRING = "download_apk_dialog_time";

	private static final String BUNDLE_DOWNLOAD_ID = "download_id";
	private static final String BUNDLE_FILE_PATH = "download_file_path";

	private static final int PERMISSION_REQUEST_WRITE_FILE = 9919;

	public static final String EXTRA_FORCE_UPDATE_DIALOG = "forceu";

	private SharedPreferences sharedPreferences;
	private String downloadUrl;
	private DownloadUtil.DownloadState downloadState;

	private final ActivityResultLauncher<Intent> requestUnknownSourcesSettingsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
		result -> {
			if (downloadState != null) {
				installPackage(downloadState.getDestinationUri());
			} else {
				logger.error("downloadState should not be null");
			}
		});

	private final BroadcastReceiver downloadApkFinishedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING, true);

			//check if the broadcast message is for our Enqueued download
			final long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

			if (referenceId > 0 && context != null) {
				downloadState = DownloadUtil.getNewestDownloadState(referenceId);
				if (downloadState != null) {
					int status = 0, reason = 0;
					DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
					DownloadManager.Query query = new DownloadManager.Query();
					query.setFilterById(downloadState.getDownloadId());
					Cursor cursor = null;
					try {
						cursor = downloadManager.query(query);
						if (cursor.moveToFirst()) {
							status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
							reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
						}
					} finally {
						if (cursor != null) {
							cursor.close();
						}
					}

					if (status == DownloadManager.STATUS_SUCCESSFUL) {
						Uri uri = downloadManager.getUriForDownloadedFile(referenceId);
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !getPackageManager().canRequestPackageInstalls()) {
								try {
									requestUnknownSourcesSettingsLauncher.launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(Uri.parse(String.format("package:%s", getPackageName()))));
								} catch (ActivityNotFoundException e) {
									logger.error("No activity for unknown sources", e);
									Toast.makeText(getApplicationContext(), getString(R.string.enable_unknown_sources, getString(R.string.app_name)), Toast.LENGTH_LONG).show();
									finishUp();
								}
							} else {
								installPackage(uri);
							}
							return;
						} else {
							Uri fileUri = Uri.fromFile(downloadState.getDestinationFile());
							Intent installIntent = new Intent(Intent.ACTION_VIEW);
							installIntent.setDataAndType(fileUri, "application/vnd.android.package-archive");
							installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							context.startActivity(installIntent);
						}
					} else {
						Toast.makeText(getApplicationContext(), getString(R.string.download_failed, reason), Toast.LENGTH_LONG).show();
					}
					finishUp();
				}
			}
		}
	};

	private void finishUp() {
		new Handler().postDelayed(this::finish, 1000);
	}

	private void installPackage(@NonNull Uri downloadedFileUri) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !getPackageManager().canRequestPackageInstalls()) {
			Toast.makeText(getApplicationContext(), getString(R.string.enable_unknown_sources, getString(R.string.app_name)), Toast.LENGTH_LONG).show();
		} else {
			Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
			installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			installIntent.setData(downloadedFileUri);
			try {
				startActivity(installIntent);
			} catch (Exception e) {
				logger.error("error installing apk", e);
			}
		}
		finishUp();
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
			setTheme(R.style.Theme_Threema_Translucent_Dark);
		}

		if (savedInstanceState != null) {
			long downloadID = savedInstanceState.getLong(BUNDLE_DOWNLOAD_ID, -1);
			if (downloadID >= 0) {
				downloadState = new DownloadUtil.DownloadState() {
					private final DownloadManager manager = (DownloadManager) DownloadApkActivity.this.getSystemService(Context.DOWNLOAD_SERVICE);

					@Override
					public long getDownloadId() {
						return downloadID;
					}

					@Override
					public Uri getDestinationUri() {
						return manager.getUriForDownloadedFile(getDownloadId());
					}

					@Override
					public File getDestinationFile() {
						return new File(savedInstanceState.getString(BUNDLE_FILE_PATH));
					}
				};
			}
		}

		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext());
		long lastShownTime = sharedPreferences.getLong(PREF_STRING, 0);

		Intent intent = getIntent();

		if (intent.getBooleanExtra(EXTRA_FORCE_UPDATE_DIALOG, false) || (System.currentTimeMillis() > (lastShownTime + DateUtils.DAY_IN_MILLIS))) {
			GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.update_available, IntentDataUtil.getMessage(intent), R.string.download, R.string.not_now, false);
			dialog.setData(downloadUrl = IntentDataUtil.getUrl(intent));
			getSupportFragmentManager().beginTransaction().add(dialog, DIALOG_TAG_DOWNLOAD_UPDATE).commitAllowingStateLoss();

			this.registerReceiver(this.downloadApkFinishedReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
		} else {
			finish();
		}
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		if (downloadState != null) {
			outState.putLong(BUNDLE_DOWNLOAD_ID, downloadState.getDownloadId());
			outState.putString(BUNDLE_FILE_PATH, downloadState.getDestinationFile().getPath());
		}
	}

	@Override
	protected void onDestroy() {
		try {
			this.unregisterReceiver(this.downloadApkFinishedReceiver);
		} catch (Exception ignore) { }

		super.onDestroy();
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onYes(String tag, Object data) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || ConfigUtils.requestStoragePermissions(this, null, PERMISSION_REQUEST_WRITE_FILE)) {
			reallyDownload((String) data);
		}
	}

	private void reallyDownload(String data) {
		GenericProgressDialog.newInstance(R.string.downloading, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING);
		DownloadUtil.downloadUpdate(this, data);
	}

	@Override
	public void onNo(String tag, Object data) {
		sharedPreferences.edit().putLong(PREF_STRING, System.currentTimeMillis()).apply();

		finish();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			if (requestCode == PERMISSION_REQUEST_WRITE_FILE) {
				reallyDownload(downloadUrl);
			}
		} else {
			if (requestCode == PERMISSION_REQUEST_WRITE_FILE) {
				Toast.makeText(getApplicationContext(), R.string.error_saving_file, Toast.LENGTH_LONG).show();
			}
		}
	}
}
