/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.app.voip;

import android.os.Build;

import androidx.annotation.NonNull;
import ch.threema.app.utils.TurnServerCache;

/**
 * VoIP configuration.
 */
public class Config {
	private static final int MIN_SPARE_TURN_VALIDITY = 3600*1000;

	// Hardware AEC exclusion list (Manufacturer;Model)
	@NonNull private final static String[] HW_AEC_EXCLUSION_LIST = new String[] {
		"Fairphone;FP2",
		"ZUK;ZUK Z1", // Ticket #286367
        "bq;Aquaris X" // Ticket #494934
	};

	/**
	 * Return whether this device is allowed to use hardware echo cancellation.
	 *
	 * This will return false only for devices on the {@link #HW_AEC_EXCLUSION_LIST}.
	 */
	public static boolean allowHardwareAec() {
		final String deviceInfo = Build.MANUFACTURER + ";" + Build.MODEL;
		for (String entry : HW_AEC_EXCLUSION_LIST) {
			if (entry.equalsIgnoreCase(deviceInfo)) {
				return false;
			}
		}
		return true;
	}

	// Hardware video codec exclusion list (Manufacturer;Model;AndroidVersionPrefix)
	@NonNull private final static String[] HW_VIDEO_CODEC_EXCLUSION_LIST = new String[] {
		"Samsung;SM-A310F;7.", // Galaxy A3 (2016), Ticket #301129
		"Samsung;SM-A320FL;8.", // Galaxy A3 (2017), Ticket #926673
		"Samsung;SM-G930F;7.", // Galaxy S7, Ticket #573851
		"Samsung;SM-G960F;8.", // Galaxy S9, Ticket #379708
	};

	/**
	 * Do not use this directly, only for simplified testing.
	 * Use {@link #allowHardwareVideoCodec()} instead!
	 */
	protected static boolean allowHardwareVideoCodec(String[] exclusionList, String deviceInfo) {
		for (String entry : exclusionList) {
			if (deviceInfo.toLowerCase().startsWith(entry.toLowerCase())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Return whether this device is allowed to use hardware video codecs.
	 *
	 * This will return false only for devices on the {@link #HW_VIDEO_CODEC_EXCLUSION_LIST}.
	 */
	public static boolean allowHardwareVideoCodec() {
		final String deviceInfo = Build.MANUFACTURER + ";" + Build.MODEL + ";" + Build.VERSION.RELEASE;
		return allowHardwareVideoCodec(HW_VIDEO_CODEC_EXCLUSION_LIST, deviceInfo);
	}

	private static final TurnServerCache TURN_SERVER_CACHE = new TurnServerCache("voip", MIN_SPARE_TURN_VALIDITY);

	public static TurnServerCache getTurnServerCache() {
		return TURN_SERVER_CACHE;
	}
}
