/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ch.threema.app.utils.TestUtil;

public class GenericAlertDialog extends ThreemaDialogFragment {
    private DialogClickListener callback;
    private Activity activity;
    private AlertDialog alertDialog;
    private boolean isHtml;

    public static GenericAlertDialog newInstance(@StringRes int title, @StringRes int message,
                                                 @StringRes int positive, @StringRes int negative) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);

        dialog.setArguments(args);
        return dialog;
    }

    public static GenericAlertDialog newInstance(@StringRes int title, @StringRes int message,
                                                 @StringRes int positive, @StringRes int negative,
                                                 @StringRes int neutral, @DrawableRes int icon) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putInt("neutral", neutral);
        args.putInt("icon", icon);

        dialog.setArguments(args);
        return dialog;
    }

    public static GenericAlertDialog newInstance(@StringRes int title, @StringRes int message,
                                                 @StringRes int positive, @StringRes int negative,
                                                 @DrawableRes int icon) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putInt("icon", icon);

        dialog.setArguments(args);
        return dialog;
    }

    public static GenericAlertDialog newInstance(@StringRes int title, @StringRes int message,
                                                 @StringRes int positive, @StringRes int negative, boolean cancelable) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putInt("message", message);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putBoolean("cancelable", cancelable);

        dialog.setArguments(args);
        return dialog;
    }

    public static GenericAlertDialog newInstance(@StringRes int title, String messageString,
                                                 @StringRes int positive, @StringRes int negative, boolean cancelable) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putString("messageString", messageString);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putBoolean("cancelable", cancelable);

        dialog.setArguments(args);
        return dialog;
    }

    public static GenericAlertDialog newInstanceHtml(@StringRes int title, String messageString,
                                                     @StringRes int positive, @StringRes int negative, boolean cancelable) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putString("messageString", messageString);
        args.putInt("positive", positive);
        args.putInt("negative", negative);
        args.putBoolean("cancelable", cancelable);
        args.putBoolean("html", true);

        dialog.setArguments(args);
        return dialog;
    }

    public static GenericAlertDialog newInstance(@StringRes int title, CharSequence messageString,
                                                 @StringRes int positive, @StringRes int negative) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putInt("title", title);
        args.putCharSequence("messageString", messageString);
        args.putInt("positive", positive);
        args.putInt("negative", negative);

        dialog.setArguments(args);
        return dialog;
    }

    public static GenericAlertDialog newInstance(String titleString, CharSequence messageString,
                                                 @StringRes int positive, @StringRes int negative) {
        GenericAlertDialog dialog = new GenericAlertDialog();
        Bundle args = new Bundle();
        args.putString("titleString", titleString);
        args.putCharSequence("messageString", messageString);
        args.putInt("positive", positive);
        args.putInt("negative", negative);

        dialog.setArguments(args);
        return dialog;
    }

    public static GenericAlertDialog newInstance(String titleString, CharSequence messageString,
                                                 @StringRes int positive, @StringRes int negative, @StringRes int neutral) {
        GenericAlertDialog dialog = newInstance(titleString, messageString, positive, negative);
        if (dialog.getArguments() != null) {
            dialog.getArguments().putInt("neutral", neutral);
        }
        return dialog;
    }


    public interface DialogClickListener {
        void onYes(String tag, Object data);

        default void onNo(String tag, Object data) {
        }

        ;

        default void onNeutral(String tag, Object data) {
            // optional interface
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (callback == null) {
            try {
                callback = (DialogClickListener) getTargetFragment();
            } catch (ClassCastException e) {
                //
            }

            // called from an activity rather than a fragment
            if (callback == null) {
                if ((activity instanceof DialogClickListener)) {
                    callback = (DialogClickListener) activity;
                } else {
                    throw new ClassCastException("Calling fragment must implement DialogClickListener interface");
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
    public void onStart() {
        super.onStart();

        if (isHtml) {
            View textView = alertDialog.findViewById(android.R.id.message);

            if (textView instanceof TextView) {
                ((TextView) textView).setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }

    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        int title = getArguments().getInt("title");
        String titleString = getArguments().getString("titleString");
        int message = getArguments().getInt("message");
        CharSequence messageString = getArguments().getCharSequence("messageString");
        int positive = getArguments().getInt("positive");
        int negative = getArguments().getInt("negative");
        int neutral = getArguments().getInt("neutral");
        @DrawableRes int icon = getArguments().getInt("icon", 0);
        boolean cancelable = getArguments().getBoolean("cancelable", true);
        isHtml = getArguments().getBoolean("html", false);

        final String tag = this.getTag();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
        if (TestUtil.isEmptyOrNull(titleString)) {
            if (title != 0) {
                builder.setTitle(title);
            }
        } else {
            builder.setTitle(titleString);
        }
        if (TextUtils.isEmpty(messageString)) {
            if (message != 0) {
                builder.setMessage(message);
            }
        } else {
            if (isHtml) {
                builder.setMessage(Html.fromHtml(messageString.toString()));
            } else {
                builder.setMessage(messageString);
            }
        }

        builder.setPositiveButton(getString(positive), (dialog, whichButton) -> callback.onYes(tag, object)
        );
        if (negative != 0) {
            builder.setNegativeButton(getString(negative), (dialog, whichButton) -> callback.onNo(tag, object));
        }

        if (neutral != 0) {
            builder.setNeutralButton(getString(neutral), (dialog, whichButton) -> callback.onNeutral(tag, object));
            cancelable = false;
        }

        if (icon != 0) {
            builder.setIcon(icon);
        }

        alertDialog = builder.create();

        if (!cancelable) {
            setCancelable(false);
        }

        return alertDialog;
    }

    @Override
    public void onCancel(DialogInterface dialogInterface) {
        callback.onNo(getTag(), object);
    }

    public GenericAlertDialog setTargetFragment(@Nullable Fragment fragment) {
        setTargetFragment(fragment, 0);
        return this;
    }

    /**
     * Set the callback of this dialog.
     *
     * @param dialogClickListener the listener
     */
    public void setCallback(DialogClickListener dialogClickListener) {
        callback = dialogClickListener;
    }
}

