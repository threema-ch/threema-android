package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.view.View;

import androidx.annotation.AnyThread;
import ch.threema.app.ui.listitemholder.AbstractListItemHolder;
import ch.threema.app.utils.RuntimeUtil;

abstract class AdapterDecorator {

    final public void decorate(AbstractListItemHolder holder, Context context, int position) {
        this.configure(holder, context, position);
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
    protected void invalidate(final AbstractListItemHolder holder, final Context context, final int position) {
        RuntimeUtil.runOnUiThread(() -> {
            if (holder != null && holder.position == position) {
                configure(holder, context, position);
            }
        });
    }

    abstract protected void configure(AbstractListItemHolder holder, Context context, int position);

    abstract protected boolean isInChoiceMode();
}
