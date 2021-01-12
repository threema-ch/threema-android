/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.DialogFragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.LocaleService;
import ch.threema.app.threemasafe.ThreemaSafeService;
import ch.threema.app.utils.DialogUtil;

public class WizardSafeSearchPhoneDialog extends DialogFragment implements SelectorDialog.SelectorDialogClickListener {

	private static final String DIALOG_TAG_PROGRESS = "pro";
	private static final String DIALOG_TAG_SELECT_ID = "se";

	private WizardSafeSearchPhoneDialogCallback callback;
	private Activity activity;
	private ThreemaSafeService threemaSafeService;
	private LocaleService localeService;

	private EditText emailEditText, phoneEditText;

	private ArrayList<String> matchingIDs;

	public static WizardSafeSearchPhoneDialog newInstance() {
		return new WizardSafeSearchPhoneDialog();
	}

	public interface WizardSafeSearchPhoneDialogCallback {
		void onYes(String tag, String id);
		void onNo(String tag);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			callback = (WizardSafeSearchPhoneDialogCallback) getTargetFragment();
		} catch (ClassCastException e) {
			//
		}

		// called from an activity rather than a fragment
		if (callback == null) {
			if (!(activity instanceof WizardSafeSearchPhoneDialogCallback)) {
				throw new ClassCastException("Calling fragment must implement WizardSafeSearchPhoneDialogCallback interface");
			}
			callback = (WizardSafeSearchPhoneDialogCallback) activity;
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		final String tag = this.getTag();

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_wizard_safe_search_phone, null);
		Button positiveButton = dialogView.findViewById(R.id.ok);
		final Button negativeButton = dialogView.findViewById(R.id.cancel);

		phoneEditText = dialogView.findViewById(R.id.safe_phone);
		emailEditText = dialogView.findViewById(R.id.safe_email);

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), R.style.Threema_Dialog_Wizard);
		builder.setView(dialogView);

		try {
			threemaSafeService = ThreemaApplication.getServiceManager().getThreemaSafeService();
			localeService = ThreemaApplication.getServiceManager().getLocaleService();
		} catch (Exception e) {
			dismiss();
		}

		positiveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String phone = null, email = null;
				if (phoneEditText.getText() != null) {
					phone = localeService.getNormalizedPhoneNumber(phoneEditText.getText().toString());
				}
				if (emailEditText.getText() != null) {
					email = emailEditText.getText().toString();
				}
				if (phone != null || email != null) {
					searchID(phone, email);
				} else {
					dismiss();
					callback.onYes(tag, null);
				}
			}
		});
		negativeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
				callback.onNo(tag);
			}
		});

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			phoneEditText.addTextChangedListener(new PhoneNumberFormattingTextWatcher(localeService.getCountryIsoCode()));
		} else {
			phoneEditText.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
		}

		setCancelable(false);

		return builder.create();
	}

	private void searchID(String phone, String email) {
		new SearchIdTask(this).execute(phone, email);
	}

	@Override
	public void onCancel(DialogInterface dialogInterface) {
		callback.onNo(this.getTag());
	}

	private static class SearchIdTask extends AsyncTask<String, Void, ArrayList<String>> {
		private WeakReference<WizardSafeSearchPhoneDialog> contextReference;

		SearchIdTask(WizardSafeSearchPhoneDialog context) {
			contextReference = new WeakReference<>(context);
		}

		@Override
		protected void onPreExecute() {
			WizardSafeSearchPhoneDialog dialog = contextReference.get();
			if (dialog == null || dialog.isRemoving() || dialog.isDetached()) return;

			GenericProgressDialog.newInstance(R.string.safe_id_lookup, R.string.please_wait).show(dialog.getFragmentManager(), DIALOG_TAG_PROGRESS);
		}

		@Override
		protected ArrayList<String> doInBackground(String... params) {
			return contextReference.get().threemaSafeService.searchID(params[0], params[1]);
		}

		@Override
		protected void onPostExecute(ArrayList<String> ids) {
			final WizardSafeSearchPhoneDialog dialog = contextReference.get();
			if (dialog == null || dialog.isRemoving() || dialog.isDetached()) return;

			dialog.matchingIDs = ids;

			DialogUtil.dismissDialog(dialog.getFragmentManager(), DIALOG_TAG_PROGRESS, true);
			if (ids != null) {
				if (ids.size() == 1) {
					dialog.callback.onYes(dialog.getTag(), ids.get(0));
					dialog.dismiss();
				} else {
					SelectorDialog selectorDialog = SelectorDialog.newInstance(dialog.getString(R.string.safe_select_id), ids, null);
					selectorDialog.setTargetFragment(dialog, 0);
					selectorDialog.show(dialog.getFragmentManager(), DIALOG_TAG_SELECT_ID);
				}
			} else {
				Toast.makeText(dialog.getActivity(), R.string.safe_no_id_found, Toast.LENGTH_LONG).show();
			}
		}
	}

	/*
	 * Selector callbacks
	 */

	@Override
	public void onClick(String tag, int which, Object data) {
		callback.onYes(getTag(), matchingIDs.get(which));
		dismiss();
	}

	@Override
	public void onCancel(String tag) {}

	@Override
	public void onNo(String tag) {}
}
