/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;

import com.google.android.material.appbar.AppBarLayout;

import org.slf4j.Logger;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.wizard.WizardBaseActivity;
import ch.threema.app.dialogs.WizardDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public abstract class WizardFragment extends Fragment {
	private static final Logger logger = LoggingUtil.getThreemaLogger("WizardFragment");

	private static final String DIALOG_TAG_ADDITIONAL_INFO = "ai";

	protected PreferenceService preferenceService;
	protected UserService userService;
	protected LocaleService localeService;
	protected ViewStub contentViewStub;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (!requiredInstances()) {
			requireActivity().finish();
		}
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(
		LayoutInflater inflater,
		ViewGroup container,
		Bundle savedInstanceState
	) {
		View rootView = inflater.inflate(R.layout.fragment_wizard, container, false);

		contentViewStub = rootView.findViewById(R.id.stub_content);

		ImageView infoIcon = rootView.findViewById(R.id.wizard_icon_info);
		infoIcon.setOnClickListener(v -> showAdditionalInfo());

		return rootView;
	}

	private void showAdditionalInfo() {
		int infoStringRes = getAdditionalInfoText();
		if (infoStringRes != 0) {
			WizardDialog wizardDialog = WizardDialog.newInstance(infoStringRes, R.string.ok);
			wizardDialog.show(getParentFragmentManager(), DIALOG_TAG_ADDITIONAL_INFO);
		}
	}

	private boolean requiredInstances() {
		if (!this.checkInstances()) {
			this.instantiate();
		}
		return this.checkInstances();
	}

	private boolean checkInstances() {
		return TestUtil.required(
			this.preferenceService,
			this.userService,
			this.localeService
		);
	}

	private void instantiate() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			this.preferenceService = serviceManager.getPreferenceService();
			try {
				this.userService = serviceManager.getUserService();
				this.localeService = serviceManager.getLocaleService();
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}

	protected void setPage(int page) {
		((WizardBaseActivity) requireActivity()).setPage(page);
	}

	protected abstract @StringRes int getAdditionalInfoText();
}
