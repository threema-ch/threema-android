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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

/**
 * A simple string dialog with a "don't show again" checkbox
 * If the checkbox has not previously been checked, the dialog will be shown, otherwise nothing will happen
 * Make sure to use a unique tag for this dialog in the show() method
 */
public class ShowOnceDialog extends ThreemaDialogFragment {
    private static final Logger logger = getThreemaLogger("ShowOnceDialog");

    private Activity activity;

    private static final String PREF_PREFIX = "dialog_";

    public static final String ARG_TITLE = "title";
    public static final String ARG_MESSAGE_STRING = "messageString";
    public static final String ARG_MESSAGE_INT = "messageInt";
    public static final String ARG_ICON = "icon";

    private ShowOnceDialog.ShowOnceDialogClickListener callback;

    public static ShowOnceDialog newInstance(@StringRes int title, @StringRes int message) {
        final Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MESSAGE_INT, message);
        return newInstance(args);
    }

    public static ShowOnceDialog newInstance(@StringRes int title, @StringRes int message, @DrawableRes int icon) {
        final Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putInt(ARG_MESSAGE_INT, message);
        args.putInt(ARG_ICON, icon);
        return newInstance(args);
    }

    public static ShowOnceDialog newInstance(@StringRes int title, @NonNull String message) {
        final Bundle args = new Bundle();
        args.putInt(ARG_TITLE, title);
        args.putString(ARG_MESSAGE_STRING, message);
        return newInstance(args);
    }

    private static ShowOnceDialog newInstance(@NonNull Bundle args) {
        final ShowOnceDialog dialog = new ShowOnceDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (callback == null) {
            try {
                callback = (ShowOnceDialogClickListener) getTargetFragment();
            } catch (ClassCastException e) {
                //
            }

            // called from an activity rather than a fragment
            if (callback == null) {
                if ((activity instanceof ShowOnceDialogClickListener)) {
                    callback = (ShowOnceDialogClickListener) activity;
                } else {
                    // no callback no problem
                }
            }
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
    }

    @Override
    // generally allow state loss for simple string alerts
    public void show(FragmentManager manager, String tag) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext());

        if (!sharedPreferences.getBoolean(PREF_PREFIX + tag, false)) {
            FragmentTransaction ft = manager.beginTransaction();
            ft.add(this, tag);
            ft.commitAllowingStateLoss();
        }
    }

    // generally allow state loss for simple string alerts
    public static boolean shouldNotShowAnymore(String tag) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext());
        return sharedPreferences.getBoolean(PREF_PREFIX + tag, false);
    }

    private void saveDontShowAgain(boolean dontShow) {
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext());
        sharedPreferences.edit().putBoolean(PREF_PREFIX + getTag(), dontShow).apply();
    }

    public interface ShowOnceDialogClickListener {
        void onYes(String tag);

        default void onCancel(String tag) {
        }
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {

        final Bundle arguments = getArguments();
        @StringRes int title = arguments.getInt(ARG_TITLE);
        @StringRes int messageInt = arguments.getInt(ARG_MESSAGE_INT);
        String messageString = null;
        if (messageInt == 0) {
            messageString = arguments.getString(ARG_MESSAGE_STRING);
        }
        @DrawableRes int icon = arguments.getInt(ARG_ICON, 0);
        AtomicBoolean dontShowAgain = new AtomicBoolean(false);

        final String tag = this.getTag();

        final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_show_once, null);
        final TextView textView = dialogView.findViewById(R.id.message);
        final MaterialCheckBox checkbox = dialogView.findViewById(R.id.checkbox);
        if (callback != null) {
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> dontShowAgain.set(isChecked));
        } else {
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> saveDontShowAgain(isChecked));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
        builder.setView(dialogView);
        builder.setCancelable(false);

        if (title != -1) {
            builder.setTitle(title);
        }

        if (icon != 0) {
            builder.setIcon(icon);
        }

        if (callback != null) {
            builder.setPositiveButton(getString(R.string.ok), (dialog, whichButton) -> {
                saveDontShowAgain(dontShowAgain.get());
                callback.onYes(tag);
            });
            builder.setNegativeButton(getString(R.string.cancel), (dialog, whichButton) -> callback.onCancel(tag));
        } else {
            builder.setPositiveButton(getString(R.string.ok), null);
        }

        if (messageString != null) {
            textView.setText(messageString);
        } else {
            textView.setText(messageInt);
        }

        setCancelable(false);

        AlertDialog alertDialog = builder.create();
        return alertDialog;
    }
}
