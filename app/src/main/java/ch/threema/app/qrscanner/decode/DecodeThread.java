/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.threema.app.qrscanner.decode;

import android.os.Handler;
import android.os.Looper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import ch.threema.app.qrscanner.activity.CaptureActivity;


/**
 * This thread does all the heavy lifting of decoding the images.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */

/**
 * @date 2016-11-18 15:41
 * @auther GuoJinyu
 * @description modified
 */
public final class DecodeThread extends Thread {
	private final CaptureActivity activity;
	private final Map<DecodeHintType, Object> hints;
	private final CountDownLatch handlerInitLatch;
	private Handler handler;

	public DecodeThread(CaptureActivity activity, ResultPointCallback resultPointCallback) {
		this.activity = activity;
		handlerInitLatch = new CountDownLatch(1);
		hints = new EnumMap<>(DecodeHintType.class);
		// The prefs can't change while the thread is running, so pick them up once here.
		Collection<BarcodeFormat> decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
		decodeFormats.addAll(DecodeFormatManager.PRODUCT_FORMATS);
		decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
		decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
		decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
		decodeFormats.addAll(DecodeFormatManager.AZTEC_FORMATS);
		decodeFormats.addAll(DecodeFormatManager.PDF417_FORMATS);
		hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
		hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
		//Log.i("DecodeThread", "Hints: " + hints);
	}

	public Handler getHandler() {
		try {
			handlerInitLatch.await();
		} catch (InterruptedException ie) {
			// continue?
		}
		return handler;
	}

	@Override
	public void run() {
		Looper.prepare();
		handler = new DecodeHandler(activity, hints);
		handlerInitLatch.countDown();
		Looper.loop();
	}

}
