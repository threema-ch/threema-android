/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

public class BottomSheetListDialog extends BottomSheetAbstractDialog {
	public static BottomSheetListDialog newInstance(@StringRes int title, ArrayList<BottomSheetItem> items, int selected) {
		BottomSheetListDialog dialog = new BottomSheetListDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putInt("selected", selected);
		args.putParcelableArrayList("items", items);
		dialog.setArguments(args);
		return dialog;
	}


	public static BottomSheetListDialog newInstance(@StringRes int title, ArrayList<BottomSheetItem> items, int selected, BottomSheetDialogInlineClickListener listener) {
		// do not use inline callbacks in activities that don't have android:configChanges="orientation|screenSize|keyboardHidden" set
		// or fragments without setRetainInstance(true)
		BottomSheetListDialog dialog = new BottomSheetListDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putInt("selected", selected);
		args.putParcelableArrayList("items", items);
		args.putParcelable("listener", listener);

		dialog.setArguments(args);
		return dialog;
	}
}
