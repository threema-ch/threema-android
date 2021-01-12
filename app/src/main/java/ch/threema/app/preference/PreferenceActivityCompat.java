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

/*
 * Copyright (c) 2018 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package ch.threema.app.preference;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import ch.threema.app.R;

/**
 * @author [大前良介 (OHMAE Ryosuke)](mailto:ryo@mm2d.net)
 */
@SuppressLint("Registered")
public class PreferenceActivityCompat extends AppCompatActivity implements
        PreferenceActivityCompatDelegate.Connector,
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
	private static final Logger logger = LoggerFactory.getLogger(PreferenceActivityCompat.class);

	private PreferenceActivityCompatDelegate mDelegate;

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDelegate = new PreferenceActivityCompatDelegate(this, this);
        mDelegate.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        mDelegate.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
    	try {
		    super.onSaveInstanceState(outState);
	    } catch (IllegalStateException e) {
		    logger.error("Exception", e);
	    }
        mDelegate.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle state) {
        super.onRestoreInstanceState(state);
        mDelegate.onRestoreInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (mDelegate.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onIsMultiPane() {
        return getResources().getBoolean(R.bool.tablet_layout);
    }

    @Override
    public void onBuildHeaders(@NonNull final List<Header> target) {
    }

    @Override
    public boolean isValidFragment(@Nullable final String fragmentName) {
	    throw new RuntimeException(
	            "Subclasses of PreferenceActivity must override isValidFragment(String)"
	                    + " to verify that the Fragment class is valid! "
	                    + getClass().getName()
	                    + " has not checked if fragment " + fragmentName + " is valid.");
    }

    public int getSelectedItemPosition() {
        return mDelegate.getSelectedItemPosition();
    }

    public boolean hasHeaders() {
        return mDelegate.hasHeaders();
    }

    @NonNull
    public List<Header> getHeaders() {
        return mDelegate.getHeaders();
    }

    public boolean isMultiPane() {
        return mDelegate.isMultiPane();
    }

    public void invalidateHeaders() {
        mDelegate.invalidateHeaders();
    }

    public void loadHeadersFromResource(
            @XmlRes final int resId,
            @NonNull final List<Header> target) {
        mDelegate.loadHeadersFromResource(resId, target);
    }

    public void setListFooter(@NonNull final View view) {
        mDelegate.setListFooter(view);
    }

    public void switchToHeader(@NonNull final Header header) {
        mDelegate.switchToHeader(header);
    }

    @Override
    public boolean onPreferenceStartFragment(
            @NonNull final PreferenceFragmentCompat caller,
            @NonNull final Preference pref) {
        mDelegate.startPreferenceFragment(pref);
        return true;
    }
}
