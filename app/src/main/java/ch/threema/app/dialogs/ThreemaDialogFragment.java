/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class ThreemaDialogFragment extends DialogFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaDialogFragment");

    protected @Nullable Object object;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        setRetainInstance(true);
    }

    /**
     * Shows a DialogFragment. Can be used from onActivityResult() without provoking "IllegalStateException: Can not perform this action after onSaveInstanceState"
     *
     * @param manager FragmentManager
     * @param tag     Arbitrary tag for this DialogFragment
     */
    @Override
    public void show(@Nullable FragmentManager manager, @Nullable String tag) {
        if (manager != null) {
            try {
                super.show(manager, tag);
            } catch (IllegalStateException e) {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, tag);
                ft.commitAllowingStateLoss();
            }
        }
    }

    /**
     * Shows a DialogFragment. Can be used from onActivityResult() without provoking "IllegalStateException: Can not perform this action after onSaveInstanceState"
     */
    public void show(@Nullable FragmentManager manager) {
        if (manager != null) {
            try {
                super.show(manager, null);
            } catch (IllegalStateException e) {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, null);
                ft.commitAllowingStateLoss();
            }
        }
    }

    /**
     * Immediately shows a DialogFragment. Can be used from onActivityResult() without provoking "IllegalStateException: Can not perform this action after onSaveInstanceState"
     *
     * @param manager FragmentManager
     * @param tag     Arbitrary tag for this DialogFragment
     */
    @Override
    public void showNow(@Nullable FragmentManager manager, String tag) {
        if (manager != null) {
            try {
                super.showNow(manager, tag);
            } catch (IllegalStateException e) {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, tag);
                ft.commitNowAllowingStateLoss();
            }
        }
    }

    public ThreemaDialogFragment setData(@NonNull Object data) {
        this.object = data;
        return this;
    }
}
