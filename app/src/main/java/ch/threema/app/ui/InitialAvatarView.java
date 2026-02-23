package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import ch.threema.app.R;

public class InitialAvatarView extends FrameLayout {
    private TextView avatarInitials;

    public InitialAvatarView(Context context) {
        super(context);
        init(context);
    }

    public InitialAvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public InitialAvatarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater inflater = (LayoutInflater) context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.initial_avatar_view, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        avatarInitials = this.findViewById(R.id.avatar_initials);
    }

    public void setInitials(String firstName, String lastName) {
        StringBuilder initialsBuilder = new StringBuilder();
        if (firstName != null && !firstName.isEmpty()) {
            initialsBuilder.append(firstName.substring(0, 1));
        }
        if (lastName != null && !lastName.isEmpty()) {
            initialsBuilder.append(lastName.substring(0, 1));
        }

        avatarInitials.setText(initialsBuilder.length() > 0 ? initialsBuilder.toString() : "");
        requestLayout();
    }
}
