/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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

public abstract class DimmingPopupWindow extends PopupWindow {
	private final Context context;

	protected DimmingPopupWindow(Context context) {
		super(context);

		this.context = context;
	}

	protected void dimBackground() {
		View container;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			container = (View) getContentView().getParent().getParent();
		} else {
			container = (View) getContentView().getParent();
		}
		if (container != null) {
			WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
			WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();

			if (p != null && wm != null) {
				p.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
				p.dimAmount = 0.6f;
				wm.updateViewLayout(container, p);
			}
		}
	}

	protected Context getContext() {
		return this.context;
	}
}
