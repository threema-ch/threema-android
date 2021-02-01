/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.app.fragments.wizard;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import ch.threema.app.R;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;

public class WizardFragment2 extends WizardFragment {
	private EditText nicknameText;
	public static final int PAGE_ID = 2;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View rootView = super.onCreateView(inflater, container, savedInstanceState);

		WizardFragment5.SettingsInterface callback = (WizardFragment5.SettingsInterface) getActivity();

		// inflate content layout
		contentViewStub.setLayoutResource(R.layout.fragment_wizard2);
		contentViewStub.inflate();

		nicknameText = rootView.findViewById(R.id.wizard_edit1);
		if (callback.isReadOnlyProfile()) {
			nicknameText.setEnabled(false);
			rootView.findViewById(R.id.disabled_by_policy).setVisibility(View.VISIBLE);
		} else {
			nicknameText.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					if (getActivity().getCurrentFocus() == nicknameText) {
						((OnSettingsChangedListener) getActivity()).onNicknameSet(s.toString());
					}
				}
			});
		}

		return rootView;
	}

	@Override
	protected int getAdditionalInfoText() {
		return R.string.new_wizard_info_nickname;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	public interface OnSettingsChangedListener {
        void onNicknameSet(String nickname);
    }

	@Override
	public void onResume() {
		super.onResume();
		new Handler().postDelayed(() -> RuntimeUtil.runOnUiThread(this::initValues), 50);
		if (this.nicknameText != null) {
			this.nicknameText.requestFocus();
			EditTextUtil.showSoftKeyboard(this.nicknameText);
		}
	}

	@Override
	public void onPause() {
		if (this.nicknameText != null) {
			this.nicknameText.clearFocus();
			EditTextUtil.hideSoftKeyboard(this.nicknameText);
		}
		super.onPause();
	}

	private void initValues() {
		if (isResumed()) {
			WizardFragment5.SettingsInterface callback = (WizardFragment5.SettingsInterface) getActivity();
			String nickname = callback.getNickname();
			nicknameText.setText(nickname);
			if (!TestUtil.empty(nickname)) {
				nicknameText.setSelection(nickname.length());
			}
		}
	}
}
