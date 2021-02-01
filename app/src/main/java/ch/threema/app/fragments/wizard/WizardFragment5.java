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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import ch.threema.app.R;
import ch.threema.app.dialogs.WizardDialog;
import ch.threema.app.threemasafe.ThreemaSafeServerInfo;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;

import static ch.threema.app.ThreemaApplication.EMAIL_LINKED_PLACEHOLDER;
import static ch.threema.app.ThreemaApplication.PHONE_LINKED_PLACEHOLDER;

public class WizardFragment5 extends WizardFragment implements View.OnClickListener {
	private TextView nicknameText, phoneText, emailText, syncContactsText, phoneWarnText, emailWarnText, safeText;
	private ImageView phoneWarn, emailWarn;
	private ProgressBar phoneProgress, emailProgress, syncContactsProgress, safeProgress;
	private Button finishButton;
	private SettingsInterface callback;
	public static final int PAGE_ID = 5;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_wizard5, container, false);

		nicknameText = rootView.findViewById(R.id.wizard_nickname_preset);
		phoneText = rootView.findViewById(R.id.wizard_phone_preset);
		emailText = rootView.findViewById(R.id.wizard_email_preset);
		syncContactsText = rootView.findViewById(R.id.sync_contacts_preset);
		syncContactsProgress = rootView.findViewById(R.id.wizard_contact_sync_progress);
		phoneProgress = rootView.findViewById(R.id.wizard_phone_progress);
		emailProgress = rootView.findViewById(R.id.wizard_email_progress);
		phoneWarn = rootView.findViewById(R.id.wizard_phone_warn);
		emailWarn = rootView.findViewById(R.id.wizard_email_warn);
		phoneWarnText = rootView.findViewById(R.id.wizard_phone_error_text);
		emailWarnText = rootView.findViewById(R.id.wizard_email_error_text);
		safeText = rootView.findViewById(R.id.threema_safe_preset);
		safeProgress = rootView.findViewById(R.id.threema_safe_progress);

		finishButton = rootView.findViewById(R.id.wizard_finish);
		finishButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				phoneText.setClickable(false);
				emailText.setClickable(false);
				callback.onWizardFinished(WizardFragment5.this, finishButton);
				phoneText.setClickable(true);
				emailText.setClickable(true);
			}
		});

		emailText.setOnClickListener(this);
		phoneText.setOnClickListener(this);
		emailWarnText.setOnClickListener(this);
		phoneWarnText.setOnClickListener(this);

		if (!ConfigUtils.isWorkBuild()) {
			rootView.findViewById(R.id.wizard_email_layout).setVisibility(View.GONE);
			rootView.findViewById(R.id.wizard_email_error_layout).setVisibility(View.GONE);
		}

		return rootView;
	}

	@Override
	protected int getAdditionalInfoText() {
		return 0;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		callback = (SettingsInterface) activity;
	}

	void initValues() {
		if (isResumed()) {
			String email = callback.getEmail();
			String phone = callback.getPhone();

			nicknameText.setText(callback.getNickname());
			emailText.setText(TestUtil.empty(email) ? getString(R.string.not_linked) : EMAIL_LINKED_PLACEHOLDER.equals(email) ? getString(R.string.unchanged) : email);
			phoneText.setText(TestUtil.empty(phone) ? getString(R.string.not_linked) : PHONE_LINKED_PLACEHOLDER.equals(phone) ? getString(R.string.unchanged) : phone);
			syncContactsText.setText(callback.getSyncContacts() ? R.string.on : R.string.off);
			setThreemaSafeInProgress(false, null);
		}
	}

	@Override
	@SuppressLint("NewApi")
	public void onResume () {
		super.onResume();

		initValues();

		if (ConfigUtils.isWorkRestricted() && callback.isSkipWizard()) {
			finishButton.callOnClick();
		}
	}

	@Override
	public void onClick(View v) {
		switch(v.getId()) {
			case R.id.wizard_phone_error_text:
				WizardDialog.newInstance(phoneWarnText.getText().toString(), R.string.ok).show(getFragmentManager(),"ph");
				break;
			case R.id.wizard_email_error_text:
				WizardDialog.newInstance(emailWarnText.getText().toString(), R.string.ok).show(getFragmentManager(), "em");
				break;
			case R.id.wizard_email_preset:
			case R.id.wizard_phone_preset:
				setPage(2);
				break;
			default:
				break;
		}
	}

	public void setMobileLinkingInProgress(boolean inProgress) {
		phoneWarn.setVisibility(View.GONE);
		phoneProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
		phoneWarnText.setVisibility(View.GONE);
		phoneText.setVisibility(inProgress ? View.GONE : View.VISIBLE);
	}

	public void setEmailLinkingInProgress(boolean inProgress) {
		emailWarn.setVisibility(View.GONE);
		emailProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
		emailWarnText.setVisibility(View.GONE);
		emailText.setVisibility(inProgress ? View.GONE : View.VISIBLE);
	}

	public void setMobileLinkingAlert(String message) {
		phoneWarn.setVisibility(View.VISIBLE);
		phoneWarnText.setText(message);
		phoneWarnText.setVisibility(View.VISIBLE);
		phoneProgress.setVisibility(View.GONE);
		phoneText.setVisibility(View.VISIBLE);
	}

	public void setEmailLinkingAlert(String message) {
		emailWarn.setVisibility(View.VISIBLE);
		emailWarnText.setText(message);
		emailWarnText.setVisibility(View.VISIBLE);
		emailProgress.setVisibility(View.GONE);
		emailText.setVisibility(View.VISIBLE);
	}

	public void setContactsSyncInProgress(boolean inProgress, String text) {
		syncContactsProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
		if (TestUtil.empty(text)) {
			syncContactsText.setText(callback.getSyncContacts() ? R.string.on : R.string.off);
		} else {
			syncContactsText.setText(text);
		}
	}

	public void setThreemaSafeInProgress(boolean inProgress, String text) {
		safeProgress.setVisibility(inProgress ? View.VISIBLE : View.GONE);
		if (TestUtil.empty(text)) {
			if (TestUtil.empty(callback.getSafePassword())) {
				safeText.setText(R.string.off);
			} else {
				if (callback.getSafeServerInfo().isDefaultServer()) {
					safeText.setText(getString(R.string.on));
				} else {
					safeText.setText(String.format("%s - %s", getString(R.string.on), callback.getSafeServerInfo().getHostName()));
				}
			}
		} else {
			safeText.setText(text);
		}
	}

	public interface SettingsInterface {
		String getNickname();
		String getPhone();
		String getPrefix();
		String getNumber();
		String getEmail();
		String getPresetPhone();
		String getPresetEmail();
		boolean getSafeForcePasswordEntry();
		boolean getSafeSkipBackupPasswordEntry();
		boolean getSafeDisabled();
		String getSafePassword();
		ThreemaSafeServerInfo getSafeServerInfo();
		boolean getSyncContacts();
		boolean isReadOnlyProfile();
		boolean isSkipWizard();
		void onWizardFinished(WizardFragment5 fragment, Button finishButton);
	}
}
