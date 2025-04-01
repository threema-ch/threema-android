/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.Space
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.activities.PermissionRequestActivity.Companion.INTENT_PERMISSION_REQUESTS
import ch.threema.app.ui.PermissionIconView
import ch.threema.app.ui.PermissionIconView.PermissionIconState
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.PermissionRequest
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("PermissionRequestActivity")

/**
 * This activity guides the user through the permission requests. This activity finishes with
 * [Activity.RESULT_OK] if all the given (required) permissions have been granted. If the activity
 * finishes with [Activity.RESULT_CANCELED], then at least one required permission is not yet given.
 *
 * The permission requests can be added to the intent as a list of [PermissionRequest] with the key
 * [INTENT_PERMISSION_REQUESTS].
 */
class PermissionRequestActivity : ThreemaActivity() {

    companion object {
        const val INTENT_PERMISSION_REQUESTS = "permission_requests_extra"
    }

    private lateinit var preferences: SharedPreferences

    private val permissionStates: MutableList<Pair<PermissionState, PermissionIconView>> =
        ArrayList()

    private lateinit var permissionIconViewContainer: LinearLayout

    private lateinit var permissionTitleTextView: TextView
    private lateinit var permissionDescriptionTextView: TextView
    private lateinit var permissionSettingsExplanation: TextView

    private lateinit var permissionGrantButton: Button
    private lateinit var permissionGrantSettingsButton: Button
    private lateinit var permissionContinueButton: Button
    private lateinit var permissionIgnoreButton: Button
    private lateinit var permissionSkipButton: Button

    private var currentPosition = 0

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            logger.info("Permission result received: {}", isGranted)

