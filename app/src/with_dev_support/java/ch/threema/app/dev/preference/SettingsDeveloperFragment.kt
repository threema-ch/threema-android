package ch.threema.app.dev.preference

import android.content.SharedPreferences
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.asynctasks.AddContactRestrictionPolicy
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask
import ch.threema.app.asynctasks.ContactAvailable
import ch.threema.app.asynctasks.PolicyViolation
import ch.threema.app.dev.androidcontactsync.AndroidContactDebugActivity
import ch.threema.app.dev.patternlibrary.PatternLibraryActivity
import ch.threema.app.exceptions.InvalidEntryException
import ch.threema.app.exceptions.PolicyViolationException
import ch.threema.app.preference.ThreemaPreferenceFragment
import ch.threema.app.preference.onClick
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.problemsolving.Problem
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ContactService
import ch.threema.app.services.MessageService
import ch.threema.app.services.UserService
import ch.threema.app.usecases.OverrideOneTimeHintsUseCase
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.MessageId.Companion.random
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.data.status.VoipStatusDataModel
import java.util.Date
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsDeveloperFragment")

@Suppress("unused")
class SettingsDeveloperFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val contactService: ContactService by inject()
    private val messageService: MessageService by inject()
    private val userService: UserService by inject()
    private val apiConnector: APIConnector by inject()
    private val appRestrictions: AppRestrictions by inject()
    private val contactModelRepository: ContactModelRepository by inject()
    private val sharedPreferences: SharedPreferences by inject()
    private val contentCreator: ContentCreator by inject()
    private val overrideOneTimeHintsUseCase: OverrideOneTimeHintsUseCase by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    override fun initializePreferences() {
        getPref<Preference>(R.string.preferences__dev_reset_one_time_hints).onClick {
            resetOneTimeHintsShown()
        }

        // Reset dismissed problems
        getPref<Preference>(R.string.preferences__dev_reset_dismissed_problems).onClick {
            resetDismissedProblems()
        }

        // Generate text messages
        getPref<Preference>(R.string.preferences__dev_create_text_messages).onClick {
            generateTextMessages()
        }

        // Generate messages with reactions
        getPref<Preference>(R.string.preferences__dev_create_messages_with_reactions).onClick {
            generateReactionMessages()
        }

        // Generate nonces
        getPref<Preference>(R.string.preferences__dev_create_nonces).onClick {
            generateNonces()
        }

        // Generate VoIP messages
        val generateVoipPreference = getPref<Preference>(R.string.preferences__generate_voip_messages)
        generateVoipPreference.setSummary("Create the test identity $TEST_IDENTITY_1 and add all possible VoIP messages to that conversation.")
        generateVoipPreference.onClick {
            generateVoipMessages()
        }

        // Generate test quotes
        val generateRecursiveQuote = getPref<Preference>(R.string.preferences__generate_test_quotes)
        generateRecursiveQuote.setSummary("Create the test identities $TEST_IDENTITY_1 and $TEST_IDENTITY_2 and add some test quotes.")
        generateRecursiveQuote.onClick {
            generateTestQuotes()
        }

        // Theming
        getPref<Preference>(R.string.preferences__open_pattern_library).onClick {
            startActivity(PatternLibraryActivity.createIntent(requireContext()))
        }

        // Android Contact Sync Debugging
        getPref<Preference>(R.string.preferences__open_android_contact_sync_debug_activity).onClick {
            startActivity(AndroidContactDebugActivity.createIntent(requireContext()))
        }

        // Crash the app on purpose
        getPref<Preference>(R.string.preferences__cause_crash).onClick {
            try {
                try {
                    throw IllegalArgumentException("This is the inner cause!")
                } catch (e: Exception) {
                    throw IllegalStateException("This is the outer cause", e)
                }
            } catch (e: Exception) {
                throw RuntimeException("Crash test!", e)
            }
        }

        // Remove developer menu
        getPref<Preference>(R.string.preferences__remove_menu).onClick {
            hideDeveloperMenu()
        }
    }

    private fun resetOneTimeHintsShown() {
        logger.info("Reset one-time hints")
        overrideOneTimeHintsUseCase.call(dismiss = false)
        showToast("One-time hints reset")
    }

    private fun resetDismissedProblems() {
        Problem.entries.forEach { problem ->
            if (problem.dismissKey != null) {
                preferenceService.setProblemDismissed(problem.dismissKey, null)
            }
        }
        showToast("Problems reset")
    }

    @UiThread
    private fun generateTextMessages() {
        contentCreator.createTextMessageSpam(getParentFragmentManager())
    }

    @UiThread
    private fun generateReactionMessages() {
        contentCreator.createReactionSpam(getParentFragmentManager())
    }

    @UiThread
    private fun generateNonces() {
        contentCreator.createNonces(getParentFragmentManager())
    }

    @UiThread
    private fun generateVoipMessages() {
        /** Pojo for holding test data. */
        class VoipMessage(val dataModel: VoipStatusDataModel, val description: String)

        // Test messages
        val testMessages = arrayOf(
            VoipMessage(
                dataModel = VoipStatusDataModel.createMissed(VoipStatusDataModel.NO_CALL_ID, null),
                description = "missed",
            ),
            VoipMessage(
                dataModel = VoipStatusDataModel.createFinished(VoipStatusDataModel.NO_CALL_ID, 42),
                description = "finished",
            ),
            VoipMessage(
                dataModel = VoipStatusDataModel.createRejected(VoipStatusDataModel.NO_CALL_ID, VoipCallAnswerData.RejectReason.UNKNOWN),
                description = "rejected (unknown)",
            ),
            VoipMessage(
                dataModel = VoipStatusDataModel.createRejected(VoipStatusDataModel.NO_CALL_ID, VoipCallAnswerData.RejectReason.BUSY),
                description = "rejected (busy)",
            ),
            VoipMessage(
                dataModel = VoipStatusDataModel.createRejected(VoipStatusDataModel.NO_CALL_ID, VoipCallAnswerData.RejectReason.TIMEOUT),
                description = "rejected (timeout)",
            ),
            VoipMessage(
                dataModel = VoipStatusDataModel.createRejected(VoipStatusDataModel.NO_CALL_ID, VoipCallAnswerData.RejectReason.REJECTED),
                description = "rejected (rejected)",
            ),
            VoipMessage(
                dataModel = VoipStatusDataModel.createRejected(VoipStatusDataModel.NO_CALL_ID, VoipCallAnswerData.RejectReason.DISABLED),
                description = "rejected (disabled)",
            ),
            VoipMessage(
                dataModel = VoipStatusDataModel.createRejected(VoipStatusDataModel.NO_CALL_ID, 99.toByte()),
                description = "rejected (invalid reason code)",
            ),
            VoipMessage(
                dataModel = VoipStatusDataModel.createRejected(VoipStatusDataModel.NO_CALL_ID, null),
                description = "rejected (null reason code)",
            ),
            VoipMessage(
                dataModel = VoipStatusDataModel.createAborted(VoipStatusDataModel.NO_CALL_ID),
                description = "aborted",
            ),
        )

        lifecycleScope.launch(dispatcherProvider.worker) {
            try {
                // Create test identity
                val contact = createTestContact(TEST_IDENTITY_1, "Developer", "Testcontact")

                // Create test messages
                val receiver = contactService.createReceiver(contact)
                messageService.createStatusMessage("Creating test messages...", receiver)
                booleanArrayOf(true, false).forEach { isOutbox ->
                    testMessages.forEach { message ->
                        val text = (if (isOutbox) "Outgoing " else "Incoming ") + message.description
                        messageService.createStatusMessage(text, receiver)
                        messageService.createVoipStatus(message.dataModel, receiver, isOutbox, true)
                    }
                }

                showToast("Test messages created!")
            } catch (e: Exception) {
                logger.error("Failed to generate voip messages", e)
                showToast(R.string.an_error_occurred)
            }
        }
    }

    @UiThread
    private fun generateTestQuotes() {
        lifecycleScope.launch(dispatcherProvider.worker) {
            try {
                // Create test identity
                val contact1 = createTestContact(TEST_IDENTITY_1, "Developer", "Testcontact")
                val receiver1 = contactService.createReceiver(contact1)
                val contact2 = createTestContact(TEST_IDENTITY_2, "Another Developer", "Testcontact")
                val receiver2 = contactService.createReceiver(contact2)

                messageService.createStatusMessage("Creating test quotes...", receiver1)

                // Create recursive quote
                val messageIdRecursive = random()
                val messageRecursive = TextMessage()
                messageRecursive.fromIdentity = contact1.identity
                messageRecursive.toIdentity = userService.getIdentity()
                messageRecursive.date = Date()
                messageRecursive.messageId = messageIdRecursive
                messageRecursive.text = "> quote #$messageIdRecursive\n\na quote that references itself"
                messageService.processIncomingContactMessage(messageRecursive, TriggerSource.LOCAL)

                // Create cross-chat quote
                val messageIdCrossChat1 = random()
                val messageIdCrossChat2 = random()
                val messageChat2 = TextMessage()
                messageChat2.fromIdentity = contact2.identity
                messageChat2.toIdentity = userService.getIdentity()
                messageChat2.date = Date()
                messageChat2.messageId = messageIdCrossChat2
                messageChat2.text = "hello, this is a secret message"
                messageService.processIncomingContactMessage(messageChat2, TriggerSource.LOCAL)
                val messageChat1 = TextMessage()
                messageChat1.fromIdentity = contact1.identity
                messageChat1.toIdentity = userService.getIdentity()
                messageChat1.date = Date()
                messageChat1.messageId = messageIdCrossChat1
                messageChat1.text = "> quote #$messageIdCrossChat2\n\nOMG!"
                messageService.processIncomingContactMessage(messageChat1, TriggerSource.LOCAL)

                messageService.createStatusMessage("Done creating test quotes", receiver1)

                showToast("Test quotes created!")
            } catch (e: Exception) {
                logger.error("Failed to create quotes", e)
                showToast(R.string.an_error_occurred)
            }
        }
    }

    @WorkerThread
    @Throws(InvalidEntryException::class, PolicyViolationException::class)
    private fun createTestContact(
        identity: String,
        firstName: String,
        lastName: String,
    ): ContactModel {
        val result = BasicAddOrUpdateContactBackgroundTask(
            identity = identity,
            acquaintanceLevel = ContactModel.AcquaintanceLevel.DIRECT,
            myIdentity = userService.getIdentity()!!,
            apiConnector = apiConnector,
            contactModelRepository = contactModelRepository,
            addContactRestrictionPolicy = AddContactRestrictionPolicy.CHECK,
            appRestrictions = appRestrictions,
            expectedPublicKey = null,
        ).runSynchronously()

        when (result) {
            is ContactAvailable -> {
                result.contactModel.setNameFromLocal(firstName, lastName)
                val contactModel = contactService.getByIdentity(identity)!!
                return contactModel
            }
            is PolicyViolation -> throw PolicyViolationException()
            else -> throw InvalidEntryException(R.string.invalid_threema_id)
        }
    }

    @UiThread
    private fun hideDeveloperMenu() {
        preferenceService.setShowDeveloperMenu(false)
        showToast("Developer settings hidden")
        activity?.finish()
    }

    override fun getPreferenceTitleResource() = R.string.prefs_developers

    override fun getPreferenceResource() = R.xml.preference_developers

    companion object {
        private const val TEST_IDENTITY_1 = "ADDRTCNX"
        private const val TEST_IDENTITY_2 = "H6AXSHKC"
    }
}
