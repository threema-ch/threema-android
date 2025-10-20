/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.threema.app.ThreemaApplication
import ch.threema.app.di.awaitServiceManagerWithTimeout
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.services.ConversationService
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.services.ballot.BallotService
import ch.threema.app.utils.AutoDeleteUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.WorkManagerUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.DisplayTag
import ch.threema.storage.models.data.media.BallotDataModel
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent

private val logger = LoggingUtil.getThreemaLogger("AutoDeleteWorker")

class AutoDeleteWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {
    private lateinit var preferenceService: PreferenceService
    private lateinit var conversationService: ConversationService
    private lateinit var groupService: GroupService
    private lateinit var messageService: MessageService
    private lateinit var fileService: FileService
    private lateinit var ballotService: BallotService

    companion object {
        const val EXTRA_GRACE_DAYS = "grace_days"
        private val schedulePeriod = 0.5.days

        /**
         * Schedule the auto delete worker to run periodically. If auto delete is not configured
         * and a worker is already scheduled, it will be cancelled.
         */
        fun scheduleAutoDelete(context: Context) {
            val graceDays = getGraceDays(context)
            if (graceDays != null && graceDays > ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_OFF_VALUE) {
                logger.info("Scheduling auto delete")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val operation = WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            WorkerNames.WORKER_AUTO_DELETE,
                            ExistingPeriodicWorkPolicy.UPDATE,
                            buildPeriodicWorkRequest(graceDays),
                        )
                        logger.info(
                            "Schedule result = {}",
                            withContext(Dispatchers.IO) {
                                operation.result.get()
                            },
                        )
                    } catch (e: IllegalStateException) {
                        logger.error("Exception scheduling auto delete", e)
                    }
                }
            } else {
                logger.info("No auto delete configured")
                cancelAutoDelete(context)
            }
        }

        private fun buildPeriodicWorkRequest(graceDays: Int): PeriodicWorkRequest {
            logger.info("Building Periodic Work Request with graceDays = {}", graceDays)

            val data = Data.Builder()
                .putInt(EXTRA_GRACE_DAYS, graceDays)
                .build()

            return PeriodicWorkRequestBuilder<AutoDeleteWorker>(
                schedulePeriod.inWholeMilliseconds,
                TimeUnit.MILLISECONDS,
            )
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
                .setInputData(data)
                .build()
        }

        private fun getGraceDays(context: Context): Int? {
            var newGraceDays =
                if (ConfigUtils.isWorkRestricted()) AppRestrictionUtil.getKeepMessagesDays(context) else null

            if (newGraceDays == null) {
                val preferenceService = ThreemaApplication.getServiceManager()?.preferenceService
                if (preferenceService != null) {
                    newGraceDays = preferenceService.autoDeleteDays
                }
            }
            return newGraceDays
        }

        fun cancelAutoDelete(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                cancelAutoDeleteAwait(context)
            }
        }

        suspend fun cancelAutoDeleteAwait(context: Context) {
            WorkManagerUtil.cancelUniqueWorkAwait(context, WorkerNames.WORKER_AUTO_DELETE)
        }
    }

    override suspend fun doWork(): Result {
        logger.info("Start auto delete work")
        val serviceManager = awaitServiceManagerWithTimeout(20.seconds)
            ?: return Result.retry()

        this.preferenceService = serviceManager.preferenceService
        this.conversationService = serviceManager.conversationService
        this.groupService = serviceManager.groupService
        this.messageService = serviceManager.messageService
        this.fileService = serviceManager.fileService
        this.ballotService = serviceManager.ballotService

        val graceDays: Int = inputData.getInt(
            EXTRA_GRACE_DAYS,
            ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_OFF_VALUE,
        )
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

    private fun deleteMessagesThatExceededGraceTime(
        conversationModel: ConversationModel,
        graceDays: Int,
    ): Int {
        var numDeletedMessages = 0
        val today = Date()

        // do not delete messages in note groups
        if (conversationModel.isGroupConversation) {
            val groupModel = conversationModel.groupModel
            if (groupModel == null || groupModel.isNotesGroup() != false) {
                return 0
            }
        }

        val messageModels = messageService.getMessagesForReceiver(conversationModel.messageReceiver, null)

        for (messageModel in messageModels) {
            if (messageModel.isOutbox) {
                when (messageModel.state) {
                    // Do not delete messages that have not yet been sent
                    MessageState.SENDING, MessageState.PENDING, MessageState.UPLOADING -> continue
                    else -> Unit
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
            if (createdDate != null && AutoDeleteUtil.getDifferenceDays(
                    createdDate,
                    today,
                ) >= graceDays
            ) {
                if (messageModel.type == MessageType.BALLOT) {
                    val ballotModel = ballotService.get(messageModel.ballotData.ballotId)
                    if (messageModel.ballotData.type == BallotDataModel.Type.BALLOT_CLOSED) {
                        logger.info(
                            "Removing ballot message {}",
                            messageModel.apiMessageId ?: messageModel.id,
                        )
                        if (ballotModel != null) {
                            ballotService.remove(ballotModel)
                        } else {
                            // associated BallotModel has already been deleted - just remove the remaining message
                            messageService.remove(messageModel, false)
                        }
                        numDeletedMessages++
                    } else {
                        logger.info(
                            "Skipping ballot message {} of type {}.",
                            messageModel.apiMessageId ?: messageModel.id,
                            messageModel.ballotData.type,
                        )
                    }
                } else {
                    logger.info("Removing message {}", messageModel.apiMessageId ?: messageModel.id)
                    fileService.removeMessageFiles(messageModel, true)
                    messageService.remove(messageModel, false)
                    numDeletedMessages++
                }
            }
        }
        return numDeletedMessages
    }
}
