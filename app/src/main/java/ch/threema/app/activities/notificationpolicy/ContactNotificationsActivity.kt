package ch.threema.app.activities.notificationpolicy

import android.os.Bundle
import android.view.View
import ch.threema.app.AppConstants
import ch.threema.app.services.RingtoneService
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.IdentityString
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ContactNotificationsActivity")

class ContactNotificationsActivity : NotificationsActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val ringtoneService: RingtoneService by inject()
    private val contactModelRepository: ContactModelRepository by inject()

    private val contactIdentity: IdentityString? by lazy {
        intent.getStringExtra(AppConstants.INTENT_DATA_CONTACT)
    }

    private val contactModel: ContactModel? by lazy {
        contactIdentity?.let(contactModelRepository::getByIdentity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (contactIdentity.isNullOrEmpty() || contactModel == null) {
            finish()
            return
        }

        uid = ContactUtil.getUniqueIdString(contactIdentity)

        refreshSettings()
    }

    public override fun refreshSettings() {
        defaultRingtone = ringtoneService.defaultContactRingtone
        selectedRingtone = ringtoneService.getContactRingtone(uid)

        super.refreshSettings()
    }

    override fun setupButtons() {
        super.setupButtons()

        radioSilentExceptMentions.visibility = View.GONE
    }

    override fun onSettingChanged(mutedOverrideUntil: Long?) {
        contactModel?.setNotificationTriggerPolicyOverrideFromLocal(mutedOverrideUntil)
    }

    override fun isMutedRightNow(): Boolean {
        val currentContactModelData = contactModel?.data ?: return false
        return currentContactModelData.currentNotificationTriggerPolicyOverride.muteAppliesRightNow
    }

    // This setting is only available for group chats
    override fun isMutedExceptMentions(): Boolean = false

    override fun getNotificationTriggerPolicyOverrideValue(): Long? {
        val currentContactModelData = contactModel?.data ?: return null
        return currentContactModelData.notificationTriggerPolicyOverride
    }
}
