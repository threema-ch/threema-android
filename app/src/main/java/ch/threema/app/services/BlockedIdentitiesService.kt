package ch.threema.app.services

import android.content.Context
import ch.threema.app.tasks.ReflectSettingsSyncTask
import ch.threema.base.SessionScoped
import ch.threema.domain.types.IdentityString

/**
 * Manage blocked identities.
 */
@SessionScoped
interface BlockedIdentitiesService {
    /**
     * Block an identity. The identity will be persisted regardless whether a contact with this
     * identity exists or not. If a [context] is provided, a toast is shown.
     *
     * Note that this method reflects a [ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate] with
     * the currently blocked identities.
     *
     * @param identity the identity to block
     * @param context if provided, a toast is shown
     */
    fun blockIdentity(identity: IdentityString, context: Context? = null)

    /**
     * Unblock an identity. If a [context] is provided, a toast is shown.
     *
     * Note that this method reflects a [ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate] with
     * the currently blocked identities.
     *
     * @param identity the identity to unblock
     * @param context if provided, a toast is shown
     */
    fun unblockIdentity(identity: IdentityString, context: Context? = null)

    /**
     * Checks whether the [identity] is blocked or not.
     */
    fun isBlocked(identity: IdentityString): Boolean

    /**
     * Blocks the identity if currently not blocked or vice versa. If a [context] is provided, this
     * additionally shows a toast.
     *
     * @param identity the identity that will change from blocked to unblocked or vice versa
     * @param context if provided, a toast is shown
     */
    fun toggleBlocked(identity: IdentityString, context: Context? = null)

    /**
     * Get all blocked identities.
     */
    fun getAllBlockedIdentities(): Set<IdentityString>

    /**
     * Persist the blocked identities. This replaces all currently blocked identities with
     * [blockedIdentities]. Note that there is no reflection done.
     */
    fun persistBlockedIdentities(blockedIdentities: Set<IdentityString>)
}
