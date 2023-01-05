/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2023 Threema GmbH
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
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDialog;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ch.threema.app.R;

public class WizardDialog extends ThreemaDialogFragment {
	private static final String ARG_TITLE = "title";
	private static final String ARG_TITLE_STRING = "titleString";
	private static final String ARG_POSITIVE = "positive";
	private static final String ARG_NEGATIVE = "negative";
	private static final String ARG_HIGHLIGHT = "highlight";

	private WizardDialogCallback callback;
	private Activity activity;
	public enum Highlight {
		POSITIVE,
		NEGATIVE,
		EQUAL,
		NONE
	}

	public static WizardDialog newInstance(int title, int positive, int negative, Highlight highlight) {
		WizardDialog dialog = new WizardDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_POSITIVE, positive);
		args.putInt(ARG_NEGATIVE, negative);
		args.putSerializable(ARG_HIGHLIGHT, highlight);
		dialog.setArguments(args);
		return dialog;
	}

	public static WizardDialog newInstance(int title, int positive) {
		WizardDialog dialog = new WizardDialog();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_POSITIVE, positive);
		dialog.setArguments(args);
		return dialog;
	}

	public static WizardDialog newInstance(String title, int positive) {
		WizardDialog dialog = new WizardDialog();
		Bundle args = new Bundle();
		args.putString(ARG_TITLE_STRING, title);
		args.putInt(ARG_POSITIVE, positive);
		dialog.setArguments(args);
		return dialog;
	}

	public interface WizardDialogCallback {
		void onYes(String tag, Object data);
		void onNo(String tag);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			callback = (WizardDialogCallback) getTargetFragment();
		} catch (ClassCastException e) {
			//
		}

		// called from an activity rather than a fragment
		if (callback == null) {
			if (!(activity instanceof WizardDialogCallback)) {
				throw new ClassCastException("Calling fragment must implement WizardDialogCallback interface");
			}
			callback = (WizardDialogCallback) activity;
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		int title = getArguments().getInt(ARG_TITLE, 0);
		String titleString = getArguments().getString(ARG_TITLE_STRING);
		int positive = getArguments().getInt(ARG_POSITIVE);
		int negative = getArguments().getInt(ARG_NEGATIVE, 0);
		Highlight highlight = (Highlight) getArguments().getSerializable(ARG_HIGHLIGHT);
		final String tag = this.getTag();

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_wizard, null);
		final TextView titleText = dialogView.findViewById(R.id.wizard_dialog_title);
		final Button positiveButton = dialogView.findViewById(R.id.wizard_yes);
		final Button negativeButton = dialogView.findViewById(R.id.wizard_no);

		if (title != 0) {
			titleText.setText(title);
		} else {
			titleText.setText(titleString);
		}
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), R.style.Threema_Dialog_Wizard);
		builder.setView(dialogView);
		positiveButton.setText(positive);
		positiveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
				callback.onYes(tag, object);
			}
		});
		if (negative != 0) {
			negativeButton.setText(negative);
			negativeButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					dismiss();
					callback.onNo(tag);
				}
			});
		} else {
			negativeButton.setVisibility(View.GONE);
		}

		if (highlight != null) {
			switch (highlight) {
				case NONE:
					hightlightButton(negativeButton, false);
					hightlightButton(positiveButton, false);
				case EQUAL:
					hightlightButton(negativeButton, true);
					hightlightButton(positiveButton, true);
					break;
				case NEGATIVE:
					hightlightButton(negativeButton, true);
					hightlightButton(positiveButton, false);
					break;
				case POSITIVE:
				default:
					positiveButton.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.selector_button_green, null));
					positiveButton.setTextColor(getResources().getColor(R.color.wizard_button_text_inverse));
					break;
			}
		}

		setCancelable(false);

		return builder.create();
	}

	private void hightlightButton(Button button, boolean hightlight) {
		if (hightlight) {
			button.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.selector_button_green, null));
			button.setTextColor(getResources().getColor(R.color.wizard_button_text_inverse));
		}
		else {
			button.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.selector_button_green_inverse, null));
			button.setTextColor(getResources().getColor(R.color.wizard_button_text));
		}
	}

	@Override
	public void onCancel(DialogInterface dialogInterface) {
		callback.onNo(this.getTag());
	}
}
