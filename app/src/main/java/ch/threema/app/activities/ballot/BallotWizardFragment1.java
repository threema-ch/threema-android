/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.adapters.ballot.BallotWizard1Adapter;
import ch.threema.app.dialogs.FormatTextEntryDialog;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ballot.BallotChoiceModel;

import static com.google.android.material.timepicker.TimeFormat.CLOCK_12H;
import static com.google.android.material.timepicker.TimeFormat.CLOCK_24H;

public class BallotWizardFragment1 extends BallotWizardFragment implements BallotWizardActivity.BallotWizardCallback, BallotWizard1Adapter.OnChoiceListener {
	private static final String DIALOG_TAG_SELECT_DATE = "selectDate";
	private static final String DIALOG_TAG_SELECT_TIME = "selectTime";
	private static final String DIALOG_TAG_SELECT_DATETIME = "selectDateTime";
	private static final String DIALOG_TAG_EDIT_ANSWER = "editAnswer";

	private RecyclerView choiceRecyclerView;
	private List<BallotChoiceModel> ballotChoiceModelList;
	private BallotWizard1Adapter listAdapter = null;
	private ImageButton createChoiceButton;
	private EditText createChoiceEditText;
	private Long originalTimeInUtc = null;
	private LinearLayoutManager choiceRecyclerViewLayoutManager;
	private int lastVisibleBallotPosition;
	private int editItemPosition = -1;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {

		ViewGroup rootView = (ViewGroup) inflater.inflate(
				R.layout.fragment_ballot_wizard1, container, false);

		this.choiceRecyclerView = rootView.findViewById(R.id.ballot_list);
		this.choiceRecyclerViewLayoutManager = new LinearLayoutManager(getActivity());
		this.choiceRecyclerView.setLayoutManager(choiceRecyclerViewLayoutManager);
		this.choiceRecyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
				if (bottom < oldBottom) {
					choiceRecyclerView.post(new Runnable() {
						@Override
						public void run() {
							try {
								choiceRecyclerView.smoothScrollToPosition(lastVisibleBallotPosition);
							} catch (IllegalArgumentException ignored) { }
						}
					});
				}
			}
		});
		this.choiceRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
				super.onScrollStateChanged(recyclerView, newState);
				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					lastVisibleBallotPosition = choiceRecyclerViewLayoutManager.findLastVisibleItemPosition();
				}
			}
		});
		int moveUpDown = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
		ItemTouchHelper.Callback swipeCallback = new ItemTouchHelper.SimpleCallback(moveUpDown, 0) {
			@Override
			public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
				int fromPosition = viewHolder.getAdapterPosition();
				int toPosition = target.getAdapterPosition();
				if (fromPosition < toPosition) {
					for (int i = fromPosition; i < toPosition; i++) {
						Collections.swap(ballotChoiceModelList, i, i + 1);
					}
				} else {
					for (int i = fromPosition; i > toPosition; i--) {
						Collections.swap(ballotChoiceModelList, i, i - 1);
					}
				}
				listAdapter.notifyItemMoved(fromPosition, toPosition);
				return true;
			}

			@Override
			public boolean isItemViewSwipeEnabled() {
				return false;
			}

			@Override
			public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
		};
		ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeCallback);
		itemTouchHelper.attachToRecyclerView(choiceRecyclerView);

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

		ImageButton addDateButton = rootView.findViewById(R.id.add_date);
		addDateButton.setOnClickListener(v -> {
			final MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
				.setTitleText(R.string.select_date)
				.setSelection(originalTimeInUtc != null ? originalTimeInUtc : MaterialDatePicker.todayInUtcMilliseconds())
				.build();
			datePicker.addOnPositiveButtonClickListener(selection -> {
				Long date = datePicker.getSelection();
				if (date != null) {
					originalTimeInUtc = date;
					createDateChoice(false);
				}
			});
			if (isAdded()) {
				datePicker.show(getParentFragmentManager(), DIALOG_TAG_SELECT_DATE);
			}
		});

		ImageButton addDateTimeButton = rootView.findViewById(R.id.add_time);
		addDateTimeButton.setOnClickListener(v -> {
			final MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
				.setTitleText(R.string.select_date)
				.setSelection(originalTimeInUtc != null ? originalTimeInUtc : MaterialDatePicker.todayInUtcMilliseconds())
				.build();
			datePicker.addOnPositiveButtonClickListener(selection -> {
				Long date = datePicker.getSelection();
				if (date != null) {
					originalTimeInUtc = date;
					final MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
						.setTitleText(R.string.select_time)
						.setHour(0)
						.setMinute(0)
						.setTimeFormat(DateFormat.is24HourFormat(getContext()) ? CLOCK_24H : CLOCK_12H)
						.build();
					timePicker.addOnPositiveButtonClickListener(v1 -> {
						originalTimeInUtc += timePicker.getHour() * DateUtils.HOUR_IN_MILLIS;
						originalTimeInUtc += timePicker.getMinute() * DateUtils.MINUTE_IN_MILLIS;
						createDateChoice(true);
					});
					if (isAdded()) {
						timePicker.show(getParentFragmentManager(), DIALOG_TAG_SELECT_TIME);
					}
				}
			});
			if (isAdded()) {
				datePicker.show(getParentFragmentManager(), DIALOG_TAG_SELECT_DATETIME);
			}
		});

		initAdapter();

		return rootView;
	}

	private void createDateChoice(boolean showTime) {
		if (createChoiceEditText != null) {
			int format = DateUtils.FORMAT_UTC | DateUtils.FORMAT_ABBREV_WEEKDAY | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
			if (showTime) {
				format |= DateUtils.FORMAT_SHOW_TIME;
			}
			if (!isSameYear(originalTimeInUtc)) {
				format |= DateUtils.FORMAT_SHOW_YEAR;
			}
			String dateString = DateUtils.formatDateTime(getContext(), originalTimeInUtc, format);
			createChoiceEditText.setText(dateString);
			createChoice();
		}
	}

	private void initAdapter() {
		if(this.getBallotActivity() != null) {
			this.ballotChoiceModelList = this.getBallotActivity().getBallotChoiceModelList();
			this.listAdapter = new BallotWizard1Adapter(this.ballotChoiceModelList);
			this.listAdapter.setOnChoiceListener(this);
			this.choiceRecyclerView.setAdapter(this.listAdapter);
		}
	}

	@Override
	public void onEditClicked(int position) {
		this.editItemPosition = position;
		FormatTextEntryDialog alertDialog = FormatTextEntryDialog.newInstance(
			R.string.edit_answer, 0,
			R.string.ok,
			R.string.cancel,
			ballotChoiceModelList.get(position).getName(),
			5, new FormatTextEntryDialog.FormatTextEntryDialogClickListener() {
				@Override
				public void onYes(String text) {
					if (!TestUtil.empty(text)) {
						synchronized (ballotChoiceModelList) {
							if (editItemPosition != -1) {
								ballotChoiceModelList.get(editItemPosition).setName(text);
								listAdapter.notifyItemChanged(editItemPosition);
							}
							editItemPosition = -1;
						}
					}
					createChoiceEditText.requestFocus();
				}

				@Override
				public void onNo() {
					createChoiceEditText.requestFocus();
				}
			});
		alertDialog.show(getParentFragmentManager(), DIALOG_TAG_EDIT_ANSWER);
	}

	@Override
	public void onRemoveClicked(int position) {
		synchronized (ballotChoiceModelList) {
			ballotChoiceModelList.remove(position);
			listAdapter.notifyItemRemoved(position);
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
				int insertPosition = this.ballotChoiceModelList.size() - 1;
				listAdapter.notifyItemInserted(insertPosition);
				choiceRecyclerView.smoothScrollToPosition(insertPosition);
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

	private boolean isSameYear(long dateInMillis) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(dateInMillis);
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
