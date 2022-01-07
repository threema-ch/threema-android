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

package ch.threema.app.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;

public class MultiChoiceSelectorDialog extends ThreemaDialogFragment {
	private SelectorDialogClickListener callback;
	private Activity activity;
	private AlertDialog alertDialog;

	public static MultiChoiceSelectorDialog newInstance(String title, String[] items, boolean[] checkedItems) {
		MultiChoiceSelectorDialog dialog = new MultiChoiceSelectorDialog();
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putStringArray("items", items);
		args.putBooleanArray("checked", checkedItems);

		dialog.setArguments(args);
		return dialog;
	}

	public interface SelectorDialogClickListener {
		void onYes(String tag, boolean[] checkedItems);
		void onCancel(String tag);
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialogInterface) {
		super.onCancel(dialogInterface);

		callback.onCancel(this.getTag());
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		String title = getArguments().getString("title");
		final String[] items = getArguments().getStringArray("items");
		final boolean[] checkedItems = getArguments().getBooleanArray("checked");

		final String tag = this.getTag();

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
		if (title != null) {
			builder.setTitle(title);
		}
		builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
			//
		});

		builder.setPositiveButton(getString(R.string.ok), (dialog, which) -> {
			dialog.dismiss();
			callback.onYes(tag, checkedItems);
		});

		builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
			dialog.dismiss();
			callback.onCancel(tag);
		});

		alertDialog = builder.create();

		return alertDialog;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			callback = (SelectorDialogClickListener) getTargetFragment();
		} catch (ClassCastException e) {
			//
		}

		// maybe called from an activity rather than a fragment
		if (callback == null) {
			if ((activity instanceof SelectorDialogClickListener)) {
				callback = (SelectorDialogClickListener) activity;
			}
		}
	}
}
