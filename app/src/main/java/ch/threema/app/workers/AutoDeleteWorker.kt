package ch.threema.app.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import ch.threema.android.buildPeriodicWorkRequest
import ch.threema.android.setBackoffCriteria
import ch.threema.android.setInitialDelay
import ch.threema.android.setInputData
import ch.threema.app.di.awaitAppFullyReadyWithTimeout
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ConversationService
import ch.threema.app.services.FileService
import ch.threema.app.services.MessageService
import ch.threema.app.services.ballot.BallotService
import ch.threema.app.utils.AutoDeleteUtil
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.DisplayTag
import ch.threema.storage.models.data.media.BallotDataModel
import java.util.Date
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("AutoDeleteWorker")

class AutoDeleteWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val conversationService: ConversationService by inject()
    private val messageService: MessageService by inject()
    private val fileService: FileService by inject()
    private val ballotService: BallotService by inject()

    override suspend fun doWork(): Result {
        logger.info("Start auto delete work")
        awaitAppFullyReadyWithTimeout(20.seconds)
            ?: return Result.retry()

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
            if (createdDate != null &&
                AutoDeleteUtil.getDifferenceDays(
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

    class Scheduler(
        private val workManager: WorkManager,
        private val preferenceService: PreferenceService,
        private val appRestrictions: AppRestrictions,
        private val dispatcherProvider: DispatcherProvider,
    ) {
        /**
         * Schedule the auto delete worker to run periodically. If auto delete is not configured
         * and a worker is already scheduled, it will be cancelled.
         */
        fun scheduleAutoDelete() {
            val graceDays = getGraceDays()
            if (graceDays > ProtocolDefines.AUTO_DELETE_KEEP_MESSAGES_DAYS_OFF_VALUE) {
                logger.info("Scheduling auto delete")
                CoroutineScope(dispatcherProvider.io).launch {
                    try {
                        val operation = workManager.enqueueUniquePeriodicWork(
                            WorkerNames.WORKER_AUTO_DELETE,
                            ExistingPeriodicWorkPolicy.UPDATE,
                            buildWorkRequest(graceDays),
                        )
                        logger.info("Schedule result = {}", operation.result.get())
                    } catch (e: IllegalStateException) {
                        logger.error("Exception scheduling auto delete", e)
                    }
                }
            } else {
                logger.info("No auto delete configured")
                cancelAutoDelete()
            }
        }

        private fun buildWorkRequest(graceDays: Int): PeriodicWorkRequest {
            logger.info("Building Periodic Work Request with graceDays = {}", graceDays)
            return buildPeriodicWorkRequest<AutoDeleteWorker>(schedulePeriod) {
                setInitialDelay(5.minutes)
                setBackoffCriteria(BackoffPolicy.LINEAR, 1.hours)
                setInputData {
                    putInt(EXTRA_GRACE_DAYS, graceDays)
                }
            }
        }

        private fun getGraceDays(): Int =
            appRestrictions.getKeepMessagesDays()
                ?: preferenceService.getAutoDeleteDays()

        fun cancelAutoDelete() {
            CoroutineScope(dispatcherProvider.io).launch {
                cancelAutoDeleteAwait()
            }
        }

        suspend fun cancelAutoDeleteAwait() = withContext(dispatcherProvider.io) {
            workManager.cancelUniqueWork(WorkerNames.WORKER_AUTO_DELETE).await()
        }
    }

    companion object {
        private const val EXTRA_GRACE_DAYS = "grace_days"
        private val schedulePeriod = 0.5.days
    }
}
