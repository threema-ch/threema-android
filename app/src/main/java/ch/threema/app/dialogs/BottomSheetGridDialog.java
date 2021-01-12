/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

import android.os.Bundle;

import java.util.ArrayList;

import androidx.annotation.StringRes;
import ch.threema.app.ui.BottomSheetItem;

public class BottomSheetGridDialog extends BottomSheetAbstractDialog {
	public static BottomSheetGridDialog newInstance(@StringRes int title, ArrayList<BottomSheetItem> items) {
		BottomSheetGridDialog dialog = new BottomSheetGridDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putParcelableArrayList("items", items);
		dialog.setArguments(args);
		return dialog;
	}

	/* Hack to prevent TransactionTooLargeException when hosting activity goes into the background */
	@Override
	public void onPause() {
		dismiss();

		super.onPause();
	}
}
