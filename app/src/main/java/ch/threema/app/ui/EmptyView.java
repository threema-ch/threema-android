package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import ch.threema.app.R;

public class EmptyView extends LinearLayout {

    private static final int NO_IMAGE_RES = -1;

    private final TextView emptyText;
    private final ImageView emptyImageView;
    private final CircularProgressIndicator loadingView;

    @DrawableRes
    private int imageRes = NO_IMAGE_RES;

    public EmptyView(Context context) {
        this(context, null, 0);
    }

    public EmptyView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmptyView(Context context, int parentOffset) {
        this(context, null, parentOffset);
    }

    public EmptyView(Context context, AttributeSet attrs, int parentOffset) {
        super(context, attrs);

        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.CENTER);
        int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics());
        setPadding(paddingPx, parentOffset, paddingPx, 0);
        setLayoutParams(
            new ListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        );

        LayoutInflater.from(context).inflate(R.layout.view_empty, this, true);
        setVisibility(View.GONE);

        this.loadingView = (CircularProgressIndicator) getChildAt(0);
        this.emptyImageView = (ImageView) getChildAt(1);
        this.emptyText = (TextView) getChildAt(2);
    }

    public void setup(@StringRes int labelRes) {
        this.emptyText.setText(labelRes);
    }

    public void setup(String label) {
        this.emptyText.setText(label);
    }

    public void setup(@StringRes int labelRes, @DrawableRes int imageRes) {
        this.imageRes = imageRes;
        this.emptyImageView.setImageResource(imageRes);
        this.emptyImageView.setVisibility(VISIBLE);
        this.emptyText.setText(labelRes);
    }

    public void setColorsInt(@ColorInt int background, @ColorInt int foreground) {
        this.setBackgroundColor(background);
        this.emptyText.setTextColor(foreground);
    }

    public void setLoading(boolean isLoading) {
        this.loadingView.setVisibility(isLoading ? VISIBLE : GONE);
        this.emptyText.setVisibility(isLoading ? GONE : VISIBLE);
        this.emptyImageView.setVisibility((isLoading || !hasImageRes()) ? GONE : VISIBLE);
    }

    private boolean hasImageRes() {
        return this.imageRes != NO_IMAGE_RES;
    }
}
