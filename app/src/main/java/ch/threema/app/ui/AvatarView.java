package ch.threema.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import ch.threema.app.R;

public class AvatarView extends FrameLayout {
    private ImageView avatar, badge;

    public AvatarView(Context context) {
        super(context);
        init(context);
    }

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AvatarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater inflater = (LayoutInflater) context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.avatar_view, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        avatar = this.findViewById(R.id.avatar);
        badge = this.findViewById(R.id.avatar_badge);

        badge.setVisibility(GONE);
    }

    public void setImageResource(@DrawableRes int resource) {
        avatar.setImageResource(resource);
        avatar.requestLayout();
    }

    public void setImageBitmap(Bitmap bitmap) {
        avatar.setImageBitmap(bitmap);
        avatar.requestLayout();
    }

    public void setImageDrawable(Drawable drawable) {
        avatar.setImageDrawable(drawable);
        avatar.requestLayout();
    }

    /**
     * This returns the avatar image view. This is mainly needed for glide to directly set the avatars.
     *
     * @return the image view of the avatar drawable
     */
    public ImageView getAvatarView() {
        return avatar;
    }

    public void setBadgeVisible(boolean visibile) {
        badge.setVisibility(visibile ? VISIBLE : GONE);
    }
}
