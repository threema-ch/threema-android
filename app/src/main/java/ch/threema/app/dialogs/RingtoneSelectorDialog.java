/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

package ch.threema.app.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.msgpack.core.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.utils.RingtoneUtil;

public class RingtoneSelectorDialog extends ThreemaDialogFragment {
	private static final Logger logger = LoggerFactory.getLogger(RingtoneSelectorDialog.class);

	private RingtoneSelectorDialogClickListener callback;
	private Activity activity;
	private AlertDialog alertDialog;
	private Uri selectedRingtoneUri, defaultUri;
	private static final String CURSOR_DEFAULT_ID = "-2";
	private static final String CURSOR_NONE_ID = "-1";
	private int selectedIndex = -1;
	private Cursor cursor;
	private RingtoneManager ringtoneManager;
	private Ringtone selectedRingtone;

	/**
	 * Creates a ringtone selector dialog similar to Android's RingtonePreference
	 * @param title Title shown on top of the dialog
	 * @param ringtoneType Type of ringtone as defined in {@link RingtoneManager}
	 * @param existingUri Uri pointing to the currently selected ringtone.
	 * @param defaultUri Uri pointing to a ringtone that will be marked as "default". If null, the system's default ringtone for {@param ringtoneType} will be used
	 * @param showDefault Show a selection for the default ringtone
	 * @param showSilent Show a selection for a silent ringtone
	 * @return RingtoneSelectorDialog
	 */
	public static RingtoneSelectorDialog newInstance(String title, int ringtoneType, Uri existingUri, Uri defaultUri, boolean showDefault, boolean showSilent) {
		RingtoneSelectorDialog dialog = new RingtoneSelectorDialog();
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putInt("type", ringtoneType);
		args.putParcelable("existingUri", existingUri);
		args.putParcelable("defaultUri", defaultUri);
		args.putBoolean("showDefault", showDefault);
		args.putBoolean("showSilent", showSilent);

		dialog.setArguments(args);
		return dialog;
	}

	public interface RingtoneSelectorDialogClickListener {
		void onRingtoneSelected(String tag, Uri ringtone);

		void onCancel(String tag);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			callback = (RingtoneSelectorDialog.RingtoneSelectorDialogClickListener) getTargetFragment();
		} catch (ClassCastException e) {
			//
		}

