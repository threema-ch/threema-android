/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

package ch.threema.app.adapters;

import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Based on http://www.jayway.com/2014/12/23/android-recyclerview-simple-list/
 */
public abstract class AbstractRecyclerAdapter<V, K extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<K> {
	protected List<V> data = new ArrayList<V>();
	private V selectedItem = null;

	@NonNull
	@Override
	public abstract K onCreateViewHolder(@NonNull ViewGroup viewGroup, int i);

	@Override
	public abstract void onBindViewHolder(@NonNull K k, int i);

	@Override
	public int getItemCount() {
		return data.size();
	}

	public void setData(final List<V> data) {
		this.setData(data, null);
	}

	public void setData(final List<V> data, final List<V> changedPositionData) {
		// Remove all deleted items.
		for (int i = this.data.size() - 1; i >= 0; --i) {
			if (getLocation(data, this.data.get(i)) < 0) {
				deleteEntity(i);
			}
		}

		// Add and move items.
		for (int i = 0; i < data.size(); ++i) {
			V entity = data.get(i);
			int loc = getLocation(this.data, entity);
			if (loc < 0) {
				addEntity(i, entity);
			}
			else if (loc != i) {
				moveEntity(loc, i);
				notifyItemChanged(i);
			}
		}

		//and update changed position data
		if(changedPositionData != null) {
			for (V entity : changedPositionData) {
				int loc = getLocation(this.data, entity);
				if (loc >= 0) {
					notifyItemChanged(loc);
				}
			}
		}
	}

	private int getLocation(List<V> data, V entity) {
		for (int j = 0; j < data.size(); ++j) {
			V newEntity = data.get(j);
			if (entity != null && entity.equals(newEntity)) {
				return j;
			}
		}
		return -1;
	}

	public void addEntity(int i, V entity) {
		data.add(i, entity);
		notifyItemInserted(i);
	}

	public void deleteEntity(int i) {
		data.remove(i);
		notifyItemRemoved(i);
	}

	public void moveEntity(int i, int loc) {
		move(data, i, loc);
		notifyItemMoved(i, loc);
	}

	private void move(List<V> data, int a, int b) {
		V temp = data.remove(a);
		if (b <= data.size()) {
			data.add(b, temp);
		}
	}

	public void setSelected(V entity) {
		if (selectedItem != null) {
			notifyItemChanged(getLocation(data, selectedItem));
		}
		selectedItem = entity;
		notifyItemChanged(getLocation(data, entity));
	}

	public V getSelected() {
		return selectedItem;
	}

	public void clearSelection() {
		notifyItemChanged(getLocation(data, selectedItem));
		selectedItem = null;
	}

	public V getEntity(int pos) {
		if (pos >= 0 && pos < this.data.size()) {
			return this.data.get(pos);
		}
		return null;
	}

	public void setEntity(int pos, V entity) {
		if (entity == null) {
			return;
		}
		V oldEntity = this.getEntity(pos);
		if (oldEntity != null && entity != oldEntity) {
			this.data.set(pos, entity);
			notifyItemChanged(pos);
		}
	}
}
