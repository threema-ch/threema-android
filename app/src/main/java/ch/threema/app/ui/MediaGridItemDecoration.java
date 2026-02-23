package ch.threema.app.ui;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class MediaGridItemDecoration extends RecyclerView.ItemDecoration {
    private int space;

    public MediaGridItemDecoration(int space) {
        this.space = space;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view,
                               RecyclerView parent, RecyclerView.State state) {

        outRect.left = space / 2;
        outRect.right = space / 2;
        outRect.bottom = space;
        // Add top margin only for the first item to avoid double space between items
        outRect.top = 0;
    }
}