		// called from an activity rather than a fragment
		if (callback == null) {
			if (!(activity instanceof RingtoneSelectorDialog.RingtoneSelectorDialogClickListener)) {
				throw new ClassCastException("Calling fragment must implement RingtoneSelectorDialogClickListener interface");
			}
			callback = (RingtoneSelectorDialog.RingtoneSelectorDialogClickListener) activity;
		}
	}

	@Override
	public void onCancel(DialogInterface dialogInterface) {
		super.onCancel(dialogInterface);

		callback.onCancel(this.getTag());
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		String title = getArguments().getString("title");
		final int ringtoneType = getArguments().getInt("type");
		defaultUri = getArguments().getParcelable("defaultUri");
		final Uri existingUri = getArguments().getParcelable("existingUri");
		final boolean showDefault = getArguments().getBoolean("showDefault");
		final boolean showSilent = getArguments().getBoolean("showSilent");

		if (showDefault && defaultUri == null) {
			// get default URI from system if none is provided by caller
			defaultUri = RingtoneManager.getDefaultUri(ringtoneType);
		}

		selectedRingtoneUri = existingUri;

		cursor = createCursor(existingUri, ringtoneType, defaultUri, showDefault, showSilent);

		final String tag = this.getTag();

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
		if (title != null) {
			builder.setTitle(title);
		}

		String[] labels = new String[cursor.getCount()];

		if (cursor.moveToFirst()) {
			do {
				labels[cursor.getPosition()] = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
			} while (cursor.moveToNext());
		}

		final TypedArray a = getContext().obtainStyledAttributes(null, androidx.appcompat.R.styleable.AlertDialog,
				androidx.appcompat.R.attr.alertDialogStyle, 0);
		int itemLayout = a.getResourceId(com.google.android.material.R.styleable.AlertDialog_singleChoiceItemLayout, 0);
		RingtoneListItemAdapter adapter = new RingtoneListItemAdapter(getContext(), itemLayout, android.R.id.text1, labels);

		builder
				.setSingleChoiceItems(adapter, selectedIndex, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						if (i < adapter.getCount()) {
							selectedIndex = i;

							stopPlaying();

							selectedRingtoneUri = getUriFromPosition(selectedIndex, showSilent, showDefault);
							if (selectedRingtoneUri == null) {
								ringtoneManager.stopPreviousRingtone(); // "playing" silence
							} else {
								selectedRingtone = RingtoneManager.getRingtone(getContext(), selectedRingtoneUri);
								if (selectedRingtone != null) {
									try {
										selectedRingtone.play();
									} catch (Exception e) {
										// this may cause java.lang.NullPointerException: Attempt to invoke virtual method 'android.net.Uri android.net.Uri.getCanonicalUri()' on a null object reference
										// on some HTC devices
										Toast.makeText(getContext(), "Unable to play ringtone " + selectedRingtoneUri.toString(), Toast.LENGTH_LONG).show();
										logger.debug("Unable to play ringtone " + selectedRingtoneUri.toString());
										logger.error("Exception", e);

										ringtoneManager.stopPreviousRingtone();
									}
								}
							}
						}
					}
				})
				.setOnDismissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialogInterface) {
						stopPlaying();
						callback.onCancel(tag);
					}
				})
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						stopPlaying();
						callback.onCancel(tag);

					}
				})
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						stopPlaying();
						callback.onRingtoneSelected(tag, selectedRingtoneUri);
					}
				});

		alertDialog = builder.create();

		return alertDialog;
	}

	@Override
	public void onPause() {
		super.onPause();

		stopPlaying();
	}

	private void stopPlaying() {
		if (selectedRingtone != null && selectedRingtone.isPlaying()) {
			selectedRingtone.stop();
		}

		if (ringtoneManager != null) {
			ringtoneManager.stopPreviousRingtone();
		}
	}

	@NonNull
	private Cursor createCursor(Uri existingUri, int ringtoneType, Uri defaultUri, boolean showDefault, boolean showSilent) {
		ringtoneManager = new RingtoneManager(getContext());
		ringtoneManager.setType(ringtoneType);
		ringtoneManager.setStopPreviousRingtone(true);

		Cursor ringtoneCursor = ringtoneManager.getCursor();

		String colId = ringtoneCursor.getColumnName(RingtoneManager.ID_COLUMN_INDEX);
		String colTitle = ringtoneCursor.getColumnName(RingtoneManager.TITLE_COLUMN_INDEX);

		try (MatrixCursor extras = new MatrixCursor(new String[]{colId, colTitle})) {

			if (showSilent) {
				extras.addRow(new String[]{CURSOR_NONE_ID, getString(R.string.ringtone_none)});
			}

			if (showDefault) {
				String defaultUriString = RingtoneUtil.getRingtoneNameFromUri(getContext(), defaultUri);
				// hack to prevent showing label for default uri twice
				if (!(defaultUriString.contains("(") && defaultUriString.contains(")"))) {
					defaultUriString = String.format(getString(R.string.ringtone_selection_default), defaultUriString);
				}
				extras.addRow(new String[]{CURSOR_DEFAULT_ID, defaultUriString});
			}

			if (showSilent && existingUri != null && existingUri.toString().equals("")) {
				// silent default
				selectedIndex = 0;
			} else {
				try {
					selectedIndex = ringtoneManager.getRingtonePosition(existingUri);
				} catch (Exception e) {
					logger.error("Exception", e);
					selectedIndex = 0;
				}

				if (selectedIndex >= 0) {
					selectedIndex += (showDefault ? 1 : 0) + (showSilent ? 1 : 0);
				}

				if (selectedIndex < 0 && showDefault) {
					selectedIndex = showSilent ? 1 : 0;
				}

				if (selectedIndex < 0 && showSilent) {
					selectedIndex = 0;
				}
			}

			// get uri for initial selection
			selectedRingtoneUri = getUriFromPosition(selectedIndex, showSilent, showDefault);

			Cursor[] cursors = {extras, ringtoneCursor};
			return this.cursor = new MergeCursor(cursors);
		}
	}

	private static class RingtoneListItemAdapter extends ArrayAdapter<CharSequence> {
		RingtoneListItemAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull CharSequence[] objects) {
			super(context, resource, textViewResourceId, objects);
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}
	}

	private @Nullable Uri getUriFromPosition(int index, boolean showSilent, boolean showDefault) {
		int positionFix = 0;

		if (showSilent) {
			if (index == 0) {
				// silent
				return null;
			}
			positionFix += 1;
		}

		if (showDefault) {
			if ((showSilent && index == 1) || index == 0) {
				// "default" ringtone
				selectedRingtone = RingtoneManager.getRingtone(getContext(), defaultUri);
				return defaultUri;
			}
			positionFix += 1;
		}

		Uri uri = null;
		try {
			uri = ringtoneManager.getRingtoneUri(index - positionFix);
		} catch (Exception e) {
			logger.error("Buggy Ringtone Manager", e);
		}
		return uri;
	}

	@Override
	public void onDestroy() {
		if (this.cursor != null && !this.cursor.isClosed()) {
			this.cursor.close();
		}

		super.onDestroy();
	}
}
