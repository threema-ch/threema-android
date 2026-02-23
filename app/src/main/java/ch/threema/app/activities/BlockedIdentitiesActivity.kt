package ch.threema.app.activities

import android.content.Context
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.types.Identity

private val logger = getThreemaLogger("BlockedIdentitiesActivity")

class BlockedIdentitiesActivity : IdentityListActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val identityList: IdentityList? by lazy {
        val blockedIdentitiesService =
            ThreemaApplication.getServiceManager()?.blockedIdentitiesService ?: return@lazy null

        object : IdentityList {
            override fun getAll(): Set<String> {
                return blockedIdentitiesService.getAllBlockedIdentities()
            }

            override fun addIdentity(identity: Identity) {
                blockedIdentitiesService.blockIdentity(identity)
            }

            override fun removeIdentity(identity: Identity) {
                blockedIdentitiesService.unblockIdentity(identity)
            }
        }
    }

    override fun getIdentityListHandle(): IdentityList? {
        return identityList
    }

    override fun getBlankListText(): String {
        return this.getString(R.string.prefs_sum_blocked_contacts)
    }

    override fun getTitleText(): String {
        return this.getString(R.string.prefs_title_blocked_contacts)
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<BlockedIdentitiesActivity>(context)
    }
}
