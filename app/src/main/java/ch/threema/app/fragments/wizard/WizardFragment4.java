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
import androidx.appcompat.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import ch.threema.app.R;
import ch.threema.app.activities.wizard.WizardBaseActivity;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.SynchronizeContactsUtil;

public class WizardFragment4 extends WizardFragment {
	private static final boolean defaultSwitchValue = WizardBaseActivity.DEFAULT_SYNC_CONTACTS;
	private SwitchCompat syncContactsSwitch;
	public static final int PAGE_ID = 4;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View rootView = super.onCreateView(inflater, container, savedInstanceState);

		WizardFragment5.SettingsInterface callback = (WizardFragment5.SettingsInterface) getActivity();

		// inflate content layout
		contentViewStub.setLayoutResource(R.layout.fragment_wizard4);
		contentViewStub.inflate();

		syncContactsSwitch = rootView.findViewById(R.id.wizard_switch_sync_contacts);

		if (SynchronizeContactsUtil.isRestrictedProfile(getActivity()) &&
				!ConfigUtils.isWorkRestricted()) {
			// restricted user profiles cannot add accounts
			syncContactsSwitch.setChecked(false);
			syncContactsSwitch.setEnabled(false);
			((OnSettingsChangedListener) getActivity()).onSyncContactsSet(false);
		} else {
			if (callback.isReadOnlyProfile()) {
				syncContactsSwitch.setEnabled(false);
				rootView.findViewById(R.id.disabled_by_policy).setVisibility(View.VISIBLE);
			} else {
				syncContactsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						((OnSettingsChangedListener) getActivity()).onSyncContactsSet(isChecked);
					}
				});
				syncContactsSwitch.setChecked(defaultSwitchValue);
				((OnSettingsChangedListener) getActivity()).onSyncContactsSet(defaultSwitchValue);
			}
		}

		return rootView;
	}

	@Override
	protected int getAdditionalInfoText() {
		return R.string.new_wizard_info_sync_contacts;
	}

	@Override
	public void onResume() {
		super.onResume();
		initValues();
	}

	void initValues() {
		if (isResumed() && ConfigUtils.isWorkRestricted()) {
			WizardFragment5.SettingsInterface callback = (WizardFragment5.SettingsInterface) getActivity();
			syncContactsSwitch.setChecked(callback.getSyncContacts());
		}
	}

	public interface OnSettingsChangedListener {
		void onSyncContactsSet(boolean enabled);
	}
}
