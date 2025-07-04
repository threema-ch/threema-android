/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.os.Handler;
import android.text.Editable;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ContactModel;

public class TypingIndicatorTextWatcher extends SimpleTextWatcher {

    private static final long TYPING_SEND_TIMEOUT = 10 * DateUtils.SECOND_IN_MILLIS;
    private final Handler typingIndicatorHandler = new Handler();
    @NonNull
    private final ContactService contactService;
    private final ContactModel contactModel;

    private boolean isTypingSent = false;
    private String previousText = "";

    private final Runnable sendStoppedTyping = new Runnable() {
        public void run() {
            if (isTypingSent) {
                isTypingSent = false;
                contactService.sendTypingIndicator(contactModel.getIdentity(), false);
            }
        }
    };

    public TypingIndicatorTextWatcher(@NonNull ContactService contactService, ContactModel contactModel) {
        this.contactService = contactService;
        this.contactModel = contactModel;
    }

    public void stopTyping() {
        sendStoppedTyping.run();
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
        if (textHasChanged(charSequence)) {
            if (!isTypingSent) {
                new Thread(() -> {
                    isTypingSent = true;
                    contactService.sendTypingIndicator(contactModel.getIdentity(), true);
                }).start();
            } else {
                //stop end typing sending handler
                killEvents();
            }
        }
    }

    public void killEvents() {
        typingIndicatorHandler.removeCallbacks(sendStoppedTyping);
    }

    @Override
    public void afterTextChanged(Editable editable) {
        if (editable != null && editable.length() == 0) {
            typingIndicatorHandler.post(sendStoppedTyping);
        } else {
            typingIndicatorHandler.postDelayed(sendStoppedTyping, TYPING_SEND_TIMEOUT);
        }
    }

    private boolean textHasChanged(CharSequence charSequence) {
        if (charSequence != null) {
            if (!TestUtil.compare(charSequence.toString(), this.previousText)) {
                this.previousText = charSequence.toString();
                return true;
            }
        }

        return false;
    }
}