            // Note that at this point 'goToSettings' is not yet true if the user just denied
            // the permission request. In this case the view will be updated to explain the next
            // steps the user needs to perform.
            if (!isGranted && getCurrentPermissionState().goToSettings) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                startActivity(intent)
            }

            updatePermissionStates()
            if (updateCurrentPositionOrLeave()) {
                updateView(true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ConfigUtils.configureSystemBars(this)

        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            finishWithSuccess()
            return
        }

        setContentView(R.layout.activity_permission_request)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        permissionIconViewContainer = findViewById(R.id.permission_progress)

        permissionTitleTextView = findViewById(R.id.permission_title)
        permissionDescriptionTextView = findViewById(R.id.permission_description)
        permissionSettingsExplanation = findViewById(R.id.permission_settings_explanation)

        permissionGrantButton = findViewById(R.id.grant_permission)
        permissionGrantSettingsButton = findViewById(R.id.grant_permission_settings)
        permissionContinueButton = findViewById(R.id.permission_continue)
        permissionIgnoreButton = findViewById(R.id.ignore_permission)
        permissionSkipButton = findViewById(R.id.skip_permission)

        permissionIgnoreButton.setOnClickListener {
            val currentPermissionState = getCurrentPermissionState()
            if (currentPermissionState.ignorePermissionPreference == null) {
                logger.error("Permission ignore button should not be shown for permissions without preference")
                updateView(true)
                return@setOnClickListener
            }

            logger.info("Save do-not-ask again setting for {}", currentPermissionState.title)
            preferences.edit {
                putBoolean(currentPermissionState.ignorePermissionPreference, true)
            }

            currentPermissionState.asked = true

            if (updateCurrentPositionOrLeave()) {
                updateView(true)
            }
        }

        permissionGrantButton.setOnClickListener {
            val currentPermission = getCurrentPermissionState().permission
            logger.info("Request permission {}", currentPermission)
            requestPermissionLauncher.launch(currentPermission)
        }

        permissionGrantSettingsButton.setOnClickListener {
            val currentPermission = getCurrentPermissionState().permission
            logger.info("Request permission {} (via settings)", currentPermission)
            requestPermissionLauncher.launch(currentPermission)
        }

        permissionContinueButton.setOnClickListener {
            if (updateCurrentPositionOrLeave()) {
                updateView(true)
            }
        }

        permissionSkipButton.setOnClickListener {
            getCurrentPermissionState().asked = true
            if (updateCurrentPositionOrLeave()) {
                updateView(true)
            }
        }

        permissionSkipButton.text =
            getString(R.string.use_threema_without_this_permission, getString(R.string.app_name))

        initializePermissionRequests()

        logger.info("Initialized PermissionRequestActivity for the following permission requests")
        logPermissionStates()
    }

    override fun onStart() {
        super.onStart()

        updatePermissionStates()
        if (updateCurrentPositionOrLeave()) {
            updateView(false)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (getFirstPendingPermissionStatePosition() == null) {
            finishWithSuccess()
        } else {
            finishWithoutSuccess()
        }
    }

    private fun initializePermissionRequests() {
        val requests = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(
                INTENT_PERMISSION_REQUESTS,
                PermissionRequest::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(INTENT_PERMISSION_REQUESTS)
        }

        if (requests == null) {
            logger.error("No permission requests in intent")
            finish()
            return
        }

        // Only create permission states for permissions that are required on the current API level.
        for (i in requests.filter { it.permission.isRequired() }.indices) {
            val request = requests[i]
            val view = createPermissionView(this, request)
            view.setOnClickListener {
                currentPosition = i
                updateView(true)
            }
            permissionStates.add(PermissionState(request, preferences, this) to view)
        }

        initializePermissionViews()
    }

    private fun createPermissionView(
        context: Context,
        request: PermissionRequest
    ): PermissionIconView {
        val view = PermissionIconView(context)
        view.setIcon(request.icon)
        return view
    }

    private fun initializePermissionViews() {
        permissionStates.map { it.second }.forEach { iconView ->
            permissionIconViewContainer.addView(createSpaceView())
            permissionIconViewContainer.addView(iconView)
        }
        permissionIconViewContainer.addView(createSpaceView())
    }

    private fun createSpaceView(): Space {
        return Space(this).also {
            it.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    /**
     * Updates the texts and buttons based on [currentPosition].
     */
    private fun updateView(animate: Boolean) {
        val permissionState = getCurrentPermissionState()
        permissionTitleTextView.text = permissionState.title
        permissionDescriptionTextView.text = permissionState.description
        permissionSettingsExplanation.text =
            getString(R.string.permission_enable_in_settings_rationale, permissionState.title)

        permissionSkipButton.visibility = visibleOrGone(shouldShowSkipButton(permissionState))

        permissionIgnoreButton.visibility = visibleOrGone(shouldShowIgnoreButton(permissionState))

        for ((state, view) in permissionStates) {
            view.setHighlighted(state == permissionState, animate)
            view.updateBadge(getBadgeState(state))
        }

        permissionSettingsExplanation.visibility =
            visibleOrGone(shouldShowGoToSettingsExplanation(permissionState))

        when {
            permissionState.granted -> {
                permissionGrantButton.visibility = View.INVISIBLE
                permissionGrantSettingsButton.visibility = View.INVISIBLE
                permissionContinueButton.visibility = View.VISIBLE
            }

            permissionState.goToSettings -> {
                permissionGrantButton.visibility = View.INVISIBLE
                permissionGrantSettingsButton.visibility = View.VISIBLE
                permissionContinueButton.visibility = View.INVISIBLE
            }

            else -> {
                permissionGrantButton.visibility = View.VISIBLE
                permissionGrantSettingsButton.visibility = View.INVISIBLE
                permissionContinueButton.visibility = View.INVISIBLE
            }
        }

        permissionGrantButton.text = when {
            // If the permission is already granted, then continue to the next permission
            permissionState.granted -> getString(R.string.next)
            // Note that this does not work for 'Ask every time'-permissions as we cannot properly
            // detect their state in advance. Therefore the user may still see a permission dialog
            // without needing to go to the settings.
            permissionState.goToSettings -> getString(R.string.grant_permission_settings)
            // Otherwise show default grant permission text
            else -> getString(R.string.grant_permission)
        }
    }

    /**
     * Get the current permission based on [currentPosition].
     */
    private fun getCurrentPermissionState(): PermissionState =
        permissionStates[currentPosition].first

    /**
     * Get the position of the first permission that still needs user action. This can be a required
     * permission that has not yet been granted or an optional permission that has not yet been
     * granted or denied.
     *
     * @return the position of the pending permission, or null if all have been handled
     */
    private fun getFirstPendingPermissionStatePosition(): Int? {
        return permissionStates
            .withIndex()
            .firstOrNull {
                val request = it.value.first
                val requiredAndNotGranted = !request.optional && !request.granted
                val optionalNotGrantedAndNotAsked =
                    request.optional && !request.granted && !request.asked
                requiredAndNotGranted || optionalNotGrantedAndNotAsked
            }?.index
    }

    /**
     * Updates the [currentPosition] based on the current state of the permission. Note that the
     * permission states may need to be updated first with [updatePermissionStates].
     *
     * If all required permissions are given and the optional permissions have been granted or
     * rejected, then the activity gets finished.
     *
     * @return true if the position has been updated, false if the activity will be finished
     */
    private fun updateCurrentPositionOrLeave(): Boolean {
        val firstPendingPosition = getFirstPendingPermissionStatePosition()
        return if (firstPendingPosition != null) {
            currentPosition = firstPendingPosition
            true
        } else {
            finishWithSuccess()
            false
        }
    }

    /**
     * Updates the [permissionStates] regarding the [PermissionState.granted] and
     * [PermissionState.goToSettings].
     */
    private fun updatePermissionStates() {
        for ((request, _) in permissionStates) {
            request.granted =
                ContextCompat.checkSelfPermission(this, request.permission) == PERMISSION_GRANTED
            request.goToSettings =
                !ActivityCompat.shouldShowRequestPermissionRationale(this, request.permission)

            // Reset the permission ignore preference if the permission has been granted anyway.
            // This means that the permission will be requested again, once the user denies the
            // permission.
            if (request.ignorePermissionPreference != null && request.granted) {
                preferences.edit {
                    putBoolean(request.ignorePermissionPreference, false)
                }
            }
        }
    }

    private fun finishWithSuccess() {
        logger.info("All required permissions are granted")
        logPermissionStates()
        setResult(RESULT_OK)
        finish()
    }

    private fun finishWithoutSuccess() {
        logger.info("Some required permissions are not granted")
        logPermissionStates()
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun logPermissionStates() {
        for (permission in permissionStates.map { it.first }) {
            logger.info(
                "Permission '{}': granted={}, redirectToSettings={}",
                permission.permission,
                permission.granted,
                permission.goToSettings
            )
        }
    }

    private fun shouldShowSkipButton(permissionState: PermissionState) =
        !permissionState.granted && permissionState.optional

    private fun shouldShowIgnoreButton(permissionState: PermissionState) =
        !permissionState.granted && permissionState.optional && permissionState.ignorePermissionPreference != null

    private fun shouldShowGoToSettingsExplanation(permissionState: PermissionState) =
        !permissionState.granted && permissionState.goToSettings

    private fun getBadgeState(permissionState: PermissionState): PermissionIconState =
        if (permissionState.granted) {
            PermissionIconState.GRANTED
        } else if (permissionState.asked && permissionState.optional) {
            PermissionIconState.OPTIONAL_AND_DENIED
        } else {
            PermissionIconState.REQUIRED_OR_UNDECIDED
        }

    private fun visibleOrInvisible(visible: Boolean): Int = if (visible) {
        View.VISIBLE
    } else {
        View.INVISIBLE
    }

    private fun visibleOrGone(visible: Boolean): Int = if (visible) {
        View.VISIBLE
    } else {
        View.GONE
    }

    /**
     * The current state of the permission request.
     */
    private data class PermissionState(
        val permission: String,                     // the permission string
        val title: String,                          // the name of the permission
        val description: String,                    // the explanation of the permission
        var goToSettings: Boolean,                  // true if the user (is likely) redirected to the settings
        var granted: Boolean,                       // true if the permission is granted
        var asked: Boolean,                         // true if the user has granted or denied this permission
        var optional: Boolean,                      // true if this permission is optional
        val ignorePermissionPreference: String?,    // the 'never-ask-again'-preference (if nonnull)
    ) {
        constructor(
            permissionRequest: PermissionRequest,
            preferences: SharedPreferences,
            context: Context
        ) : this(
            permissionRequest.permission.getPermissionString(),
            permissionRequest.permission.getPermissionName(context),
            permissionRequest.description,
            false,
            false,
            preferences.getBoolean(permissionRequest.permissionIgnorePreference, false),
            permissionRequest.optional,
            permissionRequest.permissionIgnorePreference
        )
    }
}
