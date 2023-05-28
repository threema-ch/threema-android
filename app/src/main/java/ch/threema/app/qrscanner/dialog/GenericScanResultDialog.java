/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

package ch.threema.app.qrscanner.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import ch.threema.app.R;
import ch.threema.app.dialogs.ThreemaDialogFragment;
import ch.threema.app.emojis.EmojiConversationTextView;

public class GenericScanResultDialog extends ThreemaDialogFragment {

	public static final String EXTRA_SCAN_RESULT = "scan_result";

	public static GenericScanResultDialog newInstance(String scanResult) {
		GenericScanResultDialog dialog = new GenericScanResultDialog();
		Bundle args = new Bundle();
		args.putString(EXTRA_SCAN_RESULT, scanResult);
		dialog.setArguments(args);
		return dialog;
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		if (getArguments() == null) {
			return returnAnErrorOccuredDialog("No dialog arguments found");
		}

		final String result = getArguments().getString(EXTRA_SCAN_RESULT);

		final View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_scan_result, null);
		final EmojiConversationTextView resultView = dialogView.findViewById(R.id.scan_result);
		final AppCompatImageButton copyButton = dialogView.findViewById(R.id.copy);
		final AppCompatImageButton shareButton = dialogView.findViewById(R.id.share);

		GenericScanResultDialog.ScanResultClickListener callback = (GenericScanResultDialog.ScanResultClickListener) getActivity();

		if (callback == null) {
			return returnAnErrorOccuredDialog("Callback not found, does the calling activity implements GenericScanResultDialog click listener?");
		}

		resultView.setText(result);
		copyButton.setOnClickListener(v -> callback.onCopy(result));
		shareButton.setOnClickListener(v -> callback.onShare(result));

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
		builder
			.setView(dialogView)
			.setTitle(getString(R.string.qr_scan_result_dialog_title))
			.setPositiveButton(getString(R.string.close), (dialog, whichButton) -> {
				callback.onClose();
				dismiss();
			});

		return builder.create();
	}

	private AppCompatDialog returnAnErrorOccuredDialog(String errorMessage) {
		return new MaterialAlertDialogBuilder(getActivity())
			.setTitle(R.string.error)
			.setMessage(String.format(getString(R.string.an_error_occurred_more), errorMessage))
			.setPositiveButton(R.string.ok, null)
			.create();
	}

	public interface ScanResultClickListener {
		void onCopy(String result);
		void onShare(String result);
		void onClose();
	}
}
