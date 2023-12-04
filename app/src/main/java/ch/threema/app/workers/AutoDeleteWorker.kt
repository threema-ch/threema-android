/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

package ch.threema.app.workers

import android.content.Context
import android.text.format.DateUtils
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ConversationService
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.ballot.BallotService
import ch.threema.app.utils.AppRestrictionUtil
import ch.threema.app.utils.AutoDeleteUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.DisplayTag
import ch.threema.storage.models.data.media.BallotDataModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

class AutoDeleteWorker(context: Context, workerParameters: WorkerParameters) : Worker(context, workerParameters) {
    private lateinit var preferenceService: PreferenceService
    private lateinit var conversationService: ConversationService
    private lateinit var groupService: GroupService
    private lateinit var messageService: MessageService
    private lateinit var fileService: FileService
    private lateinit var ballotService: BallotService

    companion object {
        private val logger = LoggingUtil.getThreemaLogger("AutoDeleteWorker")

        const val EXTRA_GRACE_DAYS = "grace_days"
        private const val schedulePeriodMs = DateUtils.DAY_IN_MILLIS / 2

        fun scheduleAutoDelete(context: Context) : Boolean {
            val graceDays = getGraceDays(context)
            if (graceDays != null && graceDays > ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_OFF_VALUE) {
                logger.info("Scheduling auto delete")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val operation = WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            ThreemaApplication.WORKER_AUTO_DELETE,
                            ExistingPeriodicWorkPolicy.UPDATE,
                            buildPeriodicWorkRequest(graceDays)
                        )
                        logger.info("Schedule result = {}",
                            withContext(Dispatchers.IO) {
                                operation.result.get()
                            })
                    } catch (e: IllegalStateException) {
                        logger.error("Exception scheduling auto delete", e)
                    }
                }
                return true
            } else {
                logger.info("No auto delete configured")
            }
            return false
        }

        private fun buildPeriodicWorkRequest(graceDays: Int): PeriodicWorkRequest {
            logger.info("Building Periodic Work Request with graceDays = {}", graceDays)

            val data = Data.Builder()
                .putInt(EXTRA_GRACE_DAYS, graceDays)
                .build()

            return PeriodicWorkRequestBuilder<AutoDeleteWorker>(schedulePeriodMs, TimeUnit.MILLISECONDS)
                    .setInitialDelay(5, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
                    .apply { setInputData(data) }
                    .build()
        }

        private fun getGraceDays(context: Context) : Int? {
            var newGraceDays = if (ConfigUtils.isWorkRestricted()) AppRestrictionUtil.getKeepMessagesDays(context) else null

            if (newGraceDays == null) {
                val preferenceService = ThreemaApplication.getServiceManager()?.preferenceService
                if (preferenceService != null) {
                    newGraceDays = preferenceService.autoDeleteDays
                }
            }
            return newGraceDays
        }

        fun cancelAutoDelete(context: Context) {
            logger.info("Canceling auto delete")
            CoroutineScope(Dispatchers.IO).launch {
                val operation = WorkManager.getInstance(context)
                    .cancelUniqueWork(ThreemaApplication.WORKER_AUTO_DELETE)
                logger.info("Cancel result = {}",
                    withContext(Dispatchers.IO) {
                        operation.result.get()
                    })
            }
        }
    }

    override fun doWork(): Result {
        logger.info("Start auto delete work")
        val serviceManager = ThreemaApplication.getServiceManager()

        try {
            if (serviceManager != null) {
                this.preferenceService = serviceManager.preferenceService
                this.conversationService = serviceManager.conversationService
                this.groupService = serviceManager.groupService
                this.messageService = serviceManager.messageService
                this.fileService = serviceManager.fileService
                this.ballotService = serviceManager.ballotService
            } else {
                // auto cleanup cannot be performed if the service manager is not available - try again later
                return Result.retry()
            }
        } catch (e: ThreemaException) {
            // we cannot perform our work if master key is locked - try again later
            logger.error("Auto cleanup failed. Master Key is locked.", e)
            return Result.retry()
        }

        val graceDays: Int = inputData.getInt(EXTRA_GRACE_DAYS, ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_OFF_VALUE)
        if (graceDays <= ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_OFF_VALUE) {
            logger.info("Stopping auto delete with graceDays = {}", graceDays)
            return Result.success()
        }

        logger.info("Performing auto delete with graceDays = {}", graceDays)

        var numDeletedMessages = 0
        val conversationModels = conversationService.getAll(true)
        conversationModels.forEach {
            numDeletedMessages += deleteMessagesThatExceededGraceTime(it, graceDays)
        }

        if (numDeletedMessages > 0) {
            ListenerManager.conversationListeners.handle { listener -> listener.onModifiedAll() }
        }
        logger.info("Auto delete finished. Number of cleared messages =  {}", numDeletedMessages)

        return Result.success()
    }

    private fun deleteMessagesThatExceededGraceTime(conversationModel: ConversationModel, graceDays: Int): Int {
        var numDeletedMessages = 0
        val today = Date()

        // do not delete messages in note groups
        if (conversationModel.isGroupConversation) {
            val groupModel = conversationModel.group
            if (groupModel == null || groupService.isNotesGroup(groupModel)) {
                return 0
            }
        }

        val messageModels = messageService.getMessagesForReceiver(conversationModel.receiver, null)

        for (messageModel in messageModels) {
            if (messageModel.isOutbox) {
                // do not delete messages in outbox that are not yet queued or sent
                if (!messageModel.isQueued) {
                    continue
                }

                val messageState = messageModel.state;
                if (messageState == MessageState.SENDING || messageState == MessageState.PENDING) {
                    continue
                }

                // exclude starred messages
                if (messageModel.displayTags and DisplayTag.DISPLAY_TAG_STARRED == DisplayTag.DISPLAY_TAG_STARRED) {
                    continue
                }
            }

            // we use a locally produced reference date by default to prevent problems with phones that have an incorrect date set
            var createdDate = messageModel.createdAt
            if (createdDate == null) {
                createdDate = messageModel.postedAt
            }
            if (createdDate != null && AutoDeleteUtil.getDifferenceDays(createdDate, today) >= graceDays) {
                if (messageModel.type == MessageType.BALLOT) {
                    val ballotModel = ballotService.get(messageModel.ballotData.ballotId)
                    if (messageModel.ballotData.type == BallotDataModel.Type.BALLOT_CLOSED) {
                        logger.info("Removing ballot message {}", messageModel.apiMessageId ?: messageModel.id)
                        ballotService.remove(ballotModel)
                        numDeletedMessages++
                    } else {
                        logger.info("Skipping ballot message {} of type {}.", messageModel.apiMessageId ?: messageModel.id, messageModel.ballotData.type)
                    }
                } else {
                    logger.info("Removing message {}", messageModel.apiMessageId ?: messageModel.id)
                    fileService.removeMessageFiles(messageModel, true);
                    messageService.remove(messageModel, false)
                    numDeletedMessages++
                }
            }
        }
        return numDeletedMessages
    }
}
