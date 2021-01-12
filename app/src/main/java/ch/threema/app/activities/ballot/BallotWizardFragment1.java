/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.activities.ballot;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.adapters.ballot.BallotWizard1ListAdapter;
import ch.threema.app.dialogs.DateSelectorDialog;
import ch.threema.app.dialogs.TimeSelectorDialog;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ballot.BallotChoiceModel;

public class BallotWizardFragment1 extends BallotWizardFragment implements DateSelectorDialog.DateSelectorDialogListener, TimeSelectorDialog.TimeSelectorDialogListener, BallotWizardActivity.BallotWizardCallback {
	private static final String DIALOG_TAG_SELECT_DATE = "selectDate";
	private static final String DIALOG_TAG_SELECT_TIME = "selectTime";
	private static final String DIALOG_TAG_SELECT_DATETIME = "selectDateTime";

	private ListView choiceListView;
	private List<BallotChoiceModel> ballotChoiceModelList;
	private BallotWizard1ListAdapter listAdapter = null;
	private ImageButton createChoiceButton;
	private ImageButton addDateButton, addDateTimeButton;
	private EditText createChoiceEditText;
	private Date originalDate = null;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {

		ViewGroup rootView = (ViewGroup) inflater.inflate(
				R.layout.fragment_ballot_wizard1, container, false);

		this.choiceListView = rootView.findViewById(R.id.ballot_list);
		this.createChoiceEditText = rootView.findViewById(R.id.create_choice_name);
		this.createChoiceEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == getResources().getInteger(R.integer.ime_wizard_add_choice) || actionId == EditorInfo.IME_ACTION_NEXT || (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
					createChoice();
				}
				return false;
			}
		});
		this.createChoiceEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				if (s != null && createChoiceButton != null) {
					createChoiceButton.setEnabled(s.length() > 0);
				}
			}
		});
		this.createChoiceButton = rootView.findViewById(R.id.create_choice);
		this.createChoiceButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				createChoice();
			}
		});
		this.createChoiceButton.setEnabled(false);

		this.addDateButton = rootView.findViewById(R.id.add_date);
		this.addDateButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				DateSelectorDialog dialog = DateSelectorDialog.newInstance(originalDate);
				dialog.setTargetFragment(BallotWizardFragment1.this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_SELECT_DATE);
			}
		});

		this.addDateTimeButton = rootView.findViewById(R.id.add_time);
		this.addDateTimeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				DateSelectorDialog dialog = DateSelectorDialog.newInstance(originalDate);
				dialog.setTargetFragment(BallotWizardFragment1.this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_SELECT_DATETIME);
			}
		});

		initAdapter();

		return rootView;
	}

	private void initAdapter() {
		if(this.getBallotActivity() != null) {
			this.ballotChoiceModelList = this.getBallotActivity().getBallotChoiceModelList();
			this.listAdapter = new BallotWizard1ListAdapter(getActivity(), this.ballotChoiceModelList);

			this.listAdapter.setOnChoiceListener(new BallotWizard1ListAdapter.OnChoiceListener() {
				@Override
				public void onRemoveClicked(BallotChoiceModel choiceModel) {
					synchronized (ballotChoiceModelList) {
						ballotChoiceModelList.remove(choiceModel);
						listAdapter.notifyDataSetChanged();
					}
				}
			});

			this.choiceListView.setAdapter(this.listAdapter);
		}
	}


	/**
	 * Create a new Choice with a Input Alert.
	 */
	private void createChoice() {
		if (TestUtil.required(this.createChoiceEditText.getText())) {
			String text = createChoiceEditText.getText().toString();
			if (!TestUtil.empty(text)) {
				createChoice(text.trim(), BallotChoiceModel.Type.Text);
				listAdapter.notifyDataSetChanged();
				createChoiceEditText.setText("");
				createChoiceEditText.post(new Runnable() {
					@Override
					public void run() {
						createChoiceEditText.requestFocus();
					}
				});
			} else {
				// show keyboard on empty click
				if(this.getBallotActivity() != null) {
					EditTextUtil.showSoftKeyboard(this.createChoiceEditText);
				}
			}
		}
	}

	public void saveUnsavedData() {
		createChoice();
	}

	private void createChoice(String name, BallotChoiceModel.Type type) {
		BallotChoiceModel choiceModel = new BallotChoiceModel();
		choiceModel.setName(name);
		choiceModel.setType(type);

		synchronized (this.ballotChoiceModelList) {
			this.ballotChoiceModelList.add(choiceModel);
		}
	}

	@Override
	void updateView() {
		initAdapter();
	}

	@Override
	public void onDateSet(String tag, Date date) {
		if (date != null) {
			originalDate = date;

			if (DIALOG_TAG_SELECT_DATETIME.equals(tag)) {
				TimeSelectorDialog dialog = TimeSelectorDialog.newInstance(date);
				dialog.setTargetFragment(BallotWizardFragment1.this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_SELECT_TIME);
			} else if (DIALOG_TAG_SELECT_DATE.equals(tag) && this.createChoiceEditText != null) {
				int format = DateUtils.FORMAT_ABBREV_WEEKDAY | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
				if (!isSameYear(date)) {
					format |= DateUtils.FORMAT_SHOW_YEAR;
				}
				String dateString = DateUtils.formatDateTime(getActivity(), date.getTime(), format);

				this.createChoiceEditText.setText(dateString);
				createChoice();
			}
		}
	}

	@Override
	public void onTimeSet(String tag, Date date) {
		if (this.createChoiceEditText != null && date != null) {
			// date and time
			int format = DateUtils.FORMAT_ABBREV_WEEKDAY | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME;
			if (!isSameYear(date)) {
				format |= DateUtils.FORMAT_SHOW_YEAR;
			}
			String dateString = DateUtils.formatDateTime(getActivity(), date.getTime(), format);
			this.createChoiceEditText.setText(dateString);
			createChoice();
		}
	}

	@Override
	public void onCancel(String tag, Date date) {}

	private boolean isSameYear(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		Calendar cal1 = Calendar.getInstance();

		return cal1.get(Calendar.YEAR) == cal.get(Calendar.YEAR);
	}

	@Override
	public void onMissingTitle() {
		/**/
	}

	@Override
	public void onPageSelected(int page) {
		if (page == 0) {
			this.createChoiceEditText.clearFocus();
			this.createChoiceEditText.setFocusableInTouchMode(false);
			this.createChoiceEditText.setFocusable(false);
		} else {
			this.createChoiceEditText.setFocusableInTouchMode(true);
			this.createChoiceEditText.setFocusable(true);
			this.createChoiceEditText.requestFocus();
		}
	}
}
