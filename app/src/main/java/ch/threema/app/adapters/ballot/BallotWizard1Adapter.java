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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.storage.models.ballot.BallotChoiceModel;

/**
 *
 */
public class BallotWizard1Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

	public interface OnChoiceListener {
		void onRemoveClicked(int position);
	}

	private static class BallotAdminChoiceItemHolder extends RecyclerView.ViewHolder {

		public TextView name;
		public ImageView removeButton;

		public BallotAdminChoiceItemHolder(@NonNull View itemView) {
			super(itemView);
			name = itemView.findViewById(R.id.choice_name_readonly);
			removeButton = itemView.findViewById(R.id.remove_button);
		}

		public void bind(BallotChoiceModel choiceModel, OnChoiceListener onChoiceListener) {
			if (choiceModel != null) {
				name.setText(choiceModel.getName());
				if (canEdit(choiceModel)) {
					removeButton.setOnClickListener(view -> {
						if (onChoiceListener != null) {
							onChoiceListener.onRemoveClicked(getAdapterPosition());
						}
					});
					removeButton.setVisibility(View.VISIBLE);
				} else {
					removeButton.setVisibility(View.GONE);
				}
			}
		}

		private boolean canEdit(BallotChoiceModel choiceModel) {
			return choiceModel.getId() <= 0;
		}
	}

	private final List<BallotChoiceModel> values;
	private OnChoiceListener onChoiceListener;

	public BallotWizard1Adapter(List<BallotChoiceModel> values) {
		this.values = values;
	}

	public BallotWizard1Adapter setOnChoiceListener(OnChoiceListener onChoiceListener) {
		this.onChoiceListener = onChoiceListener;
		return this;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View view = inflater.inflate(R.layout.item_ballot_wizard1, parent, false);
		return new BallotAdminChoiceItemHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		BallotAdminChoiceItemHolder viewHolder = (BallotAdminChoiceItemHolder) holder;
		viewHolder.bind(values.get(position), onChoiceListener);
	}

	@Override
	public int getItemCount() {
		return values.size();
	}
}
