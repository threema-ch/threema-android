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
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.DialogFragment;
import ch.threema.app.R;

public class WizardRestoreSelectorDialog extends DialogFragment {

	private WizardRestoreSelectorDialogCallback callback;
	private Activity activity;

	public static WizardRestoreSelectorDialog newInstance() {
		return new WizardRestoreSelectorDialog();
	}

	public interface WizardRestoreSelectorDialogCallback {
		void onNo(String tag);
		void onDataBackupRestore();
		void onIdBackupRestore();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			callback = (WizardRestoreSelectorDialogCallback) getTargetFragment();
		} catch (ClassCastException e) {
			//
		}

		// called from an activity rather than a fragment
		if (callback == null) {
			if (!(activity instanceof WizardRestoreSelectorDialogCallback)) {
				throw new ClassCastException("Calling fragment must implement WizardRestoreSelectorDialogCallback interface");
			}
			callback = (WizardRestoreSelectorDialogCallback) activity;
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		final String tag = this.getTag();

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_wizard_restore_selector, null);

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), R.style.Threema_Dialog_Wizard);
		builder.setView(dialogView);

		dialogView.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
				callback.onNo(tag);
			}
		});

		dialogView.findViewById(R.id.id_backup).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
				callback.onIdBackupRestore();
			}
		});

		dialogView.findViewById(R.id.data_backup).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
				callback.onDataBackupRestore();
			}
		});

		return builder.create();
	}

	@Override
	public void onCancel(DialogInterface dialogInterface) {
		callback.onNo(this.getTag());
	}
}
