/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.NumberFormat;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;

public class CancelableHorizontalProgressDialog extends ThreemaDialogFragment {
	private ProgressDialogClickListener callback;
	private Activity activity;
	private DialogInterface.OnClickListener listener;
	private NumberFormat mProgressPercentFormat;
	private TextView progressPercent;
	private LinearProgressIndicator progressBar;
	private int max;

	/**
	 * Creates a DialogFragment with a horizontal progress bar and a percentage display below. Mimics deprecated system ProgressDialog behavior
	 * @param title title of dialog
	 * @param message currently ignored
	 * @param button label of cancel button
	 * @param total maximum allowed progress value.
	 * @return nothing
	 */
	public static CancelableHorizontalProgressDialog newInstance(@StringRes int title, @StringRes int message, @StringRes int button, int total) {
		CancelableHorizontalProgressDialog dialog = new CancelableHorizontalProgressDialog();
		Bundle args = new Bundle();
		args.putInt("title", title);
		args.putInt("button", button);
		args.putInt("total", total);

		dialog.setArguments(args);
		return dialog;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.mProgressPercentFormat = NumberFormat.getPercentInstance();
		this.mProgressPercentFormat.setMaximumFractionDigits(0);

		if (callback == null) {
			try {
				callback = (ProgressDialogClickListener) getTargetFragment();
			} catch (ClassCastException e) {
				//
			}
		}

		// called from an activity rather than a fragment
		if (callback == null) {
			if ((activity instanceof ProgressDialogClickListener)) {
				callback = (ProgressDialogClickListener) activity;
			}
		}
	}

	/**
	 * Set a listener to be attached to the cancel button. Do not use, implement {@link ProgressDialogClickListener} listener on the calling activity/fragment instead!
	 * @param onClickListener
	 */
	@Deprecated
	public void setOnCancelListener(DialogInterface.OnClickListener onClickListener) {
		this.listener = onClickListener;
	}

	public interface ProgressDialogClickListener {
		void onCancel(String tag, Object object);
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		int title = getArguments().getInt("title");
		int button = getArguments().getInt("button");
		int total = getArguments().getInt("total", 0);

		final String tag = this.getTag();

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_progress_horizontal, null);

		progressBar = dialogView.findViewById(R.id.progress);
		progressPercent = dialogView.findViewById(R.id.progress_percent);

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme()).setCancelable(false);
		builder.setView(dialogView);

		if (title != -1) {
			builder.setTitle(title);
		}

		max = total;
		if (max == 0) {
			max = 100;
		}
		progressBar.setMax(max);
		setProgress(0);

		if (button != 0) {
			builder.setPositiveButton(getString(button), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (listener != null) {
						listener.onClick(dialog, which);
					}
					if (callback != null) {
						callback.onCancel(tag, object);
					}
				}
			});
		}

		AlertDialog progressDialog = builder.create();

		setCancelable(false);

		return progressDialog;
	}

	/**
	 * Updates progress bar. Do not call this directly, use {@link ch.threema.app.utils.DialogUtil#updateProgress(FragmentManager, String, int)} instead!
	 * @param progress
	 */
	@UiThread
	public void setProgress(int progress) {
		if (progressBar != null && progressPercent != null) {
			double percent = (double) progress / (double) max;
			SpannableString tmp = new SpannableString(mProgressPercentFormat.format(percent));
			tmp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
					0, tmp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			progressPercent.setText(tmp);
			progressBar.setProgress(progress);
		}
	}
}
