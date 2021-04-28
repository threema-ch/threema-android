/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

package ch.threema.app.dialogs;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.widget.DatePicker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.IllegalFormatConversionException;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;

public class DateSelectorDialog extends ThreemaDialogFragment implements DatePickerDialog.OnDateSetListener {
	private static final Logger logger = LoggerFactory.getLogger(DateSelectorDialog.class);

	private Activity activity;
	private DateSelectorDialogListener callback;
	private Calendar calendar;
	private Date originalDate;

	public interface DateSelectorDialogListener {
		void onDateSet(String tag, Date date);
		void onCancel(String tag, Date date);
	}

	public static DateSelectorDialog newInstance(Date date) {
		DateSelectorDialog dialog = new DateSelectorDialog();

		Bundle args = new Bundle();
		args.putSerializable("date", date);

		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (callback == null) {
			try {
				callback = (DateSelectorDialogListener) getTargetFragment();
			} catch (ClassCastException e) {
				//
			}

			// called from an activity rather than a fragment
			if (callback == null) {
				if (!(activity instanceof SelectorDialog.SelectorDialogClickListener)) {
					throw new ClassCastException("Calling fragment must implement DateSelectorDialogClickListener interface");
				}
				callback = (DateSelectorDialogListener) activity;
			}
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		originalDate = (Date) getArguments().getSerializable("date");

		calendar = Calendar.getInstance();

		if (originalDate != null) {
			calendar.setTime(originalDate);
		}

		int day = calendar.get(Calendar.DAY_OF_MONTH);
		int month = calendar.get(Calendar.MONTH);
		int year = calendar.get(Calendar.YEAR);

		int style = R.style.Theme_Threema_Dialog;
		if (ConfigUtils.getAppTheme(activity) == ConfigUtils.THEME_DARK) {
			style = R.style.Theme_Threema_Dialog_Dark;
		}

		Context context = activity;
		if (isBrokenSamsungDevice()) {
			// workaround for http://stackoverflow.com/questions/28618405/datepicker-crashes-on-my-device-when-clicked-with-personal-app
			context = new ContextWrapper(getActivity()) {

				private Resources wrappedResources;

				@Override
				public Resources getResources() {
					Resources r = super.getResources();
					if(wrappedResources == null) {
						wrappedResources = new Resources(r.getAssets(), r.getDisplayMetrics(), r.getConfiguration()) {
							@NonNull
							@Override
							public String getString(int id, Object... formatArgs) throws Resources.NotFoundException {
								try {
									return super.getString(id, formatArgs);
								} catch (IllegalFormatConversionException ifce) {
									logger.debug("IllegalFormatConversionException om Samsung devices fixed.");
									String template = super.getString(id);
									template = template.replaceAll("%" + ifce.getConversion(), "%s");
									return String.format(getConfiguration().locale, template, formatArgs);
								}
							}
						};
					}
					return wrappedResources;
				}
			};
		}

		DatePickerDialog dialog = new DatePickerDialog(context, style, this, year, month, day);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
		}

		dialog.setButton(DialogInterface.BUTTON_NEGATIVE,
				getActivity().getString(android.R.string.cancel),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int which) {
						callback.onCancel(getTag(), originalDate);
					}
				});

		return dialog;
	}

	@Override
	public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
		// see http://code.google.com/p/android/issues/detail?id=34833
		if (view.isShown() && callback != null && calendar != null) {
			calendar.clear();
			calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
			calendar.set(Calendar.MONTH, month);
			calendar.set(Calendar.YEAR, year);
			callback.onDateSet(this.getTag(), calendar.getTime());
		}
	}

	private static boolean isBrokenSamsungDevice() {
		return (Build.MANUFACTURER.equalsIgnoreCase("samsung") &&
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
				Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1);
	}
}
