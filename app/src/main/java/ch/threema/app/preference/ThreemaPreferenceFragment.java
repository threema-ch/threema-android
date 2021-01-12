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

package ch.threema.app.preference;

import android.content.Context;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;
import ch.threema.app.utils.ConfigUtils;


public abstract class ThreemaPreferenceFragment extends PreferenceFragmentCompat {

	protected PreferenceFragmentCallbackInterface preferenceFragmentCallbackInterface;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

       try {
            preferenceFragmentCallbackInterface = (PreferenceFragmentCallbackInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement PreferenceFragmentCallbackInterface ");
        }
	}

	@Override
	public void addPreferencesFromResource(@XmlRes int preferencesResId) {
		super.addPreferencesFromResource(preferencesResId);

		ConfigUtils.tintPreferencesIcons(getContext(), getPreferenceScreen());
	}

	public interface PreferenceFragmentCallbackInterface {
		void setToolbarTitle(@StringRes int stringRes);
	}
}
