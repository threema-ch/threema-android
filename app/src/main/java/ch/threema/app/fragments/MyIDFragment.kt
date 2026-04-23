package ch.threema.app.fragments

import android.animation.LayoutTransition
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.activities.ExportIDActivity
import ch.threema.app.activities.ProfilePicRecipientsActivity
import ch.threema.app.applock.CheckAppLockContract
import ch.threema.app.availabilitystatus.displayText
import ch.threema.app.availabilitystatus.edit.EditAvailabilityStatusBottomSheetDialog
import ch.threema.app.availabilitystatus.iconRes
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.dialogs.PasswordEntryDialog
import ch.threema.app.dialogs.PasswordEntryDialog.PasswordEntryDialogClickListener
import ch.threema.app.dialogs.PublicKeyDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.dialogs.TextEntryDialog
import ch.threema.app.dialogs.TextEntryDialog.TextEntryDialogClickListener
import ch.threema.app.emojis.EmojiTextView
import ch.threema.app.listeners.ProfileListener
import ch.threema.app.listeners.SMSVerificationListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.reset.ResetAppTask
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ContactService.ProfilePictureSharePolicy
import ch.threema.app.services.LocaleService
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.ui.AvatarEditView
import ch.threema.app.ui.InsetSides.Companion.horizontal
import ch.threema.app.ui.QRCodePopup
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.LocaleUtil
import ch.threema.app.utils.ShareUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.takeUnlessEmpty
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.domain.protocol.api.LinkMobileNoException
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.TriggerSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlin.system.exitProcess
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("MyIDFragment")

/**
 * This is one of the tabs in the home screen. It shows the user's profile.
 */
class MyIDFragment : MainFragment(), DialogClickListener, TextEntryDialogClickListener, PasswordEntryDialogClickListener {

    private val userService: UserService by inject()
    private val preferenceService: PreferenceService by inject()
    private val localeService: LocaleService by inject()
    private val profilePictureRecipientsService: ProfilePictureRecipientsService by inject()
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val taskCreator: TaskCreator by inject()
    private val appRestrictions: AppRestrictions by inject()
    private val resetAppTask: ResetAppTask by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private var fragmentView: View? = null
    private var avatarView: AvatarEditView? = null
    private var picReleaseConfigView: View? = null
    private var picReleaseSpinner: MaterialAutoCompleteTextView? = null
    private var nicknameTextView: EmojiTextView? = null
    private var currentAvailabilityStatusContainer: LinearLayout? = null
    private var currentAvailabilityStatusIcon: ImageView? = null
    private var currentAvailabilityStatusName: EmojiTextView? = null

    private var hidden = false
    private var isDisabledProfilePicReleaseSettings = false

    private val smsVerificationListener: SMSVerificationListener = object : SMSVerificationListener {
        override fun onVerified() {
            lifecycleScope.launch {
                updatePendingState(requireView(), false)
            }
        }

        override fun onVerificationStarted() {
            lifecycleScope.launch {
                updatePendingState(requireView(), false)
            }
        }
    }

    private val profileListener: ProfileListener = object : ProfileListener {
        override fun onAvatarChanged(triggerSource: TriggerSource) {
            if (
                triggerSource != TriggerSource.LOCAL ||
                isDisabledProfilePicReleaseSettings ||
                preferenceService.getProfilePicRelease() != PreferenceService.PROFILEPIC_RELEASE_NOBODY
            ) {
                return
            }

            // a profile picture has been set so it's safe to assume user wants others to see his pic
            preferenceService.setProfilePicRelease(PreferenceService.PROFILEPIC_RELEASE_EVERYONE)
            // Sync new policy setting to device group (if md is active)
            if (multiDeviceManager.isMultiDeviceActive) {
                taskCreator.scheduleReflectUserProfileShareWithPolicySyncTask(ProfilePictureSharePolicy.Policy.EVERYONE)
            }
            lifecycleScope.launch {
                updatePicReleaseSpinnerText()
            }
        }

        override fun onNicknameChanged(newNickname: String?) {
            lifecycleScope.launch {
                reloadNickname()
            }
        }
    }

