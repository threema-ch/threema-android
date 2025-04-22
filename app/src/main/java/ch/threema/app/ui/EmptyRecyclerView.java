/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import org.msgpack.core.annotations.Nullable;

import java.lang.ref.WeakReference;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Taken from <a href="http://stackoverflow.com/questions/27414173/equivalent-of-listview-setemptyview-in-recyclerview/27801394#27801394">equivalent-of-listview-setemptyview-in-recyclerview</a>
 */
public class EmptyRecyclerView extends RecyclerView {
    private int numHeadersAndFooters = 0;
    private WeakReference<View> emptyViewReference;
    final private AdapterDataObserver observer = new AdapterDataObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            checkIfEmpty();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            super.onItemRangeInserted(positionStart, itemCount);
            checkIfEmpty();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            super.onItemRangeRemoved(positionStart, itemCount);
            checkIfEmpty();
        }
    };

    public EmptyRecyclerView(Context context) {
        super(context);
    }

    public EmptyRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EmptyRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    void checkIfEmpty() {
        if (emptyViewReference != null && emptyViewReference.get() != null && getAdapter() != null) {
            final boolean emptyViewVisible = getAdapter().getItemCount() == numHeadersAndFooters;
            emptyViewReference.get().setVisibility(emptyViewVisible ? VISIBLE : GONE);
            setVisibility(emptyViewVisible ? INVISIBLE : VISIBLE);
        }
    }

    @Override
    public void setAdapter(Adapter adapter) {
        final Adapter oldAdapter = getAdapter();
        if (oldAdapter != null) {
            oldAdapter.unregisterAdapterDataObserver(observer);
        }
        if (adapter != null) {
            adapter.registerAdapterDataObserver(observer);
        }
        super.setAdapter(adapter);
        checkIfEmpty();
    }

    public void setEmptyView(View emptyView) {
        this.emptyViewReference = new WeakReference<>(emptyView);
        checkIfEmpty();
    }

    public void clearEmptyView() {
        if (this.emptyViewReference != null && this.emptyViewReference.get() != null) {
            emptyViewReference.get().setVisibility(GONE);
        }
        setVisibility(INVISIBLE);
    }

    /**
     * Specify how many header or footer views this recyclerview has. This number will be considered when determining the "empty" status of the list
     *
     * @param numHeadersAndFooters Number of headers and / or footers
     */
    public void setNumHeadersAndFooters(int numHeadersAndFooters) {
        this.numHeadersAndFooters = numHeadersAndFooters;
        checkIfEmpty();
    }

    public @Nullable View getEmptyView() {
        return this.emptyViewReference.get();
    }
}
