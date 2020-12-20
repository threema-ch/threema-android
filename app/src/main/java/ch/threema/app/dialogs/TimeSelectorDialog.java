/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2020 Threema GmbH
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
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TimePicker;

import java.util.Calendar;
import java.util.Date;

import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;

public class TimeSelectorDialog extends ThreemaDialogFragment implements TimePickerDialog.OnTimeSetListener {
	private Activity activity;
	private TimeSelectorDialogListener callback;
	private Calendar calendar;
	private Date originalDate;

	public interface TimeSelectorDialogListener {
		void onTimeSet(String tag, Date date);
		void onCancel(String tag, Date date);
	}

	public static TimeSelectorDialog newInstance(Date date) {
		TimeSelectorDialog dialog = new TimeSelectorDialog();

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
				callback = (TimeSelectorDialog.TimeSelectorDialogListener) getTargetFragment();
			} catch (ClassCastException e) {
				//
			}

			// called from an activity rather than a fragment
			if (callback == null) {
				if (!(activity instanceof SelectorDialog.SelectorDialogClickListener)) {
					throw new ClassCastException("Calling fragment must implement DateSelectorDialogClickListener interface");
				}
				callback = (TimeSelectorDialog.TimeSelectorDialogListener) activity;
			}
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		originalDate = (Date) getArguments().getSerializable("date");

		calendar = Calendar.getInstance();
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		int minute = calendar.get(Calendar.MINUTE);
		int style = R.style.Theme_Threema_Dialog;

		if (ConfigUtils.getAppTheme(activity) == ConfigUtils.THEME_DARK) {
			style = R.style.Theme_Threema_Dialog_Dark;
		}
		final TimePickerDialog dialog = new TimePickerDialog(activity, style, this, hour, minute,
				DateFormat.is24HourFormat(activity));

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
	public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
		// see http://code.google.com/p/android/issues/detail?id=34833
		if (view.isShown() && callback != null && calendar != null) {
			calendar.clear();
			if (originalDate != null) {
				calendar.setTime(originalDate);
			}
			calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
			calendar.set(Calendar.MINUTE, minute);
			callback.onTimeSet(this.getTag(), calendar.getTime());
		}
	}

}
