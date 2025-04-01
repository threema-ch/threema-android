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

import android.os.Build;
import android.text.Spannable;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Method;

public class ViewUtil {
    /**
     * show the view and return true if exist
     *
     * @param view
     * @return
     */
    public static boolean show(View view) {
        return show(view, true);
    }

    /**
     * show or hide the view and return true if exist
     *
     * @param view
     * @return
     */
    public static boolean show(View view, boolean show) {
        if (view == null) {
            return false;
        }

        view.setVisibility(show ? View.VISIBLE : View.GONE);
        return true;
    }

    public static boolean show(MenuItem menuItem, boolean show) {
        if (menuItem == null) {
            return false;
        }

        menuItem.setVisible(show);
        return true;
    }

    /**
     * show the first view and hide the second, return true if the views exist
     *
     * @param viewToHide
     * @param viewToShow
     * @return
     */
    public static boolean showAndHide(View viewToShow, View viewToHide) {
        if (viewToShow == null || viewToHide == null) {
            return false;
        }

        return show(viewToShow, true)
            && show(viewToHide, false);
    }

    public static boolean showAndSet(ImageView view, int imageResourceId) {
        if (!show(view)) {
            return false;
        }
        view.setImageResource(imageResourceId);
        return true;
    }


    /**
     * show a text view and set the text, return true if the view exist
     *
     * @param view
     * @param text
     * @return
     */
    public static boolean showAndSet(TextView view, String text) {
        if (!show(view)) {
            return false;
        }

        view.setText(text);
        return true;
    }

    public static boolean showAndSet(TextView view, Spannable text) {
        if (!show(view)) {
            return false;
        }

        view.setText(text);
        return true;
    }

    /**
     * show a checkbox view and set the check state, return true if the view exist
     *
     * @param view
     * @param checked
     * @return
     */
    public static boolean showAndSet(CheckBox view, boolean checked) {
        if (!show(view)) {
            return false;
        }

        view.setChecked(checked);
        return true;
    }

    /**
     * Set touchModal flag of PopupWindow which is hidden on API<29
     *
     * @param popupWindow PopupWindow
     * @param touchModal  whether to enable or disable the flag
     */
    public static void setTouchModal(@NonNull PopupWindow popupWindow, boolean touchModal) {
        if (Build.VERSION.SDK_INT >= 29) {
            popupWindow.setTouchModal(touchModal);
        } else {
            Method method;
            try {
                method = PopupWindow.class.getDeclaredMethod("setTouchModal", boolean.class);
                method.setAccessible(true);
                method.invoke(popupWindow, touchModal);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
