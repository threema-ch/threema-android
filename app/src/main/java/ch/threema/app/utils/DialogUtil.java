/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.base.utils.LoggingUtil;

public abstract class DialogUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("DialogUtil");

    public static void dismissDialog(@Nullable FragmentManager fragmentManager, String tag, boolean allowStateLoss) {
        logger.debug("dismissDialog: {}", tag);

        if (fragmentManager == null) {
            return;
        }

        DialogFragment dialogFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);

        if (dialogFragment == null && !fragmentManager.isDestroyed()) {
            // make sure dialogfragment is really shown before removing it
            try {
                fragmentManager.executePendingTransactions();
            } catch (IllegalStateException e) {
                // catch illegal state exception
            }
            dialogFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);
        }

        if (dialogFragment != null) {
            if (allowStateLoss) {
                try {
                    dialogFragment.dismissAllowingStateLoss();
                } catch (Exception e) {
                    // catch illegal state exception
                }
            } else {
                try {
                    dialogFragment.dismiss();
                } catch (Exception e) {
                    // catch illegal state exception
                }
            }
        }
    }

    @UiThread
    public static void updateProgress(FragmentManager fragmentManager, String tag, int progress) {
        if (fragmentManager != null) {
            DialogFragment dialogFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);
            if (dialogFragment instanceof CancelableHorizontalProgressDialog) {
                CancelableHorizontalProgressDialog progressDialog = (CancelableHorizontalProgressDialog) dialogFragment;
                progressDialog.setProgress(progress);
            }
        }
    }

    @UiThread
    public static void updateMessage(FragmentManager fragmentManager, String tag, String message) {
        if (fragmentManager != null) {
            DialogFragment dialogFragment = (DialogFragment) fragmentManager.findFragmentByTag(tag);
            if (dialogFragment instanceof GenericProgressDialog) {
                GenericProgressDialog progressDialog = (GenericProgressDialog) dialogFragment;
                progressDialog.setMessage(message);
            }
        }
    }

    public static ColorStateList getButtonColorStateList(Context context) {
        // Fix for appcompat bug. Set button text color from theme
        TypedArray a = context.getTheme().obtainStyledAttributes(new int[]{R.attr.colorPrimary});
        int accentColor = a.getColor(0, 0);
        a.recycle();

        // you can't have attrs in xml colorstatelists :-(
        ColorStateList colorStateList = new ColorStateList(
            new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{}
            },
            new int[]{
                context.getResources().getColor(R.color.material_grey_400),
                accentColor,
            }
        );

        return colorStateList;
    }
}
