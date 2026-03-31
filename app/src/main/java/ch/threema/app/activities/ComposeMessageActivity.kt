package ch.threema.app.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.launch
import ch.threema.android.buildActivityIntent
import ch.threema.android.buildBundle
import ch.threema.android.disableExitTransition
import ch.threema.android.runTransaction
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.applock.CheckAppLockContract
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.fragments.composemessage.ComposeMessageFragment
import ch.threema.app.fragments.conversations.ConversationsFragment
import ch.threema.app.preference.SettingsActivity
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.startup.AppStartupAware
import ch.threema.app.startup.waitUntilReady
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConversationUtil.getContactConversationUid
import ch.threema.app.utils.ConversationUtil.getDistributionListConversationUid
import ch.threema.app.utils.ConversationUtil.getGroupConversationUid
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.DistributionListReceiverIdentifier
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ComposeMessageActivity")

class ComposeMessageActivity : ThreemaToolbarActivity(), DialogClickListener, AppStartupAware {
    init {
        logScreenVisibility(logger)
    }

    private val conversationCategoryService: ConversationCategoryService by inject()
    private val preferenceService: PreferenceService by inject()

    private var composeMessageFragment: ComposeMessageFragment? = null
    private var conversationsFragment: ConversationsFragment? = null

    private var currentIntent: Intent? = null
    private var savedSoftInputMode = 0

    private val checkLockOnCreateLauncher = registerForActivityResult(CheckAppLockContract()) { unlocked ->
        if (unlocked) {
            composeMessageFragment?.let { fragment ->
                supportFragmentManager.runTransaction {
                    show(fragment)
                }
                // mark conversation as read as soon as it's unhidden
                fragment.markAsRead()
            }
        } else {
            finish()
        }
    }
    private val checkLockOnNewIntentLauncher = registerForActivityResult(CheckAppLockContract()) { unlocked ->
        if (unlocked) {
            composeMessageFragment?.let { fragment ->
                supportFragmentManager.runTransaction {
                    show(fragment)
                }
                fragment.onNewIntent(this.currentIntent)
            }
        } else if (!ConfigUtils.isTabletLayout()) {
            finish()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        logger.info("onCreate")
        window.setAllowEnterTransitionOverlap(true)
        window.setAllowReturnTransitionOverlap(true)
        currentIntent = intent

        super.onCreate(savedInstanceState)

        // TODO(ANDR-4389): Improve the waiting mechanism
        waitUntilReady {
            initActivity(savedInstanceState)
            handleDeviceInsets()
        }
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }
        logger.info("initActivity")

        findExistingFragments()

        if (findViewById<View>(R.id.messages) != null && conversationsFragment == null) {
            // add messages fragment in tablet layout
            conversationsFragment = ConversationsFragment()
            getConversationUidFromIntent(intent)?.let { conversationUID ->
                conversationsFragment!!.setArguments(
                    buildBundle {
                        putString(ConversationsFragment.OPENED_CONVERSATION_UID, conversationUID)
                    },
                )
            }
            supportFragmentManager.runTransaction {
                add(R.id.messages, conversationsFragment!!, MESSAGES_FRAGMENT_TAG)
            }
        }

        val isHidden = checkHiddenChatLock(intent, checkLockOnCreateLauncher)
        if (composeMessageFragment == null) {
            composeMessageFragment = ComposeMessageFragment()
            if (isHidden) {
                supportFragmentManager.runTransaction {
                    add(R.id.compose, composeMessageFragment!!, COMPOSE_FRAGMENT_TAG)
                    hide(composeMessageFragment!!)
                }
            } else {
                supportFragmentManager.runTransaction {
                    add(R.id.compose, composeMessageFragment!!, COMPOSE_FRAGMENT_TAG)
                }
            }
        } else if (!isHidden) {
            supportFragmentManager.runTransaction {
                show(composeMessageFragment!!)
            }
        }
        return true
    }

    private fun getConversationUidFromIntent(intent: Intent?): String? {
        val identity = IntentDataUtil.getIdentity(intent)
        if (identity != null) {
            return getContactConversationUid(identity)
        }
        val groupDbId = IntentDataUtil.getGroupId(intent)
        if (groupDbId != -1L) {
            return getGroupConversationUid(groupDbId)
        }
        val distributionListId = IntentDataUtil.getDistributionListId(intent)
        if (distributionListId != -1L) {
            return getDistributionListConversationUid(distributionListId)
        }
        return null
    }

