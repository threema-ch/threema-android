package ch.threema.app.routines;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.UserService;
import ch.threema.domain.taskmanager.TriggerSource;

/**
 * Check the state of the pending email linkBallot
 */
public class CheckIdentityRoutine implements Runnable {
    @NonNull
    private final UserService userService;
    @Nullable
    private final OnStatusChanged onStatusChanged;
    @NonNull
    private final TriggerSource triggerSource;

    public interface OnStatusChanged {
        void onFinished(boolean success);
    }

    public CheckIdentityRoutine(
        @NonNull UserService userService,
        @Nullable OnStatusChanged onStatusChanged,
        @NonNull TriggerSource triggerSource
    ) {
        this.userService = userService;
        this.onStatusChanged = onStatusChanged;
        this.triggerSource = triggerSource;
    }

    @Override
    public void run() {
        //check email linking state
        if (this.userService.getEmailLinkingState() == UserService.LinkingState_PENDING) {
            //only if linking state is pending
            this.userService.checkEmailLinkState(triggerSource);
        }

        //check revocation key
        this.userService.checkRevocationKey(false);

        if (this.onStatusChanged != null) {
            this.onStatusChanged.onFinished(true);
        }
    }
}
