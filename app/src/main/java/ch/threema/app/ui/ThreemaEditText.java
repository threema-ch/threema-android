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

package ch.threema.app.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.AttributeSet;

import com.google.android.material.textfield.TextInputEditText;

import androidx.preference.PreferenceManager;
import ch.threema.app.R;

public class ThreemaEditText extends TextInputEditText {

	public ThreemaEditText(Context context) {
		super(context);

		init(context);
	}

	public ThreemaEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		init(context);
	}

	public ThreemaEditText(Context context, AttributeSet attrs) {
		super(context, attrs);

		init(context);
	}

	private void init(Context context) {
		// PreferenceService may not yet be available at this time
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		if (sharedPreferences != null && sharedPreferences.getBoolean(getResources().getString(R.string.preferences__incognito_keyboard), false)) {
			setImeOptions(getImeOptions() | 0x1000000);
		}
	}

	@Override
	public boolean onTextContextMenuItem(int id) {
		if (id == android.R.id.paste) {
			/* Hack to prevent rich text pasting */
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				id = android.R.id.pasteAsPlainText;
			}
		}
		return super.onTextContextMenuItem(id);
	}

	@Override
	@TargetApi(Build.VERSION_CODES.O)
	public int getAutofillType() {
		// disable Autofill in EditText due to privacy and TransactionTooLargeException as well as bug https://issuetracker.google.com/issues/67675432
		return AUTOFILL_TYPE_NONE;
	}

	@Override
	public void dispatchWindowFocusChanged(boolean hasFocus) {
		try {
			super.dispatchWindowFocusChanged(hasFocus);
		} catch (Exception ignore) {
			// catch Security Exception in com.samsung.android.content.clipboard.SemClipboardManager.getLatestClip() on Samsung devices
		}
	}
}
