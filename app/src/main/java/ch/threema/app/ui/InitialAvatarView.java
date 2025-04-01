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

package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import ch.threema.app.R;

public class InitialAvatarView extends FrameLayout {
    private TextView avatarInitials;

    public InitialAvatarView(Context context) {
        super(context);
        init(context);
    }

    public InitialAvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public InitialAvatarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater inflater = (LayoutInflater) context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.initial_avatar_view, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        avatarInitials = this.findViewById(R.id.avatar_initials);
    }

    public void setInitials(String firstName, String lastName) {
        StringBuilder initialsBuilder = new StringBuilder();
        if (firstName != null && firstName.length() > 0) {
            initialsBuilder.append(firstName.substring(0, 1));
        }
        if (lastName != null && lastName.length() > 0) {
            initialsBuilder.append(lastName.substring(0, 1));
        }

        avatarInitials.setText(initialsBuilder.length() > 0 ? initialsBuilder.toString() : "");
        requestLayout();
    }
}
