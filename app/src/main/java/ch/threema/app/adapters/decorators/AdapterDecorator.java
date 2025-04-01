/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

import androidx.annotation.AnyThread;

import androidx.annotation.NonNull;
import ch.threema.app.ui.listitemholder.AbstractListItemHolder;
import ch.threema.app.utils.RuntimeUtil;

abstract class AdapterDecorator {
    @NonNull
    private final Context context;
    private transient ListView inListView = null;

    protected AdapterDecorator(@NonNull Context context) {
        this.context = context;
    }

    @NonNull
    protected Context getContext() {
        return this.context;
    }

    final public void decorate(AbstractListItemHolder holder, int position) {
        this.configure(holder, position);
    }

    protected boolean showHide(View view, boolean show) {
        if (view != null) {
            if (show) {
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
            return true;
        }

        return false;
    }

    @AnyThread
    protected void invalidate(final AbstractListItemHolder holder, final int position) {
        RuntimeUtil.runOnUiThread(() -> {
            if (holder != null && holder.position == position) {
                configure(holder, position);
            }
        });
    }

    abstract protected void configure(AbstractListItemHolder holder, int position);

    public void setInListView(ListView inListView) {
        this.inListView = inListView;
    }

    protected boolean isInChoiceMode() {
        return this.inListView != null && (this.inListView.getChoiceMode() == AbsListView.CHOICE_MODE_MULTIPLE
            || this.inListView.getChoiceMode() == AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
    }
}
