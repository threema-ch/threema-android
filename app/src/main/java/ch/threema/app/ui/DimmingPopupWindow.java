/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;

import java.lang.ref.WeakReference;

import androidx.annotation.Nullable;

public abstract class DimmingPopupWindow extends PopupWindow {
    private final WeakReference<Context> contextRef;

    protected DimmingPopupWindow(Context context) {
        super(context);
        contextRef = new WeakReference<>(context);
    }

    protected void dimBackground() {
        if (getContext() == null) {
            return;
        }
        View container = (View) getContentView().getParent().getParent();
        if (container != null) {
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();

            if (p != null && wm != null) {
                p.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                p.dimAmount = 0.6f;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    p.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                    p.setBlurBehindRadius(20);
                }
                wm.updateViewLayout(container, p);
            }
        }
    }

    @Nullable
    protected Context getContext() {
        return this.contextRef.get();
    }
}
