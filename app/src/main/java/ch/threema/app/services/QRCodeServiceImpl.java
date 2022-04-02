/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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
import com.neilalexander.jnacl.NaCl;

import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;
import java.util.HashMap;

import androidx.annotation.IntDef;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class QRCodeServiceImpl implements QRCodeService {

	private static final Logger logger = LoggingUtil.getThreemaLogger("QRCodeService");

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({QR_TYPE_ANY, QR_TYPE_ID, QR_TYPE_ID_EXPORT, QR_TYPE_WEB, QR_TYPE_GROUP_LINK})
	public @interface QRCodeColor {}
	public static final int QR_TYPE_ANY = 0x00000000;
	public static final int QR_TYPE_ID = 0xff4caf50; // green
	public static final int QR_TYPE_ID_EXPORT = 0xffffeb3b; // yellow
	public static final int QR_TYPE_WEB = 0xff2196f3; // blue
	public static final int QR_TYPE_GROUP_LINK = 0xfff44336; // red

	private final static int QR_CODE_QUIET_ZONE_SIZE = 4; // https://www.qrcode.com/en/howto/code.html

	private final UserService userService;
	public final static String CONTENT_PREFIX = "3mid:";
	public final static String ID_SCHEME = "3mid";

	public QRCodeServiceImpl(UserService userService) {
		this.userService = userService;
	}

	private String getUserQRCodeString() {
		return CONTENT_PREFIX + this.userService.getIdentity() + "," +
				Utils.byteArrayToHexString(this.userService.getPublicKey());
	}

	@Override
	public QRCodeContentResult getResult(String content) {
		if (!TestUtil.empty(content)) {
			final String[] pieces = content.substring(CONTENT_PREFIX.length()).split(",");
			if (pieces.length >= 2 && pieces[0].length() == ProtocolDefines.IDENTITY_LEN && pieces[1].length() == NaCl.PUBLICKEYBYTES * 2) {
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
	 * @param contents String to render
	 * @param unicode Whether the string contains unicode characters
	 * @param addQuietZone If a quiet zone margin should be added around the resulting QR code
	 * @return a BitMatrix of the QR code
	 */
	private BitMatrix renderQR(String contents, boolean unicode, boolean addQuietZone) {
		BarcodeFormat barcodeFormat = BarcodeFormat.QR_CODE;

		QRCodeWriter barcodeWriter = new QRCodeWriter();
		HashMap<EncodeHintType, Object> hints = new HashMap<>(2);
		hints.put(EncodeHintType.MARGIN, addQuietZone ? QRCodeServiceImpl.QR_CODE_QUIET_ZONE_SIZE : 0);
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
	 * @param raw String to render as a QR code
	 * @param unicode Whether the string contains unicode characters
	 * @param borderColor Color of a border indicating the QR code purpose / type
	 * @return
	 */
	@Override
	public Bitmap getRawQR(String raw, boolean unicode, @QRCodeColor int borderColor) {
		if (this.userService.hasIdentity()) {
			BitMatrix matrix;

			if (raw != null && raw.length()>0) {
				matrix = this.renderQR(raw, unicode, true);
			} else {
				matrix = this.renderQR(getUserQRCodeString(), unicode, true);
			}

			if (matrix != null) {
				final int WHITE = 0xFFFFFFFF;
				int BLACK = 0xFF000000;

				int width = matrix.getWidth();
				int height = matrix.getHeight();
				int qrCodeTypeBorderSize = 1;
				int[] pixels = new int[width * height];

				for (int y = 0; y < height; y++) {
					int offset = y * width;
					for (int x = 0; x < width; x++) {
						pixels[offset + x] = matrix.get(x, y) ? BLACK : WHITE;
					}
				}

				Bitmap bitmap;
				if (ConfigUtils.showQRCodeTypeBorders()) {
					bitmap = Bitmap.createBitmap(matrix.getWidth() + (qrCodeTypeBorderSize * 2), matrix.getHeight() + (qrCodeTypeBorderSize * 2), Bitmap.Config.RGB_565);
					bitmap.eraseColor(borderColor);
					bitmap.setPixels(pixels, 0, width, qrCodeTypeBorderSize, qrCodeTypeBorderSize, width, height);
				} else {
					bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.RGB_565);
					bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
				}
				return bitmap;
			}

		}
		return null;
	}

	@Override
	public Bitmap getUserQRCode() {
		return this.getRawQR(null, false, QR_TYPE_ID);
	}
}
