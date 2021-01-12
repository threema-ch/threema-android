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
import android.util.AttributeSet;
import android.view.KeyEvent;
import androidx.appcompat.widget.SearchView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnKeyboardBackRespondingSearchView extends SearchView {
	private static final Logger logger = LoggerFactory.getLogger(OnKeyboardBackRespondingSearchView.class);

	private BackPressedListener onImeBack;

	public OnKeyboardBackRespondingSearchView(Context context) {
		super(context);
	}

	public OnKeyboardBackRespondingSearchView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public OnKeyboardBackRespondingSearchView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public void setBackPressedListener(BackPressedListener listener) {
		onImeBack = listener;
	}

	@Override
	public boolean dispatchKeyEventPreIme(KeyEvent event) {
		logger.debug("dispatch pre event triggered" + event);
		onImeBack.onImeBack(this);
		if(event.getKeyCode() == KeyEvent.KEYCODE_BACK){
			logger.debug("Test Back Pressed");
			onImeBack.onImeBack(this);
		}
		return super.dispatchKeyEventPreIme(event);
	}

	public interface BackPressedListener {
		void onImeBack(OnKeyboardBackRespondingSearchView etWrite);
	}
}
