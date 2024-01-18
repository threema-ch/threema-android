/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import ch.threema.app.R;

public class SendButton extends FrameLayout {
	private static final int STATE_SEND = 1;
	private static final int STATE_RECORD = 2;
	private static final int TRANSITION_DURATION_MS = 150;

	private Drawable backgroundEnabled, backgroundDisabled;
	private Context context;
	private AppCompatImageView icon;
	private TransitionDrawable transitionDrawable;
	private int currentState;
	private final Object currentStateLock = new Object();

	public SendButton(Context context) {
		this(context, null);
	}

	public SendButton(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public SendButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs) {
		LayoutInflater inflater = (LayoutInflater) context
			.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.send_button, this);

		this.context = context;

		this.backgroundEnabled = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_circle_send, context.getTheme());
		this.backgroundDisabled = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_circle_send_disabled, context.getTheme());
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		this.icon = this.findViewById(R.id.icon);
		this.transitionDrawable = (TransitionDrawable) ContextCompat.getDrawable(getContext(), R.drawable.transition_send_button);
		this.transitionDrawable.setCrossFadeEnabled(true);
		this.icon.setImageDrawable(this.transitionDrawable);

		synchronized (currentStateLock) {
			this.transitionDrawable.resetTransition();
			currentState = STATE_SEND;
		}
	}

	public void setSend() {
		synchronized (currentStateLock) {
			if (currentState != STATE_SEND) {
				this.transitionDrawable.reverseTransition(TRANSITION_DURATION_MS);
				setContentDescription(this.context.getString(R.string.send));
				currentState = STATE_SEND;
			}
		}
	}

	public void setRecord() {
		synchronized (currentStateLock) {
			if (currentState != STATE_RECORD) {
				this.transitionDrawable.startTransition(TRANSITION_DURATION_MS);
				setContentDescription(this.context.getString(R.string.voice_message_record));
				currentState = STATE_RECORD;
			}
		}
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		setBackground(enabled ? this.backgroundEnabled : this.backgroundDisabled);
		if (!enabled) {
			setSend();
		}
	}
}