    private val checkLockToRevokeIdLauncher = registerForActivityResult(CheckAppLockContract()) { unlocked ->
        if (unlocked) {
            showRevocationPasswordDialog()
        }
    }
    private val checkLockToDeleteIdLauncher = registerForActivityResult(CheckAppLockContract()) { unlocked ->
        if (unlocked) {
            confirmIdDelete()
        }
    }
    private val checkLockToExportIdLauncher = registerForActivityResult(CheckAppLockContract()) { unlocked ->
        if (unlocked) {
            startActivity(ExportIDActivity.createIntent(requireContext()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true

        if (ConfigUtils.supportsAvailabilityStatus()) {
            setFragmentResultListener(
                requestKey = REQUEST_KEY_EDIT_AVAILABILITY_STATUS,
            ) { _, bundle ->
                val didChangeStatus = bundle.getBoolean(EditAvailabilityStatusBottomSheetDialog.RESULT_KEY_DID_CHANGE_STATUS)
                if (didChangeStatus && this.view != null && isAdded) {
                    Snackbar.make(requireView(), R.string.edit_availability_status_did_change, LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentView = view
        if (fragmentView != null) {
            ListenerManager.profileListeners.add(profileListener)
            return fragmentView
        }

        val fragmentView = inflater.inflate(R.layout.fragment_my_id, container, false)!!
        this.fragmentView = fragmentView

        updatePendingState(fragmentView, true)

        val layoutTransition = LayoutTransition()
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val viewGroup = fragmentView.findViewById<ViewGroup>(R.id.fragment_id_container)
        viewGroup.setLayoutTransition(layoutTransition)
        viewGroup.applyDeviceInsetsAsPadding(horizontal())

        // Look up app restrictions
        val isReadonlyProfile = appRestrictions.isReadOnlyProfile()
        isDisabledProfilePicReleaseSettings = appRestrictions.isDisabledProfilePicReleaseSettings()
        val isBackupsDisabled = appRestrictions.isBackupsDisabled() || appRestrictions.isIdBackupsDisabled()

        // Look up views
        val policyExplainView = fragmentView.findViewById<View>(R.id.policy_explain)
        val changeEmailButton = fragmentView.findViewById<MaterialButton>(R.id.change_email)
        val changeMobileButton = fragmentView.findViewById<MaterialButton>(R.id.change_mobile)
        val deleteIdButton = fragmentView.findViewById<MaterialButton>(R.id.delete_id)
        val exportIdButton = fragmentView.findViewById<MaterialButton>(R.id.export_id)
        val revocationPasswordLayout = fragmentView.findViewById<View>(R.id.revocation_key_layout)
        val revocationPasswordButton = fragmentView.findViewById<MaterialButton>(R.id.revocation_key)
        val myIdView = fragmentView.findViewById<TextView>(R.id.my_id)
        val shareIdButton = fragmentView.findViewById<View>(R.id.my_id_share)
        val myIdQrButton = fragmentView.findViewById<View>(R.id.my_id_qr)
        val publicKeyButton = fragmentView.findViewById<View>(R.id.public_key_button)
        val avatarView = fragmentView.findViewById<AvatarEditView>(R.id.avatar_edit_view)
        val nicknameTextView = fragmentView.findViewById<EmojiTextView>(R.id.nickname)
        val editProfileButton = fragmentView.findViewById<View>(R.id.profile_edit)
        val picReleaseSpinner = fragmentView.findViewById<MaterialAutoCompleteTextView>(R.id.picrelease_spinner)
        val picReleaseConfigView = fragmentView.findViewById<View>(R.id.picrelease_config)
        val picReleaseTextView = fragmentView.findViewById<View>(R.id.picrelease_text)
        val currentAvailabilityStatusContainer = fragmentView.findViewById<LinearLayout>(R.id.current_availability_status_container)
        val currentAvailabilityStatusIcon = fragmentView.findViewById<ImageView>(R.id.current_availability_status_icon)
        val currentAvailabilityStatusName = fragmentView.findViewById<EmojiTextView>(R.id.current_availability_status_name)
        val changeAvailabilityStatusButton = fragmentView.findViewById<MaterialButton>(R.id.change_availability_status)
        this.avatarView = avatarView
        this.nicknameTextView = nicknameTextView
        this.picReleaseConfigView = picReleaseConfigView
        this.picReleaseSpinner = picReleaseSpinner
        this.currentAvailabilityStatusContainer = currentAvailabilityStatusContainer
        this.currentAvailabilityStatusIcon = currentAvailabilityStatusIcon
        this.currentAvailabilityStatusName = currentAvailabilityStatusName

        picReleaseConfigView.setOnClickListener {
            launchProfilePictureRecipientsSelector()
        }
        picReleaseConfigView.isVisible = preferenceService.getProfilePicRelease() == PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST

        policyExplainView.isVisible = isReadonlyProfile || isBackupsDisabled

        currentAvailabilityStatusContainer.isVisible = ConfigUtils.supportsAvailabilityStatus()
        if (ConfigUtils.supportsAvailabilityStatus()) {
            setUpAvailabilityStatusDisplay(
                availabilityStatus = preferenceService.getAvailabilityStatus(),
            )
            changeAvailabilityStatusButton.setOnClickListener {
                EditAvailabilityStatusBottomSheetDialog
                    .newInstance(
                        requestKey = REQUEST_KEY_EDIT_AVAILABILITY_STATUS,
                    )
                    .show(
                        /* manager = */
                        parentFragmentManager,
                        /* tag = */
                        "edit-availability-status-from-user-profile",
                    )
            }
        }

        if (isDisabledProfilePicReleaseSettings) {
            picReleaseSpinner.isVisible = false
            picReleaseConfigView.isVisible = false
            picReleaseTextView.isVisible = false
        }

        if (isReadonlyProfile) {
            changeEmailButton.visibility = View.INVISIBLE
            changeMobileButton.visibility = View.INVISIBLE
            deleteIdButton.visibility = View.INVISIBLE
            revocationPasswordButton.visibility = View.INVISIBLE
            editProfileButton.isVisible = false
            avatarView.setEditable(false)
        } else {
            changeEmailButton.setOnClickListener {
                logger.info("Change email clicked")
                showChangeEmailDialog()
            }
            changeMobileButton.setOnClickListener {
                logger.info("Change mobile clicked")
                startMobileLinking()
            }
            deleteIdButton.setOnClickListener {
                logger.info("Delete ID clicked")
                startIdDeletion()
            }
            revocationPasswordButton.setOnClickListener {
                logger.info("Set revocation key clicked")
                startIdRevocationSetup()
            }
            editProfileButton.isVisible = true
            editProfileButton.setOnClickListener {
                logger.info("Edit nickname clicked, showing dialog")
                showEditNicknameDialog()
            }
        }

        if (isBackupsDisabled) {
            exportIdButton.visibility = View.INVISIBLE
        } else {
            exportIdButton.setOnClickListener {
                logger.info("Export ID clicked")
                startIdExport()
            }
        }

        if (ConfigUtils.isOnPremBuild()) {
            revocationPasswordLayout.isVisible = false
        }

        if (userService.hasIdentity()) {
            myIdView.text = userService.getIdentity()
            if (ConfigUtils.isOnPremBuild()) {
                shareIdButton.isVisible = false
            } else {
                shareIdButton.setOnClickListener {
                    logger.info("Share ID button clicked")
                    ShareUtil.shareContact(requireContext(), null)
                }
            }
            myIdQrButton.setOnClickListener {
                logger.info("My ID clicked, showing QR code")
                QRCodePopup(context, requireActivity().window.decorView, activity).show(myIdQrButton, null)
            }

            publicKeyButton.setOnClickListener {
                logger.info("Show my public key clicked")
                showPublicKeyDialog()
            }
        }

        avatarView.setFragment(this)
        avatarView.setIsMyProfilePicture(true)
        val identity = userService.getIdentity()
        if (identity == null) {
            logger.error("Identity is null. Not updating avatar view")
        } else {
            avatarView.setContactIdentity(identity)
        }

        setupPicReleaseSpinner()

        reloadNickname()

        ListenerManager.profileListeners.add(this.profileListener)

        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (ConfigUtils.supportsAvailabilityStatus()) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    preferenceService.watchAvailabilityStatus().collect(::setUpAvailabilityStatusDisplay)
                }
            }
        }
    }

    private fun setUpAvailabilityStatusDisplay(availabilityStatus: AvailabilityStatus?) {
        val availabilityStatusEffective = availabilityStatus ?: AvailabilityStatus.None
        currentAvailabilityStatusIcon?.setImageResource(availabilityStatusEffective.iconRes())
        currentAvailabilityStatusName?.text = availabilityStatusEffective.displayText().get(requireContext())
    }

    private fun setupPicReleaseSpinner() {
        val spinner = picReleaseSpinner ?: return

        val adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.picrelease_choices,
            android.R.layout.simple_spinner_dropdown_item,
        )
        spinner.setAdapter<ArrayAdapter<CharSequence?>?>(adapter)
        spinner.setOnItemClickListener { _, _, position: Int, _ ->
            onPicReleaseSpinnerItemClicked(position)
        }
        updatePicReleaseSpinnerText()
    }

    private fun updatePicReleaseSpinnerText() {
        val spinner = picReleaseSpinner
            ?: return
        spinner.setText(spinner.adapter.getItem(preferenceService.getProfilePicRelease()) as CharSequence?, false)
    }

    private fun onPicReleaseSpinnerItemClicked(position: Int) {
        val sharePolicy = ProfilePictureSharePolicy.Policy.fromIntOrNull(position)
        if (sharePolicy == null) {
            logger.error("Failed to get concrete enum value of type ProfilePictureSharePolicy.Policy for ordinal value {}", position)
            return
        }

        val oldPosition = preferenceService.getProfilePicRelease()
        preferenceService.setProfilePicRelease(position)

        picReleaseConfigView?.isVisible = position == PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST

        if (position == oldPosition) {
            return
        }

        if (sharePolicy == ProfilePictureSharePolicy.Policy.ALLOW_LIST) {
            launchProfilePictureRecipientsSelector()
            if (multiDeviceManager.isMultiDeviceActive) {
                // Sync new policy setting with currently set allow list values into device group (if md is active)
                taskCreator.scheduleReflectUserProfileShareWithAllowListSyncTask(
                    profilePictureRecipientsService.getAll().toSet(),
                )
            }
        } else if (multiDeviceManager.isMultiDeviceActive) {
            // Sync new policy setting to device group (if md is active)
            taskCreator.scheduleReflectUserProfileShareWithPolicySyncTask(sharePolicy)
        }
    }

    private fun showPublicKeyDialog() {
        PublicKeyDialog.newInstance(
            getString(R.string.public_key_for, userService.getIdentity()),
            userService.getPublicKey(),
        )
            .show(parentFragmentManager, "pk")
    }

    private fun updatePendingState(fragmentView: View, force: Boolean) {
        updatePendingStateTexts(fragmentView)

        if (
            force ||
            userService.getEmailLinkingState() == UserService.LinkingState_PENDING ||
            userService.getMobileLinkingState() == UserService.LinkingState_PENDING
        ) {
            lifecycleScope.launch {
                withContext(dispatcherProvider.worker) {
                    if (userService.emailLinkingState == UserService.LinkingState_PENDING) {
                        userService.checkEmailLinkState(TriggerSource.LOCAL)
                    }
                    userService.checkRevocationKey(false)
                }
                updatePendingStateTexts(fragmentView)
            }
        }
    }

    private fun updatePendingStateTexts(fragmentView: View) {
        if (!isAdded || isDetached || isRemoving) {
            return
        }

        updateLinkedEmailTextView(fragmentView)
        updateLinkedMobileTextView(fragmentView)

        if (!ConfigUtils.isOnPremBuild()) {
            val revocationKeyLastSet = userService.getLastRevocationKeySet()
            fragmentView.findViewById<TextView>(R.id.revocation_key_sum).text = if (revocationKeyLastSet != null) {
                getString(
                    R.string.revocation_key_set_at,
                    LocaleUtil.formatTimeStampString(requireContext(), revocationKeyLastSet, true),
                )
            } else {
                getString(R.string.revocation_key_not_set)
            }
        }
    }

    private fun updateLinkedEmailTextView(fragmentView: View) {
        val linkedEmailView = fragmentView.findViewById<TextView>(R.id.linked_email)
        val linkedEmail = userService.getLinkedEmail()
        val emailText = if (linkedEmail == AppConstants.EMAIL_LINKED_PLACEHOLDER) getString(R.string.unchanged) else linkedEmail
        linkedEmailView.text = when (userService.getEmailLinkingState()) {
            UserService.LinkingState_LINKED -> getString(R.string.linked_email_pattern, emailText, getString(R.string.verified))
            UserService.LinkingState_PENDING -> getString(R.string.linked_email_pattern, emailText, getString(R.string.pending))
            else -> getString(R.string.not_linked)
        }
    }

    private fun updateLinkedMobileTextView(fragmentView: View) {
        val linkedMobileText = fragmentView.findViewById<TextView>(R.id.linked_mobile)
        val linkedMobileNumber = userService.getLinkedMobile()
        val mobileNumberText = if (linkedMobileNumber == AppConstants.PHONE_LINKED_PLACEHOLDER) getString(R.string.unchanged) else linkedMobileNumber
        when (userService.getMobileLinkingState()) {
            UserService.LinkingState_LINKED -> if (mobileNumberText != null) {
                lifecycleScope.launch {
                    val phoneNumber = withContext(dispatcherProvider.worker) {
                        localeService.getHRPhoneNumber(linkedMobileNumber)
                    }
                    linkedMobileText.text = getString(R.string.linked_mobile_pattern, phoneNumber, getString(R.string.verified))
                }
            }
            UserService.LinkingState_PENDING -> {
                val newMobileNumber = userService.getLinkedMobile(true)
                if (newMobileNumber != null) {
                    lifecycleScope.launch {
                        val phoneNumber = withContext(dispatcherProvider.worker) {
                            localeService.getHRPhoneNumber(newMobileNumber)
                        }
                        linkedMobileText.text = getString(R.string.linked_mobile_pattern, phoneNumber, getString(R.string.pending))
                    }
                }
            }
            else -> {
                linkedMobileText.text = getString(R.string.not_linked)
            }
        }
    }

    private fun showRevocationPasswordDialog() {
        val dialogFragment: DialogFragment = PasswordEntryDialog.newInstance(
            /* title = */
            R.string.revocation_key_title,
            /* message = */
            R.string.revocation_explain,
            /* hint = */
            R.string.password_hint,
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
            /* minLength = */
            8,
            /* maxLength = */
            MAX_REVOCATION_PASSWORD_LENGTH,
            /* confirmHint = */
            R.string.backup_password_again_summary,
            /* inputType = */
            0,
            /* checkboxText = */
            0,
            /* showForgotPwHint = */
            PasswordEntryDialog.ForgotHintType.NONE,
        )
        dialogFragment.setTargetFragment(this, 0)
        dialogFragment.show(parentFragmentManager, DIALOG_TAG_SET_REVOCATION_KEY)
    }

    private fun showChangeEmailDialog() {
        val textEntryDialog = TextEntryDialog.newInstance(
            /* title = */
            R.string.wizard2_email_linking,
            /* message = */
            R.string.wizard2_email_hint,
            /* positive = */
            R.string.ok,
            /* neutral = */
            if (userService.getEmailLinkingState() != UserService.LinkingState_NONE) R.string.unlink else 0,
            /* negative = */
            R.string.cancel,
            /* text = */
            userService.getLinkedEmail(),
            /* inputType = */
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            /* inputFilterType = */
            TextEntryDialog.INPUT_FILTER_TYPE_NONE,
        )
        textEntryDialog.setTargetFragment(this, 0)
        textEntryDialog.show(parentFragmentManager, DIALOG_TAG_LINKED_EMAIL)
    }

    private fun startMobileLinking() {
        val presetNumber = if (userService.getMobileLinkingState() == UserService.LinkingState_NONE) {
            val presetNumber = localeService.getCountryCodePhonePrefix()
            if (!presetNumber.isNullOrEmpty()) {
                "$presetNumber "
            } else {
                presetNumber
            }
        } else {
            localeService.getHRPhoneNumber(userService.getLinkedMobile())
        }
        showMobileLinkingDialog(presetNumber)
    }

    private fun showMobileLinkingDialog(presetNumber: String) {
        val dialog = TextEntryDialog.newInstance(
            /* title = */
            R.string.wizard2_phone_linking,
            /* message = */
            R.string.wizard2_phone_hint,
            /* positive = */
            R.string.ok,
            /* neutral = */
            if (userService.getMobileLinkingState() != UserService.LinkingState_NONE) R.string.unlink else 0,
            /* negative = */
            R.string.cancel,
            /* text = */
            presetNumber,
            /* inputType = */
            InputType.TYPE_CLASS_PHONE,
            /* inputFilterType = */
            TextEntryDialog.INPUT_FILTER_TYPE_PHONE,
        )
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, DIALOG_TAG_LINKED_MOBILE)
    }

    private fun startIdRevocationSetup() {
        if (preferenceService.getLockMechanism() != PreferenceService.LOCKING_MECH_NONE) {
            checkLockToRevokeIdLauncher.launch(Unit)
        } else {
            showRevocationPasswordDialog()
        }
    }

    private fun startIdDeletion() {
        if (preferenceService.getLockMechanism() != PreferenceService.LOCKING_MECH_NONE) {
            checkLockToDeleteIdLauncher.launch(Unit)
        } else {
            confirmIdDelete()
        }
    }

    private fun startIdExport() {
        if (preferenceService.getLockMechanism() != PreferenceService.LOCKING_MECH_NONE) {
            checkLockToExportIdLauncher.launch(Unit)
        } else {
            startActivity(ExportIDActivity.createIntent(requireContext()))
        }
    }

    private fun showEditNicknameDialog() {
        val nicknameEditDialog = TextEntryDialog.newInstance(
            /* title = */
            R.string.set_nickname_title,
            /* message = */
            R.string.wizard3_nickname_hint,
            /* positive = */
            R.string.ok,
            /* neutral = */
            0,
            /* negative = */
            R.string.cancel,
            /* text = */
            userService.getPublicNickname(),
            /* inputType = */
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            /* inputFilterType = */
            0,
            /* maxLength = */
            ProtocolDefines.PUSH_FROM_LEN,
        )
        nicknameEditDialog.setTargetFragment(this, 0)
        nicknameEditDialog.show(parentFragmentManager, DIALOG_TAG_EDIT_NICKNAME)
    }

    private fun launchProfilePictureRecipientsSelector() {
        startActivity(ProfilePicRecipientsActivity.createIntent(requireContext()))
    }

    private fun confirmIdDelete() {
        val dialogFragment = GenericAlertDialog.newInstance(
            /* title = */
            R.string.delete_id_title,
            /* message = */
            R.string.delete_id_message,
            /* positive = */
            R.string.delete_id_title,
            /* negative = */
            R.string.cancel,
        )
        dialogFragment.setTargetFragment(this)
        dialogFragment.show(parentFragmentManager, DIALOG_TAG_DELETE_ID)
    }

    @UiThread
    private fun reloadNickname() {
        nicknameTextView?.text = userService.publicNickname.takeUnlessEmpty() ?: userService.identity
    }

    private fun setRevocationKey(revocationKey: String) {
        lifecycleScope.launch {
            showProgressDialog(R.string.revocation_key_title)
            try {
                val success = withContext(dispatcherProvider.worker) {
                    userService.setRevocationKey(revocationKey)
                }
                if (!success) {
                    showToast(getString(R.string.error) + ": " + getString(R.string.revocation_key_not_set), ToastDuration.LONG)
                }
            } finally {
                updatePendingStateTexts(requireView())
                dismissProgressDialog()
            }
        }
    }

    override fun onYes(tag: String?, data: Any?) {
        when (tag) {
            DIALOG_TAG_DELETE_ID -> {
                logger.info("Showing second ID deletion warning")
                showIdDeletionConfirmDialog()
            }
            DIALOG_TAG_REALLY_DELETE -> {
                logger.info("Delete ID confirmed")
                deleteIdentity()
            }
            DIALOG_TAG_LINKED_MOBILE_CONFIRM -> {
                logger.info("Verify mobile confirmed")
                launchMobileVerification(data as String)
            }
            else -> Unit
        }
    }

    private fun showIdDeletionConfirmDialog() {
        val dialogFragment = GenericAlertDialog.newInstance(
            /* title = */
            R.string.delete_id_title,
            /* message = */
            R.string.delete_id_message2,
            /* positive = */
            R.string.delete_id_title,
            /* negative = */
            R.string.cancel,
        )
        dialogFragment.setTargetFragment(this)
        dialogFragment.show(parentFragmentManager, DIALOG_TAG_REALLY_DELETE)
    }

    private fun deleteIdentity() {
        showProgressDialog(R.string.delete_id_title)
        lifecycleScope.launch {
            try {
                resetAppTask.execute()
            } catch (e: Exception) {
                logger.error("Failed to reset app", e)
                showToast(R.string.an_error_occurred, ToastDuration.LONG)
                exitProcess(0)
            }
        }
    }

    private fun launchMobileVerification(normalizedPhoneNumber: String) {
        lifecycleScope.launch {
            try {
                withContext(dispatcherProvider.worker) {
                    userService.linkWithMobileNumber(normalizedPhoneNumber, TriggerSource.LOCAL)
                }
                showToast(R.string.verification_started, ToastDuration.LONG)
            } catch (e: LinkMobileNoException) {
                logger.warn("Got LinkMobileNoException", e)
                showMobileVerifyErrorDialog(e.message!!)
            } catch (e: Exception) {
                logger.error("Failed to verify mobile number", e)
                showMobileVerifyErrorDialog(getString(R.string.an_error_occurred))
            } finally {
                updatePendingStateTexts(requireView())
            }
        }
    }

    private fun showMobileVerifyErrorDialog(error: String) {
        SimpleStringAlertDialog.newInstance(R.string.verify_title, error)
            .show(parentFragmentManager, DIALOG_TAG_VERIFY_MOBILE_ERROR)
    }

    override fun onYes(tag: String, text: String) {
        when (tag) {
            DIALOG_TAG_LINKED_MOBILE -> {
                logger.info("Link mobile clicked, showing confirm dialog")
                showLinkMobileConfirmDialog(text)
            }
            DIALOG_TAG_LINKED_EMAIL -> {
                if (text.isEmpty()) {
                    logger.info("Email for linking removed")
                    unlinkEmail()
                } else {
                    logger.info("Email for linking provided")
                    linkWithEmail(text)
                }
            }
            DIALOG_TAG_EDIT_NICKNAME -> {
                val newNickname = text.trim { it <= ' ' }
                if (newNickname != userService.getPublicNickname()) {
                    logger.info("New nickname set")
                    userService.setPublicNickname(newNickname, TriggerSource.LOCAL)
                    reloadNickname()
                }
            }
            else -> Unit
        }
    }

    private fun showLinkMobileConfirmDialog(phoneNumber: String) {
        val normalizedPhoneNumber = localeService.getNormalizedPhoneNumber(phoneNumber)
        val alertDialog = GenericAlertDialog.newInstance(
            /* title = */
            R.string.wizard2_phone_number_confirm_title,
            /* messageString = */
            String.format(getString(R.string.wizard2_phone_number_confirm), normalizedPhoneNumber),
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
        )
        alertDialog.setData(normalizedPhoneNumber)
        alertDialog.setTargetFragment(this)
        alertDialog.show(parentFragmentManager, DIALOG_TAG_LINKED_MOBILE_CONFIRM)
    }

    private fun linkWithEmail(emailAddress: String) {
        lifecycleScope.launch {
            try {
                if (userService.getEmailLinkingState() != UserService.LinkingState_NONE && userService.getLinkedEmail() == emailAddress) {
                    showToast(R.string.email_already_linked, ToastDuration.LONG)
                } else {
                    showProgressDialog(R.string.wizard2_email_linking)
                    withContext(dispatcherProvider.worker) {
                        userService.linkWithEmail(emailAddress, TriggerSource.LOCAL)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to link email", e)
                showToast(R.string.an_error_occurred, ToastDuration.LONG)
            } finally {
                dismissProgressDialog()
                updatePendingStateTexts(requireView())
            }
        }
    }

    private fun showProgressDialog(@StringRes title: Int) {
        GenericProgressDialog.newInstance(title, R.string.please_wait)
            .show(parentFragmentManager, DIALOG_TAG_PROGRESS)
    }

    private fun dismissProgressDialog() {
        DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_PROGRESS, true)
    }

    override fun onYes(tag: String?, text: String, isChecked: Boolean, data: Any?) {
        if (tag == DIALOG_TAG_SET_REVOCATION_KEY) {
            logger.info("Revocation key set")
            setRevocationKey(text)
        }
    }

    override fun onNo(tag: String?) {
        // nothing to do here
    }

    override fun onNeutral(tag: String) {
        when (tag) {
            DIALOG_TAG_LINKED_EMAIL -> {
                logger.info("Unlinking email")
                unlinkEmail()
            }
            DIALOG_TAG_LINKED_MOBILE -> {
                logger.info("Unlinking mobile")
                unlinkMobile()
            }
            else -> Unit
        }
    }

    private fun unlinkEmail() {
        lifecycleScope.launch {
            try {
                if (userService.getEmailLinkingState() != UserService.LinkingState_NONE) {
                    showProgressDialog(R.string.unlinking_email)
                    withContext(dispatcherProvider.worker) {
                        userService.unlinkEmail(TriggerSource.LOCAL)
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to unlink email", e)
                showToast(R.string.an_error_occurred, ToastDuration.LONG)
            } finally {
                dismissProgressDialog()
                updatePendingStateTexts(requireView())
            }
        }
    }

    private fun unlinkMobile() {
        lifecycleScope.launch {
            try {
                withContext(dispatcherProvider.worker) {
                    userService.unlinkMobileNumber(TriggerSource.LOCAL)
                }
            } catch (e: Exception) {
                logger.error("Failed to unlink phone number", e)
                showToast(R.string.an_error_occurred)
            } finally {
                updatePendingStateTexts(requireView())
            }
        }
    }

    fun onLogoClicked() {
        val scrollView = view?.findViewById<NestedScrollView?>(R.id.fragment_id_container)
            ?: return
        logger.info("Logo clicked, scrolling to top")
        scrollView.scrollTo(0, 0)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && this.hidden) {
            updatePendingState(requireView(), false)
        }
        this.hidden = hidden
    }

    override fun onStart() {
        super.onStart()
        ListenerManager.smsVerificationListeners.add(smsVerificationListener)
    }

    override fun onResume() {
        super.onResume()
        setupPicReleaseSpinner()
    }

    override fun onStop() {
        ListenerManager.smsVerificationListeners.remove(smsVerificationListener)
        super.onStop()
    }

    override fun onDestroyView() {
        ListenerManager.profileListeners.remove(profileListener)
        super.onDestroyView()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        avatarView?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        avatarView?.onActivityResult(requestCode, resultCode, intent)
    }

    companion object {
        private const val MAX_REVOCATION_PASSWORD_LENGTH = 256

        private const val DIALOG_TAG_PROGRESS = "progress"
        private const val DIALOG_TAG_EDIT_NICKNAME = "cedit"
        private const val DIALOG_TAG_SET_REVOCATION_KEY = "setRevocationKey"
        private const val DIALOG_TAG_LINKED_EMAIL = "linkedEmail"
        private const val DIALOG_TAG_LINKED_MOBILE = "linkedMobile"
        private const val DIALOG_TAG_REALLY_DELETE = "reallyDeleteId"
        private const val DIALOG_TAG_DELETE_ID = "deleteId"
        private const val DIALOG_TAG_LINKED_MOBILE_CONFIRM = "cfm"
        private const val DIALOG_TAG_VERIFY_MOBILE_ERROR = "ve"

        private const val REQUEST_KEY_EDIT_AVAILABILITY_STATUS = "edit-availability-status-from-user-profile"
    }
}
