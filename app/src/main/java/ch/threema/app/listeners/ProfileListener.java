package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.domain.taskmanager.TriggerSource;

public interface ProfileListener {
    @AnyThread
    void onAvatarChanged(@NonNull TriggerSource triggerSource);

    @AnyThread
    default void onNicknameChanged(String newNickname) {}
}
