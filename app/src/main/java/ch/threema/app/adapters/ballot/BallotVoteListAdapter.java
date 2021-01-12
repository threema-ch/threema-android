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

package ch.threema.app.adapters.ballot;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.ui.CountBoxView;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ballot.BallotChoiceModel;

/**
 *
 */
public class BallotVoteListAdapter extends ArrayAdapter<BallotChoiceModel> {

	private Context context;
	private List<BallotChoiceModel> values;
	private final Map<Integer, Integer> selected;
	private final boolean readonly;
	private final boolean multipleChoice;
	private final  boolean showVoting;

	public BallotVoteListAdapter(Context context,
								 List<BallotChoiceModel> values,
								 Map<Integer, Integer> selected,
								 boolean readonly,
								 boolean multipleChoice,
								 boolean showVoting) {
		super(context, R.layout.item_ballot_choice_vote, values);

		this.context = context;
		this.readonly = readonly;
		this.multipleChoice = multipleChoice;
		this.showVoting = showVoting;
		this.values = values;
		this.selected = selected;
	}

	private static class BallotAdminChoiceItemHolder {
		public TextView name;
		public CountBoxView voteCount;
		public RadioButton radioButton;
		public CheckBox checkBox;
		int originalPosition;
	}

	@NonNull
	@Override
	public View getView(int position, View convertView, @NonNull ViewGroup parent) {
		CheckableRelativeLayout itemView = (CheckableRelativeLayout) convertView;
		BallotAdminChoiceItemHolder holder;

		if (convertView == null) {
			holder = new BallotAdminChoiceItemHolder();
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			itemView = (CheckableRelativeLayout) inflater.inflate(R.layout.item_ballot_choice_vote, parent, false);

			holder.name = itemView.findViewById(R.id.choice_name);
			holder.voteCount = itemView.findViewById(R.id.vote_count);
			holder.radioButton = itemView.findViewById(R.id.choice_radio);
			holder.checkBox = itemView.findViewById(R.id.choice_checkbox);

			itemView.setTag(holder);
		}
		else {
			holder = (BallotAdminChoiceItemHolder) itemView.getTag();
		}

		itemView.setOnCheckedChangeListener(null);

		final BallotChoiceModel choiceModel = values.get(position);
		holder.originalPosition = position;

		if(choiceModel != null) {
			if(holder.name != null) {
				holder.name.setText(choiceModel.getName());
			}
			if(holder.voteCount != null) {
				holder.voteCount.setVisibility(this.showVoting ? View.VISIBLE : View.GONE);
				if(this.showVoting) {
					long c = 0;
					try {
						c = ThreemaApplication.getServiceManager().getBallotService().getVotingCount(choiceModel);
					} catch (Exception ignored) {}
					holder.voteCount.setText(String.valueOf(c));
					holder.voteCount.setVisibility(View.VISIBLE);
				}
			}
			itemView.setChecked(this.isSelected(choiceModel));
		}

		if(TestUtil.required(holder.checkBox, holder.radioButton)) {
			holder.radioButton.setVisibility(!this.multipleChoice ? View.VISIBLE : View.GONE);
			holder.radioButton.setEnabled(!this.readonly);

			holder.checkBox.setVisibility(this.multipleChoice ? View.VISIBLE : View.GONE);
			holder.checkBox.setEnabled(!this.readonly);
		}

		if (!this.readonly) {
			itemView.setOnCheckedChangeListener((checkableView, isChecked) -> {
				select(values.get(((BallotAdminChoiceItemHolder) checkableView.getTag()).originalPosition), isChecked);
			});
		}

		return itemView;
	}

	public Map<Integer, Integer> getSelectedChoices() {
		return this.selected;
	}

	public boolean isSelected(final BallotChoiceModel model) {
		synchronized (this.selected) {
			int k = model.getId();
			return selected.containsKey(k) && selected.get(k) == 1;
		}
	}

	public void select(final BallotChoiceModel model, boolean select) {
		synchronized (this.selected) {
			int id = model.getId();

			if (!this.multipleChoice) {
				this.selected.clear();
				this.selected.put(id, 1);
				notifyDataSetChanged();
			} else {
				this.selected.put(id, (select ? 1 : 0));
			}
		}
	}
}
