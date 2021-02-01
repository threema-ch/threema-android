/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.app.fragments.wizard;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.Selection;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.TestUtil;

/**
 * Example:
 * countryName: Switzerland
 * region:      CH
 * isoCode:     CH
 * countryCode: 41
 * prefix:      +41
 */

public class WizardFragment3 extends WizardFragment {
	private static final Logger logger = LoggerFactory.getLogger(WizardFragment3.class);

	private EditText prefixText, emailEditText, phoneText;
	private CountryListAdapter countryListAdapter;
	private Spinner countrySpinner;
	private AsYouTypeFormatter phoneNumberFormatter;
	private AsyncTask<Void, Void, ArrayList<Map<String, String>>> countryListTask;
	public static final int PAGE_ID = 3;

	@Override
	public View onCreateView(LayoutInflater inflater, final ViewGroup container,
							 Bundle savedInstanceState) {
		View rootView = super.onCreateView(inflater, container, savedInstanceState);

		// inflate content layout
		contentViewStub.setLayoutResource(R.layout.fragment_wizard3);
		contentViewStub.inflate();

		WizardFragment5.SettingsInterface callback = (WizardFragment5.SettingsInterface) getActivity();

		countrySpinner = rootView.findViewById(R.id.country_spinner);
		emailEditText = rootView.findViewById(R.id.wizard_email);
		prefixText = rootView.findViewById(R.id.wizard_prefix);
		prefixText.setText("+");
		phoneText = rootView.findViewById(R.id.wizard_phone);

		if (!ConfigUtils.isWorkBuild()) {
			rootView.findViewById(R.id.wizard_email_layout).setVisibility(View.GONE);
			((TextView)rootView.findViewById(R.id.scooter)).setText(getString(R.string.new_wizard_link_mobile_only));
		}

		if (callback.isReadOnlyProfile()) {
			emailEditText.setEnabled(false);
			prefixText.setEnabled(false);
			phoneText.setEnabled(false);
			countrySpinner.setEnabled(false);
			rootView.findViewById(R.id.disabled_by_policy).setVisibility(View.VISIBLE);
		} else {
			emailEditText.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					if (getActivity() != null) {
						((OnSettingsChangedListener) getActivity()).onEmailSet(s.toString());
					}
				}
			});

			prefixText.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					String prefixString = s.toString();
					if (!prefixString.startsWith("+")) {
						prefixText.setText("+");
						Selection.setSelection(prefixText.getText(), prefixText.getText().length());
					} else if (prefixString.length() > 1 && countryListAdapter != null) {
						try {
							int countryCode = Integer.parseInt(prefixString.substring(1));
							String region = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);
							int position = countryListAdapter.getPosition(region);

							if (position > -1) {
								countrySpinner.setSelection(position);
								setPhoneNumberFormatter(countryCode);
								((OnSettingsChangedListener) getActivity()).onPrefixSet(prefixText.getText().toString());
							}
						} catch (NumberFormatException e) {
							logger.error("Exception", e);
						}
					}
				}
			});

			phoneText.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					if (!TextUtils.isEmpty(s) && phoneNumberFormatter != null) {
						phoneNumberFormatter.clear();

						String number = s.toString().replaceAll("[^\\d.]", "");
						String formattedNumber = null;

						for (int i = 0; i < number.length(); i++) {
							formattedNumber = phoneNumberFormatter.inputDigit(number.charAt(i));
						}

						if (formattedNumber != null && !s.toString().equals(formattedNumber)) {
							s.replace(0, s.length(), formattedNumber);
						}
					}
					Activity activity = getActivity();
					if (activity != null) {
						((OnSettingsChangedListener) activity).onPhoneSet(s.toString());
					}
				}
			});
		}

		TextView presetEmailText = rootView.findViewById(R.id.preset_email_text);
		TextView presetPhoneText = rootView.findViewById(R.id.preset_phone_text);

		if (!TestUtil.empty(callback.getPresetEmail())) {
			emailEditText.setVisibility(View.GONE);
			presetEmailText.setText(R.string.linked);
			presetEmailText.setVisibility(View.VISIBLE);
		}

		if (!TestUtil.empty(callback.getPresetPhone())) {
			phoneText.setVisibility(View.GONE);
			prefixText.setVisibility(View.GONE);
			countrySpinner.setVisibility(View.GONE);
			presetPhoneText.setText(R.string.linked);
			presetPhoneText.setVisibility(View.VISIBLE);
		} else {
			// load country list
			countryListTask = new AsyncTask<Void, Void, ArrayList<Map<String, String>>>() {
				final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

				@Override
				protected ArrayList<Map<String, String>> doInBackground(Void... params) {
					Set<String> regions = phoneNumberUtil.getSupportedRegions();
					ArrayList<Map<String, String>> results = new ArrayList<Map<String, String>>(regions.size());
					for (String region : regions) {
						Map<String, String> data = new HashMap<String, String>(2);
						data.put("name", getCountryName(region));
						data.put("prefix", "+" + PhoneNumberUtil.getInstance().getCountryCodeForRegion(region));
						results.add(data);
					}
					Collections.sort(results, new CountryNameComparator());


					Map<String, String> data = new HashMap<String, String>(2);
					data.put("name", getString(R.string.new_wizard_select_country));
					data.put("prefix", "");
					results.add(data);

					return results;
				}

				@Override
				protected void onPostExecute(final ArrayList<Map<String, String>> result) {
					countryListAdapter = new CountryListAdapter(getActivity(), android.R.layout.simple_spinner_dropdown_item, result);
					countrySpinner.setAdapter(countryListAdapter);
					countrySpinner.setSelection(countryListAdapter.getCount());
					countrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
						@Override
						public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
							if (position < result.size() - 1) {
								String prefixString = result.get(position).get("prefix");
								prefixText.setText(prefixString);

								if (!TestUtil.empty(prefixString) && prefixString.length() > 1) {
									setPhoneNumberFormatter(Integer.parseInt(prefixString.substring(1)));
								}
								phoneText.requestFocus();
							}
						}

						@Override
						public void onNothingSelected(AdapterView<?> parent) {
							prefixText.setText("+");
						}
					});

					if (prefixText.getText().length() <= 1) {
						String countryCode = localeService.getCountryCodePhonePrefix();
						if (!TestUtil.empty(countryCode)) {
							prefixText.setText(countryCode);
							((OnSettingsChangedListener) getActivity()).onPrefixSet(prefixText.getText().toString());
							phoneText.requestFocus();
						}
					}
				}
			};
			countryListTask.execute();
		}

		return rootView;
	}

	@Override
	protected int getAdditionalInfoText() {
		return ConfigUtils.isWorkBuild() ? R.string.new_wizard_info_link : R.string.new_wizard_info_link_phone_only;
	}

	private void showEditTextError(EditText editText, boolean show) {
		editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, show ? R.drawable.ic_error_red_24dp : 0, 0);
	}

	private String getCountryName(String region) {
		if (!TestUtil.empty(region)) {
			return new Locale("", region).getDisplayCountry(Locale.getDefault());
		} else {
			return "";
		}
	}

	void setPhoneNumberFormatter(int countryCode) {
		PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
		String regionCode = phoneNumberUtil.getRegionCodeForCountryCode(countryCode);

		if (!TestUtil.empty(regionCode)) {
			this.phoneNumberFormatter = phoneNumberUtil.getAsYouTypeFormatter(regionCode);
		} else {
			this.phoneNumberFormatter = null;
		}
	}

	private static class CountryNameComparator implements Comparator<Map<String, String>> {
		@Override
		public int compare(Map<String, String> lhs, Map<String, String> rhs) {
			// Compare two strings in the default locale
			Collator collator = Collator.getInstance();
			return collator.compare(lhs.get("name"), rhs.get("name"));
		}
	}

	private class CountryListAdapter extends BaseAdapter implements SpinnerAdapter {
		private ArrayList<Map<String, String>> list;
		private LayoutInflater inflater;
		private int resource;

		public CountryListAdapter(Context context, int resource, ArrayList<Map<String, String>> objects) {
			this.inflater = getActivity().getLayoutInflater();
			this.list = objects;
			this.resource = resource;
		}

		private class ViewHolder {
			TextView country;
		}

		@Override
		public int getCount() {
			int count = list.size();
			return count > 0 ? count - 1 : count;
		}

		@Override
		public Map<String, String> getItem(int position) {
			return list.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder viewHolder;

			if (convertView == null) {
				convertView = inflater.inflate(this.resource, parent, false);
				viewHolder = new ViewHolder();
				viewHolder.country = convertView.findViewById(android.R.id.text1);

				convertView.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) convertView.getTag();
			}
			Map<String, String> map = list.get(position);
			viewHolder.country.setText(map.get("name"));

			return convertView;
		}

		public int getPosition(String region) {
			String countryName = getCountryName(region);
			for (int i = 0; i < list.size(); i++) {
				Map<String, String> map = list.get(i);
				if (map.get("name").equalsIgnoreCase(countryName)) {
					return i;
				}
			}
			return -1;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onDetach() {
		super.onDetach();

		// make sure asynctask is cancelled before detaching fragment
		if (countryListTask != null) {
			countryListTask.cancel(true);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		initValues();
		if (this.phoneText != null) {
			this.phoneText.requestFocus();
			EditTextUtil.showSoftKeyboard(this.phoneText);
		}
	}

	@Override
	public void onPause() {
		if (this.phoneText != null) {
			this.phoneText.clearFocus();
			EditTextUtil.hideSoftKeyboard(this.phoneText);
		}
		super.onPause();
	}

	void initValues() {
		if (isResumed()) {
			WizardFragment5.SettingsInterface callback = (WizardFragment5.SettingsInterface) getActivity();
			emailEditText.setText(callback.getEmail());

			if (TestUtil.empty(callback.getPresetEmail())) {
				showEditTextError(emailEditText, !TestUtil.empty(callback.getEmail()) && !Patterns.EMAIL_ADDRESS.matcher(callback.getEmail()).matches());
			}

			prefixText.setText(callback.getPrefix());
			phoneText.setText(callback.getNumber());
			if (TestUtil.empty(callback.getPresetPhone())) {
				showEditTextError(phoneText, !TestUtil.empty(callback.getNumber()) && TestUtil.empty(callback.getPhone()));
			}
		}
	}

	public interface OnSettingsChangedListener {
		void onPrefixSet(String prefix);

		void onPhoneSet(String phoneNumber);

		void onEmailSet(String email);
	}
}
