package ch.threema.app.utils;

import android.graphics.Paint;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.GroupService;
import ch.threema.domain.models.IdentityState;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.group.GroupModelOld;

import static ch.threema.app.compose.conversation.models.ConversationNameStyleKt.INACTIVE_CONTACT_ALPHA;

public class AdapterUtil {

    /**
     * Style a TextView by means of the state
     */
    public static void styleContact(@Nullable TextView view, @Nullable ContactModel contactModel) {
        if (view == null) {
            return;
        }
        styleContact(view, contactModel != null ? contactModel.getState() : null);
    }

    public static void styleContact(@NonNull TextView view, @Nullable IdentityState identityState) {
        int paintFlags = view.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG);
        float alpha = 1f;

        if (identityState != null) {
            switch (identityState) {
                case INACTIVE:
                    alpha = INACTIVE_CONTACT_ALPHA;
                    break;

                case INVALID:
                    paintFlags = paintFlags | Paint.STRIKE_THRU_TEXT_FLAG;
                    break;
            }
        }
        view.setAlpha(alpha);
        view.setPaintFlags(paintFlags);
    }

    public static void styleGroup(
        @Nullable TextView textView,
        @NonNull GroupService groupService,
        @Nullable GroupModelOld groupModel
    ) {
        if (textView != null) {
            if (groupModel != null && !groupService.isGroupMember(groupModel)) {
                styleStrikethrough(textView);
            } else {
                styleNormal(textView);
            }
        }
    }

    private static void styleNormal(@NonNull TextView textView) {
        int paintFlags = textView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG);
        textView.setAlpha(1f);
        textView.setPaintFlags(paintFlags);
    }

    private static void styleStrikethrough(@NonNull TextView textView) {
        int paintFlags = textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG;
        textView.setAlpha(1f);
        textView.setPaintFlags(paintFlags);
    }
}
