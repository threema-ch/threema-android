/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.wizard.WizardBaseActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.TestUtil;

public abstract class WizardFragment extends Fragment {
	private static final Logger logger = LoggerFactory.getLogger(WizardFragment.class);

	protected PreferenceService preferenceService;
	protected UserService userService;
	protected LocaleService localeService;
	protected AppBarLayout appBarLayout;
	protected ViewStub contentViewStub;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (!requiredInstances()) {
			getActivity().finish();
		}
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_wizard, container, false);

		contentViewStub = rootView.findViewById(R.id.stub_content);
		appBarLayout = rootView.findViewById(R.id.appbar_layout);

		LinearLayout moreInfoTabLayout = rootView.findViewById(R.id.more_info_tab);
		moreInfoTabLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				appBarLayout.setExpanded(appBarLayout.getTop() != 0, false);
			}
		});

		Button infoDoneButton = rootView.findViewById(R.id.wizard_info_done);
		infoDoneButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				appBarLayout.setExpanded(true, false);
			}
		});

		TextView infoText = rootView.findViewById(R.id.wizard_more_info_text);
		int infoStringRes = getAdditionalInfoText();
		if (infoStringRes != 0) {
			infoText.setText(getAdditionalInfoText());
		}

		return rootView;
	}

	private boolean requiredInstances() {
		if(!this.checkInstances()) {
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
		((WizardBaseActivity) getActivity()).setPage(page);
	}
	protected abstract @StringRes int getAdditionalInfoText();
}
