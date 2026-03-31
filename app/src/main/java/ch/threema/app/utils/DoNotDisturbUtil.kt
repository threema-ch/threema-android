package ch.threema.app.utils

import ch.threema.app.stores.IdentityProvider
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride

abstract class DoNotDisturbUtil {

    protected abstract val identityProvider: IdentityProvider

    /**
     * Returns true if the chat for the provided MessageReceiver is permanently or temporarily muted AT THIS TIME and
     * no intrusive notification should be shown for an incoming message
     * If a message text is provided it is checked for possible mentions - group messages only
     *
     * @param rawMessageText Text of the incoming message (optional, group messages only)
     */
    fun isMessageMuted(
        notificationTriggerPolicyOverride: NotificationTriggerPolicyOverride?,
        rawMessageText: CharSequence?,
    ): Boolean {
        if (notificationTriggerPolicyOverride == null) {
            return false
        }
        val myIdentity = identityProvider.getIdentity()?.value
        val isMutedByOverrideSetting = if (rawMessageText != null && myIdentity != null) {
            notificationTriggerPolicyOverride.muteAppliesRightNowToMessage(rawMessageText.toString(), myIdentity)
        } else {
            notificationTriggerPolicyOverride.muteAppliesRightNow
        }
        return isMutedByOverrideSetting || isDoNotDisturbActive()
    }

    /**
     * Check if Work DND schedule is currently active
     *
     * @return true if we're currently outside of the working hours set by the user and Work DND is currently enabled, false otherwise
     */
    abstract fun isDoNotDisturbActive(): Boolean
}
