/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import android.content.Context;
import android.telephony.TelephonyManager;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Locale;

import ch.threema.app.utils.TestUtil;

public class LocaleServiceImpl implements LocaleService {
	private final Context context;
	private String countryIsoCode;
	private PhoneNumberUtil phoneNumberUtil;

	public LocaleServiceImpl(Context context) {
		this.context = context;
	}

	public String getCountryIsoCode() {
		if (this.countryIsoCode == null) {
			try {
				TelephonyManager tm = (TelephonyManager) this.context.getSystemService(Context.TELEPHONY_SERVICE);
				this.countryIsoCode = tm.getSimCountryIso().toUpperCase(Locale.US);
			}
			catch (Exception x) {
				//do nothing
				//is TELEPHONY_SERVICE disabled?
			}

			if(this.countryIsoCode == null || this.countryIsoCode.length() == 0) {
				this.countryIsoCode = Locale.getDefault().getCountry();
			}
		}

		return this.countryIsoCode;
	}
	public String getNormalizedPhoneNumber(String phoneNumber) {
		Phonenumber.PhoneNumber parsedPhoneNumber = this.getPhoneNumber(phoneNumber);
		if(parsedPhoneNumber != null) {
			return this.getPhoneNumberUtil().format(parsedPhoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
		}

		return null;
	}

	@Override
	public String getHRPhoneNumber(String phoneNumber) {
		Phonenumber.PhoneNumber parsedPhoneNumber = this.getPhoneNumber(phoneNumber);
		if(parsedPhoneNumber != null) {
			return this.getPhoneNumberUtil().format(parsedPhoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
		}
		// return unformatted number
		return phoneNumber;
	}

	public boolean validatePhoneNumber(String phoneNumber) {
		Phonenumber.PhoneNumber parsedPhoneNumber;
		try {
			parsedPhoneNumber = this.getPhoneNumberUtil().parse(phoneNumber, this.getCountryIsoCode());
		} catch (NumberParseException e) {
			return false;
		}

		return phoneNumberUtil.isValidNumber(parsedPhoneNumber);

	}

	@Override
	public String getCountryCodePhonePrefix() {
		String region = getCountryIsoCode();
		if (!TestUtil.empty(region)) {
			int countryCode = getPhoneNumberUtil().getCountryCodeForRegion(region);
			if (countryCode > 0) {
				return "+" + countryCode;
			}
		}
		return "";
	}

	private PhoneNumberUtil getPhoneNumberUtil() {
		if(this.phoneNumberUtil == null) {
			this.phoneNumberUtil = PhoneNumberUtil.getInstance();
		}

		return this.phoneNumberUtil;
	}

	private Phonenumber.PhoneNumber getPhoneNumber(String phoneNumber) {
		if(phoneNumber == null) {
			return null;
		}

		Phonenumber.PhoneNumber parsedPhoneNumber;
		try {
			parsedPhoneNumber = this.getPhoneNumberUtil().parse(phoneNumber, this.getCountryIsoCode());
		} catch (NumberParseException | IllegalStateException e) {
			return null;
		}

		return parsedPhoneNumber;
	}
}
