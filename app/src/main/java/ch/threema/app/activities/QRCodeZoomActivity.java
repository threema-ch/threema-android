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

package ch.threema.app.activities;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.ui.QRCodePopup;

import static ch.threema.app.services.QRCodeServiceImpl.QR_TYPE_ANY;

/***
 * Activity displaying QR Code popup
 */
public class QRCodeZoomActivity extends AppCompatActivity {
    QRCodePopup qrPopup = null;
    private static final String EXTRA_COLOR = "color";

    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View rootView = getWindow().getDecorView().getRootView();

        @QRCodeServiceImpl.QRCodeColor int qrCodeColor = getIntent().getIntExtra(EXTRA_COLOR, QR_TYPE_ANY);

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            showPopup(v, qrCodeColor);
            return insets;
        });
    }

    private void showPopup(final View v, @QRCodeServiceImpl.QRCodeColor int borderColor) {
        v.post(() -> {
            if (qrPopup == null || !qrPopup.isShowing()) {
                qrPopup = new QRCodePopup(this, v, this);
                qrPopup.setOnDismissListener(QRCodeZoomActivity.this::finish);
                if (!isDestroyed() && !isFinishing()) {
                    qrPopup.show(v, null, borderColor);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (qrPopup != null && qrPopup.isShowing()) {
            qrPopup.setOnDismissListener(null);
            qrPopup = null;
        }

        super.onDestroy();
    }
}
