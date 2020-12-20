/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import ch.threema.app.R;
import ch.threema.storage.models.ballot.BallotChoiceModel;

/**
 *
 */
public class BallotWizard1ListAdapter extends ArrayAdapter<BallotChoiceModel> {

	public interface OnChoiceListener {
		void onRemoveClicked(BallotChoiceModel choiceModel);
	}

	private static class BallotAdminChoiceItemHolder {
		public TextView name;
		public ImageView removeButton;
	}

	private Context context;
	private List<BallotChoiceModel> values;
	private OnChoiceListener onChoiceListener;

	public BallotWizard1ListAdapter(Context context, List<BallotChoiceModel> values) {
		super(context, R.layout.item_ballot_wizard1, values);
		this.context = context;
		this.values = values;
	}

	public BallotWizard1ListAdapter setOnChoiceListener(OnChoiceListener onChoiceListener) {
		this.onChoiceListener = onChoiceListener;
		return this;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		View itemView = convertView;
		final BallotAdminChoiceItemHolder holder;

		if (convertView == null) {
			holder = new BallotAdminChoiceItemHolder();
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			itemView = inflater.inflate(R.layout.item_ballot_wizard1, parent, false);

			holder.name = itemView.findViewById(R.id.choice_name_readonly);
			holder.removeButton = itemView.findViewById(R.id.remove_button);

			itemView.setTag(holder);
		}
		else {
			holder = (BallotAdminChoiceItemHolder) itemView.getTag();
		}

		final BallotChoiceModel choiceModel = values.get(position);

		if(choiceModel != null) {
			if(holder.name != null) {
				holder.name.setText(choiceModel.getName());
			}

			if(holder.removeButton != null) {
				if(canEdit(position)) {
					holder.removeButton.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							if (onChoiceListener != null) {
								onChoiceListener.onRemoveClicked(choiceModel);
							}
						}
					});
					holder.removeButton.setVisibility(View.VISIBLE);
				}
				else {
					holder.removeButton.setVisibility(View.GONE);
				}
			}
		}

		return itemView;
	}

	public boolean canEdit(int pos) {
		synchronized (values) {
			return pos >= 0 && pos < values.size() && values.get(pos).getId() <= 0;
		}
	}
}
