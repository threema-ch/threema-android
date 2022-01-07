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
import com.neilalexander.jnacl.NaCl;

import java.util.Date;
import java.util.Hashtable;

import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;

public class QRCodeServiceImpl implements QRCodeService {
	private final UserService userService;
	public final static String CONTENT_PREFIX = "3mid:";
	public final static String ID_SCHEME = "3mid";
	private Bitmap userQRCodeBitmap;

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

	private BitMatrix renderQR(String contents, int width, int height, int border, boolean unicode) {
		BarcodeFormat barcodeFormat = BarcodeFormat.QR_CODE;

		QRCodeWriter barcodeWriter = new QRCodeWriter();
		Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>(2);
		hints.put(EncodeHintType.MARGIN, border);
		if (unicode) {
			hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
		}

		try {
			BitMatrix matrix = barcodeWriter.encode(contents, barcodeFormat, width, height, hints);
			return matrix;
		} catch (WriterException e) {
		}
		return null;
	}

	@Override
	public Bitmap getRawQR(String raw, boolean unicode) {
		if (this.userService.hasIdentity()) {
			BitMatrix matrix = null;

			if (raw != null && raw.length()>0) {
				matrix = this.renderQR(raw, 0, 0, 0, unicode);
			} else {
				matrix = this.renderQR(getUserQRCodeString(), 0, 0, 0, unicode);
			}

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

				Bitmap bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.RGB_565);
				bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
				return bitmap;
			}

		}
		return null;
	}

	@Override
	public Bitmap getUserQRCode() {
		if (this.userQRCodeBitmap == null) {
			this.userQRCodeBitmap = this.getRawQR(null, false);
		}
		return this.userQRCodeBitmap;
	}
}
