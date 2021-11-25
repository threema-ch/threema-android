/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.base.utils.Utils;

import static android.content.Context.CLIPBOARD_SERVICE;

public class PublicKeyDialog extends SimpleStringAlertDialog {
	private String publicKeyString;
	private CharSequence title;

	public static PublicKeyDialog newInstance(CharSequence title, byte[] publicKey) {
		PublicKeyDialog dialog = new PublicKeyDialog();
		Bundle args = new Bundle();
		args.putCharSequence("title", title);
		args.putByteArray("publicKey", publicKey);

		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, @Nullable ContextMenu.ContextMenuInfo menuInfo) {
		MenuItem menuItem = menu.add(0, v.getId(), 0, getContext().getString(R.string.copy));
		menuItem.setOnMenuItemClickListener(item -> {
			ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(CLIPBOARD_SERVICE);

			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager != null) {
				ClipData clip = ClipData.newPlainText(title, publicKeyString);
				clipboard.setPrimaryClip(clip);
			}

			Toast.makeText(getContext(), R.string.copied, Toast.LENGTH_SHORT).show();

			return true;
		});
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		title = getArguments().getCharSequence("title");
		byte[] publicKey = getArguments().getByteArray("publicKey");
		publicKeyString = Utils.byteArrayToHexString(publicKey);

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_public_key, null);
		final TextView messageView = dialogView.findViewById(R.id.message);

		registerForContextMenu(messageView);

		StringBuilder message = new StringBuilder();

		for(int i = 0; i < publicKeyString.length(); i++) {
			if (i != 0 && i % 8 == 0) {
				message.append("\n");
			}
			message.append(publicKeyString.charAt(i));
		}

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme())
			.setCancelable(false)
			.setView(dialogView)
			.setTitle(title)
			.setPositiveButton(getString(R.string.ok), null)
			.setCancelable(false);

		messageView.setText(message);

		return builder.create();
	}
}
