package ch.threema.app.activities

import android.content.Context
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.IdentityString

private val logger = getThreemaLogger("ExcludedSyncIdentitiesActivity")

class ExcludedSyncIdentitiesActivity : IdentityListActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val identityList: IdentityList? by lazy {
        val excludedSyncIdentitiesService = ThreemaApplication.getServiceManager()?.excludedSyncIdentitiesService
            ?: return@lazy null

        object : IdentityList {
            override fun getAll(): Set<IdentityString> {
                return excludedSyncIdentitiesService.getExcludedIdentities()
            }

            override fun addIdentity(identity: IdentityString) {
                excludedSyncIdentitiesService.excludeFromSync(identity, TriggerSource.LOCAL)
            }

            override fun removeIdentity(identity: IdentityString) {
                excludedSyncIdentitiesService.removeExcludedIdentity(identity, TriggerSource.LOCAL)
            }
        }
    }

    override fun getIdentityListHandle(): IdentityList? {
        return identityList
    }

    override fun getBlankListText(): String {
        return this.getString(R.string.prefs_sum_excluded_sync_identities)
    }

    override fun getTitleText(): String {
        return this.getString(R.string.prefs_title_excluded_sync_identities)
    }

    companion object {
        fun createIntent(context: Context) = buildActivityIntent<ExcludedSyncIdentitiesActivity>(context)
    }
}
