/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.voip.groupcall.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
import android.graphics.Bitmap
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationGroups
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.PreferenceService
import ch.threema.app.stores.IdentityStore
import ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE
import ch.threema.app.utils.RuntimeUtil
import ch.threema.app.voip.CallAudioManager
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.app.voip.groupcall.GroupCallException
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.LocalGroupId
import ch.threema.app.voip.groupcall.sfu.CallId
import ch.threema.app.voip.groupcall.sfu.GroupCallController
import ch.threema.app.voip.groupcall.sfu.GroupCallDependencies
import ch.threema.app.voip.groupcall.sfu.GroupCallParameters
import ch.threema.app.voip.groupcall.sfu.SfuConnection
import ch.threema.app.voip.services.VoipStateService
import ch.threema.app.voip.util.VoipUtil
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.storage.models.GroupModel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch

private val logger = LoggingUtil.getThreemaLogger("GroupCallService")

/**
 * A foreground service which handles group calls. It is responsible for establishing a connection
 * to the sfu and provide all methods required for controlling the call.
 *
 * An instance of the foreground service should be responsible for a single call. Thus, the call id
 * this instance handles should never change during the service's lifecycle.
 *
 * If it is required to change to another call, the service must be stopped and restarted with the new
 * call id.
 */
class GroupCallService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 5488088

        private const val EXTRA_SFU_BASE_URL = "EXTRA_SFU_BASE_URL"
        private const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        private const val EXTRA_GROUP_ID = "EXTRA_GROUP_ID"
        private const val EXTRA_LEAVE_CALL = "EXTRA_LEAVE_CALL"

        // Do NOT use the same request codes as in
        // {@link NotificationServiceImpl#GROUP_CALL_*_REQUEST_CODE}
        // Otherwise the same pending intent might be used.
        private const val REQUEST_CODE_JOIN_CALL = 1000
        private const val REQUEST_CODE_LEAVE_CALL = 1001

        private val FG_SERVICE_TYPE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        fun getStartIntent(
            context: Context,
            sfuBaseUrl: String,
            callId: CallId,
            groupId: LocalGroupId,
        ): Intent {
            return getServiceIntent(context)
                .putExtra(EXTRA_SFU_BASE_URL, sfuBaseUrl)
                .putExtra(EXTRA_CALL_ID, callId.bytes)
                .putExtra(EXTRA_GROUP_ID, groupId.id)
        }

        private fun getLeaveCallIntent(
            context: Context,
            callId: CallId,
            groupId: LocalGroupId,
        ): Intent {
            return getServiceIntent(context)
                .putExtra(EXTRA_CALL_ID, callId.bytes)
                .putExtra(EXTRA_GROUP_ID, groupId.id)
                .putExtra(EXTRA_LEAVE_CALL, true)
        }

        fun getStopIntent(context: Context): Intent = getServiceIntent(context)

        private fun getServiceIntent(context: Context): Intent {
            return Intent(context, GroupCallService::class.java)
        }
    }

    private val serviceRunning = AtomicBoolean(false)

    private var binder: GroupCallServiceBinder? = null

    private var groupCallController: GroupCallControllerImpl? = null
    private val controllerDeferred = CompletableDeferred<GroupCallController>()

    private var audioManager: CallAudioManager? = null
    private val audioManagerDeferred = CompletableDeferred<CallAudioManager>()

    private lateinit var sfuBaseUrl: String
    private lateinit var callId: CallId
    private lateinit var groupId: ServiceGroupId

    private lateinit var identityStore: IdentityStore
    private lateinit var contactService: ContactService
    private lateinit var groupService: GroupService
    private lateinit var apiConnector: APIConnector
    private lateinit var contactModelRepository: ContactModelRepository
    private lateinit var sfuConnection: SfuConnection
    private lateinit var preferenceService: PreferenceService
    private lateinit var voipStateService: VoipStateService

    private var isLeaveCallIntent = false

    @Suppress("DEPRECATION")
    private val onCallStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)

            if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                groupCallController?.leave()
            }
        }
    }

    override fun onBind(intent: Intent?): GroupCallServiceBinder? {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        binder = GroupCallServiceBinder(controllerDeferred, audioManagerDeferred)
        initDependencies()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceRunning.set(true)
        try {
            handleIntent(intent)
            if (isLeaveCallIntent) {
                groupCallController.let { controller ->
                    if (controller != null) {
                        controller.leave()
                    } else {
                        logger.warn("Leave call intent, but call has not been joined")
                        stopService()
                    }
                }
            } else {
                joinCall()
                startForeground()
            }
        } catch (e: Exception) {
            logger.error("Error during initialisation of foreground service", e)
            stopService()
        }
        return START_NOT_STICKY
    }

    private fun initDependencies() {
        val serviceManager = ThreemaApplication.requireServiceManager()
        identityStore = serviceManager.identityStore
        contactService = serviceManager.contactService
        sfuConnection = serviceManager.sfuConnection
        groupService = serviceManager.groupService
        apiConnector = serviceManager.apiConnector
        contactModelRepository = serviceManager.modelRepositories.contacts
        preferenceService = serviceManager.preferenceService
        voipStateService = serviceManager.voipStateService
    }

    private fun handleIntent(intent: Intent?) {
        val intentSfuBaseUrl = intent?.getStringExtra(EXTRA_SFU_BASE_URL)
        val callIdBytes = intent?.getByteArrayExtra(EXTRA_CALL_ID)
        val intentCallId = callIdBytes?.let { CallId(callIdBytes) }
        val groupIdExtra = intent?.getIntExtra(EXTRA_GROUP_ID, -1)
        if (!this::callId.isInitialized && !this::groupId.isInitialized && !this::sfuBaseUrl.isInitialized) {
            if (intentSfuBaseUrl == null) {
                throw GroupCallException("No sfu base url set")
            }
            sfuBaseUrl = intentSfuBaseUrl
            if (intentCallId == null) {
                throw GroupCallException("No call id set")
            }
            callId = intentCallId
            if (groupIdExtra == null || groupIdExtra == -1) {
                throw GroupCallException("No group id set")
            }
            groupId = ServiceGroupId(groupIdExtra)
        } else {
            val leaveCall = intent?.getBooleanExtra(EXTRA_LEAVE_CALL, false) == true
            isLeaveCallIntent = leaveCall && groupId.id == groupIdExtra && callId == intentCallId
        }
    }

    private fun startForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            getForegroundNotification().apply {
                this.flags = this.flags or
                    NotificationCompat.FLAG_NO_CLEAR or
                    NotificationCompat.FLAG_ONGOING_EVENT
            },
            FG_SERVICE_TYPE,
        )
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(startedAt: Long) {
        val notification = getForegroundNotification(startedAt)
        if (serviceRunning.get()) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun getForegroundNotification(startedAt: Long = System.currentTimeMillis()): Notification {
        val group = groupService.getById(groupId.id)
        val callerPerson = Person.Builder()
            .setName(getNotificationTitle(group))
            .setImportant(true)
            .build()
        val builder = NotificationCompat.Builder(this, NotificationChannels.NOTIFICATION_CHANNEL_IN_CALL)
            .setContentTitle(getNotificationTitle(group))
            .setContentText(getString(R.string.group_call))
            .setSmallIcon(R.drawable.ic_phone_locked_outline)
            .setLargeIcon(getAvatar(group))
            .setColor(ResourcesCompat.getColor(resources, R.color.md_theme_light_primary, theme))
            .setLocalOnly(true)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(startedAt)
            .setGroup(NotificationGroups.CALLS)
            .setGroupSummary(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(getJoinCallPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT))
            .apply {
                getLeaveCallPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)?.let { leaveCallPendingIntent ->
                    setStyle(
                        NotificationCompat.CallStyle.forOngoingCall(
                            callerPerson,
                            leaveCallPendingIntent,
                        ),
                    )
                }
            }

        return builder.build()
    }

    private fun getJoinCallPendingIntent(flags: Int): PendingIntent? {
        return PendingIntent.getActivity(
            applicationContext,
            REQUEST_CODE_JOIN_CALL,
            GroupCallActivity.getJoinCallIntent(applicationContext, groupId.id),
            flags or PENDING_INTENT_FLAG_IMMUTABLE,
        )
    }

    private fun getLeaveCallPendingIntent(flags: Int): PendingIntent? {
        return PendingIntent.getService(
            applicationContext,
            REQUEST_CODE_LEAVE_CALL,
            getLeaveCallIntent(applicationContext, callId, groupId.localGroupId),
            flags or PENDING_INTENT_FLAG_IMMUTABLE,
        )
    }

    private fun getNotificationTitle(groupModel: GroupModel?): String {
        return groupModel?.name ?: getString(R.string.group_call)
    }

    private fun getAvatar(groupModel: GroupModel?): Bitmap? {
        return groupModel?.let { groupService.getAvatar(it, false, true) }
    }

    // TODO(ANDR-1964): Could this be called twice for a call and cause problems?
    //   make sure groupCallController and audioManager will only be initialised once per service lifetime
    private fun joinCall() {
        logger.info("Join call (callId={}, sfuBaseUrl={})", callId, sfuBaseUrl)
        stopOngoingOneToOneCall()

        setPSTNCallStateListener()

        CoroutineScope(GroupCallThreadUtil.dispatcher).launch {
            groupCallController = GroupCallControllerImpl(
                callId,
                this@GroupCallService::leaveCall,
                contactService.me,
            ).also { controller ->
                controller.parameters = GroupCallParameters(
                    preferenceService.allowWebrtcIpv6(),
                    preferenceService.aecMode,
                    PreferenceService.VIDEO_CODEC_SW,
                )
                controller.dependencies = GroupCallDependencies(
                    identityStore,
                    contactService,
                    groupService,
                    apiConnector,
                    contactModelRepository,
                )
                CoroutineScope(GroupCallThreadUtil.dispatcher).launch {
                    launch {
                        controller.join(
                            applicationContext,
                            sfuBaseUrl,
                            sfuConnection,
                        ) { stopService() }
                    }
                    val startedAt = controller.descriptionSignal.await().startedAt.toLong()
                    updateNotification(startedAt)
                }
                controllerDeferred.complete(controller)
                initAudioManager()
            }
        }
    }

    /**
     * Set a listener for incoming PSTN calls while being in a group call. If a PSTN call is
     * accepted, the current group call will be left.
     */
    @Suppress("DEPRECATION")
    private fun setPSTNCallStateListener() {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE)
        if (telephonyManager is TelephonyManager && (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                )
        ) {
            telephonyManager.listen(onCallStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun initAudioManager() {
        audioManager = CallAudioManager(applicationContext)
            .also {
                it.start()
                audioManagerDeferred.complete(it)
            }
    }

    private fun stopOngoingOneToOneCall() {
        val state = voipStateService.callState
        if (!state.isIdle) {
            logger.info("Stopping ongoing 1:1 call (state={})", state)
            VoipUtil.sendOneToOneCallHangupCommand(this)
        }
    }

    private fun leaveCall() {
        logger.info("Call has been left. Stop service.")
        if (groupCallController?.hasForeverAloneTimerFired() == true) {
            RuntimeUtil.runOnUiThread(
                Runnable {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.group_call_inactivity_left),
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
        stopService()
    }

    private fun stopService() {
        logger.info("Stop service")
        serviceRunning.set(false)
        stopSelf()
    }

    override fun onDestroy() {
        logger.info("Destroy GroupCallService")
        super.onDestroy()
        val exception = ThreemaException("GroupCallService has been stopped")
        // If controllerDeferred and callEnded are not yet completed, they are completed exceptionally.
        // If they already _are_ completed, nothing will happen when calling `completeExceptionally`
        controllerDeferred.completeExceptionally(exception)
        groupCallController?.callLeftSignal?.completeExceptionally(exception)
        groupCallController = null
        audioManagerDeferred.completeExceptionally(exception)
        audioManager?.stop()
        audioManager = null
        binder = null
        getJoinCallPendingIntent(PendingIntent.FLAG_NO_CREATE)?.cancel()
        getLeaveCallPendingIntent(PendingIntent.FLAG_NO_CREATE)?.cancel()
    }

    // wrapper for group id to make it an object and `lateinit` can be used
    private data class ServiceGroupId(val id: Int) {
        val localGroupId
            get() = LocalGroupId(id)
    }
}
