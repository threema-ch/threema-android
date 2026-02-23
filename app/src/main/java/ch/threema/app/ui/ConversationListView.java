package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

public final class ConversationListView extends ListView {

    public ConversationListView(Context context) {
        super(context);
    }

    public ConversationListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean isLastItemVisible() {
        // returns true if the last item in this list view is visible
        final int lastPos = getLastVisiblePosition();
        final int countPos = getCount();

        return (lastPos == countPos - 1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // called when size of view changes (e.g. soft keyboard appears or screen is rotated)
        super.onSizeChanged(w, h, oldw, oldh);

        if (isLastItemVisible()) {
            // only scroll to end of list if last item is visible - otherwise stay put
            setSelection(Integer.MAX_VALUE);
        }
    }

}
