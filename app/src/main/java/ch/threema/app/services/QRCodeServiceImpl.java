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

package ch.threema.app.services;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.crypto.NaCl;

import org.slf4j.Logger;

import java.util.Date;
import java.util.HashMap;

import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class QRCodeServiceImpl implements QRCodeService {

    private static final Logger logger = LoggingUtil.getThreemaLogger("QRCodeServiceImpl");

    // https://www.qrcode.com/en/howto/code.html
    private final static int QR_CODE_QUIET_ZONE_SIZE = 4;

    private final UserService userService;
    public final static String CONTENT_PREFIX = "3mid:";

    public QRCodeServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @NonNull
    private String getUserQRCodeString() {
        return CONTENT_PREFIX + this.userService.getIdentity() + "," +
            Utils.byteArrayToHexString(this.userService.getPublicKey());
    }

    @Override
    public QRCodeContentResult getResult(String content) {
        if (!TestUtil.isEmptyOrNull(content)) {
            final String[] pieces = content.substring(CONTENT_PREFIX.length()).split(",");
            if (pieces.length >= 2 && pieces[0].length() == ProtocolDefines.IDENTITY_LEN && pieces[1].length() == NaCl.PUBLIC_KEY_BYTES * 2) {
                return new QRCodeContentResult() {
                    @Override
                    public String getIdentity() {
                        return pieces[0];
                    }

                    @Override
                    public byte[] getPublicKey() {
                        return Utils.hexStringToByteArray(pieces[1]);
                    }

                    @Override
                    public Date getExpirationDate() {
                        if (pieces.length >= 3)
                            return new Date(Long.parseLong(pieces[2]) * 1000);

                        return null;
                    }
                };
            }
        }
        return null;
    }

    /**
     * Render QR code for provided string
     *
     * @param contents     String to render
     * @param unicode      Whether the string contains unicode characters
     * @return a BitMatrix of the QR code
     */
    @Nullable
    private BitMatrix renderQR(String contents, boolean unicode) {
        BarcodeFormat barcodeFormat = BarcodeFormat.QR_CODE;

        QRCodeWriter barcodeWriter = new QRCodeWriter();
        HashMap<EncodeHintType, Object> hints = new HashMap<>(2);
        hints.put(EncodeHintType.MARGIN, QRCodeServiceImpl.QR_CODE_QUIET_ZONE_SIZE);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q);

        if (unicode) {
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        }

        try {
            return barcodeWriter.encode(contents, barcodeFormat, 0, 0, hints);
        } catch (WriterException e) {
            logger.error("BarcodeWriter exception", e);
        }
        return null;
    }

    /**
     * Get a qr code bitmap for the string provided
     *
     * @param raw         String to render as a QR code
     * @param unicode     Whether the string contains unicode characters
     */
    @Override
    public Bitmap getRawQR(String raw, boolean unicode) {
        if (this.userService.hasIdentity()) {
            var content = raw != null && !raw.isEmpty()
                ? raw
                : getUserQRCodeString();
            BitMatrix matrix = renderQR(content, unicode);

            if (matrix != null) {
                final int WHITE = 0xFFFFFFFF;
                int BLACK = 0xFF000000;

                int width = matrix.getWidth();
                int height = matrix.getHeight();
                int[] pixels = new int[width * height];

                for (int y = 0; y < height; y++) {
                    int offset = y * width;
                    for (int x = 0; x < width; x++) {
                        pixels[offset + x] = matrix.get(x, y) ? BLACK : WHITE;
                    }
                }

                final Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.RGB_565);
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
                return bitmap;
            }

        }
        return null;
    }

    @Override
    public Bitmap getUserQRCode() {
        return this.getRawQR(null, false);
    }
}
