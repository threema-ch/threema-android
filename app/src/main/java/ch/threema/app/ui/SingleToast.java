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

package ch.threema.app.ui;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.UiThread;
import ch.threema.app.ThreemaApplication;

public class SingleToast {
    private Toast toast = null;

    private static SingleToast sInstance = null;

    public static synchronized SingleToast getInstance() {
        if (sInstance == null) {
            sInstance = new SingleToast();
        }
        return sInstance;
    }

    private SingleToast() {
    }

    public SingleToast text(String text, int length) {
        return this.text(text, length, Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL, 0, 0);
    }

    @SuppressLint("ShowToast")
    synchronized public SingleToast text(String text, int length, int gravity, int x, int y) {
        if (this.toast == null) {
            this.toast = Toast.makeText(ThreemaApplication.getAppContext(), text, length);

            if (gravity != 0 || x != 0 || y != 0) {
                this.toast.setGravity(gravity, x, y);
            }

        } else if (this.toast.getGravity() != gravity
            || this.toast.getXOffset() != x
            || this.toast.getYOffset() != y) {
            //close toast to reset gravity
            this.toast.cancel();
            this.toast = null;
            this.text(text, length, gravity, x, y);
            return this;
        } else {
            this.toast.setText(text);
        }

        this.toast.show();
        return this;
    }

    public SingleToast close() {
        if (this.toast != null) {
            this.toast.cancel();
            this.toast = null;
        }
        return this;
    }

    @UiThread
    public void showShortText(String text) {
        showText(text, Toast.LENGTH_SHORT);
    }

    @UiThread
    public void showLongText(String text) {
        showText(text, Toast.LENGTH_LONG);
    }

    @UiThread
    private void showText(String text, int length) {
        text(text, length);
    }

    @UiThread
    public void showBottom(String text, int length) {
        text(text, length, 0, 0, 0);
    }
}
