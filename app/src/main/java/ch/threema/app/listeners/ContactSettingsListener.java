package ch.threema.app.listeners;

import androidx.annotation.AnyThread;

public interface ContactSettingsListener {
    @AnyThread
    default void onSortingChanged() {}

    @AnyThread
    default void onNameFormatChanged() {}

    @AnyThread
    default void onAvatarSettingChanged() {}

    @AnyThread
    default void onInactiveContactsSettingChanged() {}

    @AnyThread
    default void onNotificationSettingChanged(String uid) {}
}
