/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

package ch.threema.app.preference;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.RateDialog;

import static ch.threema.app.ThreemaApplication.getAppContext;

public class SettingsRateFragment extends ThreemaPreferenceFragment implements RateDialog.RateDialogClickListener, GenericAlertDialog.DialogClickListener {

	private static final String DIALOG_TAG_RATE = "rate";
	private static final String DIALOG_TAG_RATE_ON_GOOGLE_PLAY = "ratep";

	@Override
	protected void initializePreferences() {
		RateDialog rateDialog = RateDialog.newInstance(getString(R.string.rate_title));
		rateDialog.setTargetFragment(SettingsRateFragment.this, 0);
		rateDialog.show(getParentFragmentManager(), DIALOG_TAG_RATE);
	}

	private boolean startRating(Uri uri) throws ActivityNotFoundException {
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
		try {
			startActivity(intent);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	@Override
	public void onYes(String tag, final int rating, final String text) {
		if (rating >= 4 && (BuildFlavor.getLicenseType() == BuildFlavor.LicenseType.GOOGLE || BuildFlavor.getLicenseType() == BuildFlavor.LicenseType.NONE)) {
			GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.rate_title,
					getString(R.string.rate_thank_you) + " " +
							getString(R.string.rate_forward_to_play_store) ,
					R.string.yes,
					R.string.no);
			dialog.setTargetFragment(this);
			dialog.show(getParentFragmentManager(), DIALOG_TAG_RATE_ON_GOOGLE_PLAY);
		} else {
			Toast.makeText(getAppContext(), getString(R.string.rate_thank_you), Toast.LENGTH_LONG).show();
			requireActivity().onBackPressed();
		}
	}

	@Override
	public void onYes(String tag, Object data) {
		if (DIALOG_TAG_RATE_ON_GOOGLE_PLAY.equals(tag)) {
			if (!startRating(Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID))) {
				startRating(Uri.parse("https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID));
			}
			requireActivity().onBackPressed();
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		requireActivity().onBackPressed();
	}

	@Override
	public void onCancel(String tag) {
		requireActivity().onBackPressed();
	}

	@Override
	public int getPreferenceTitleResource() {
		return R.string.rate_title;
	}

	@Override
	public int getPreferenceResource() {
		return R.xml.preference_rate;
	}
}