    override fun getLayoutResource() = if (ConfigUtils.isTabletLayout(this)) {
        R.layout.activity_compose_message_tablet
    } else {
        R.layout.activity_compose_message
    }

    private fun findExistingFragments() {
        composeMessageFragment = supportFragmentManager.findFragmentByTag(COMPOSE_FRAGMENT_TAG) as ComposeMessageFragment?
        conversationsFragment = supportFragmentManager.findFragmentByTag(MESSAGES_FRAGMENT_TAG) as ConversationsFragment?
    }

    public override fun onNewIntent(intent: Intent) {
        logger.info("onNewIntent")

        super.onNewIntent(intent)

        this.currentIntent = intent

        findExistingFragments()

        composeMessageFragment?.let { composeMessageFragment ->
            if (!checkHiddenChatLock(intent, checkLockOnNewIntentLauncher)) {
                supportFragmentManager.runTransaction {
                    show(composeMessageFragment)
                }
                composeMessageFragment.onNewIntent(intent)
            }
        }
    }

    override fun enableOnBackPressedCallback() = true

    override fun handleOnBackPressed() {
        logger.info("handleOnBackPressed")
        if (ConfigUtils.isTabletLayout() && conversationsFragment?.onBackPressed() == true) {
            return
        }
        composeMessageFragment?.let { composeMessageFragment ->
            if (!composeMessageFragment.onBackPressed()) {
                finish()
                if (ConfigUtils.isTabletLayout()) {
                    disableExitTransition()
                }
            }
            return
        }
        finish()
    }

    public override fun onResume() {
        super.onResume()

        // Set the soft input mode to resize when activity resumes because it is set to adjust nothing while it is paused
        savedSoftInputMode = window.attributes.softInputMode
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    public override fun onPause() {
        super.onPause()

        // Set the soft input mode to adjust nothing while paused. This is needed when the keyboard is opened to edit the contact before sending.
        if (savedSoftInputMode > 0) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
    }

    private fun checkHiddenChatLock(intent: Intent?, launcher: ActivityResultLauncher<Unit>): Boolean {
        val messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(applicationContext, intent)

        if (messageReceiver == null) {
            logger.info("Intent does not have any extras. Check \"Don't keep activities\" option in developer settings.")
            return false
        }

        if (conversationCategoryService.isPrivateChat(messageReceiver.uniqueIdString)) {
            if (ConfigUtils.hasProtection(preferenceService)) {
                launcher.launch()
            } else {
                GenericAlertDialog.newInstance(R.string.hide_chat, R.string.hide_chat_enter_message_explain, R.string.set_lock, R.string.cancel)
                    .show(supportFragmentManager, DIALOG_TAG_HIDDEN_NOTICE)
            }
            return true
        }
        return false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        ConfigUtils.adjustToolbar(this, toolbar)

        val messagesLayout = findViewById<FrameLayout?>(R.id.messages)

        if (messagesLayout != null) {
            // adjust width of messages fragment in tablet layout
            val layoutParams = messagesLayout.layoutParams as FrameLayout.LayoutParams
            layoutParams.width = resources.getDimensionPixelSize(R.dimen.message_fragment_width)
            messagesLayout.setLayoutParams(layoutParams)
        }
    }

    override fun onYes(tag: String?, data: Any?) {
        startActivity(SettingsActivity.createIntent(this, SettingsActivity.InitialScreen.SECURITY))
        finish()
    }

    override fun onNo(tag: String?, data: Any?) {
        finish()
    }

    companion object {
        private const val COMPOSE_FRAGMENT_TAG = "compose_message_fragment"
        private const val MESSAGES_FRAGMENT_TAG = "message_section_fragment"

        private const val DIALOG_TAG_HIDDEN_NOTICE = "hidden"

        fun createIntent(context: Context, receiverIdentifier: ReceiverIdentifier) = buildActivityIntent<ComposeMessageActivity>(context) {
            when (receiverIdentifier) {
                is GroupReceiverIdentifier -> {
                    putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, receiverIdentifier.groupDatabaseId)
                }
                is DistributionListReceiverIdentifier -> {
                    putExtra(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID, receiverIdentifier.id)
                }
                is ContactReceiverIdentifier -> {
                    putExtra(AppConstants.INTENT_DATA_CONTACT, receiverIdentifier.identity)
                }
            }
        }
    }
}
