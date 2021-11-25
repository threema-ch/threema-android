package ch.threema.taskmanager

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.trySendBlocking

sealed class RunnerMessage {
    /**
     * Message that is triggered when new tasks are added to the queue
     */
    object NewTaskAvailable : RunnerMessage()

    /**
     * Message that is triggered when a task runner should start processing tasks.
     */
    object Initialize : RunnerMessage()

    /**
     * Message that is triggered when the actor should process the queued tasks, quit and complete
     * the deferrable.
     */
    data class Close(val messageProcessed: CompletableDeferred<Unit> = CompletableDeferred()) : RunnerMessage()
}

/**
 * Make sure only one task is running at any given time,
 */
@ObsoleteCoroutinesApi // Currently required as actors might change in a later coroutines release.
class TaskRunner(
    private val taskQueue: TaskQueue,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val exceptionHandler: CoroutineExceptionHandler = CoroutineExceptionHandler {
            _, throwable -> println("A TaskRunner Task failed with exception: $throwable")
    }
) {

    private val actor = scope.actor<RunnerMessage>(
        capacity = Channel.UNLIMITED,
        context = SupervisorJob()
    ) {
        for (message in channel) when (message) {
            is RunnerMessage.Initialize,
            is RunnerMessage.NewTaskAvailable -> {
                do {
                    val task = taskQueue.removeFirstOrNull()
                    launch(exceptionHandler) {
                        task?.invoke(this)
                    }.join()
                } while (task != null)
            }
            is RunnerMessage.Close -> {
                message.messageProcessed.complete(Unit)
                return@actor
            }
        }
    }

    init {
        taskQueue.newTaskObservers.add {
            actor.send(RunnerMessage.NewTaskAvailable)
        }

        actor.trySendBlocking(RunnerMessage.Initialize)
    }

    /**
     * Ordered shutdown of a task runner.
     *
     * All tasks registered *before* this method call are processed as expected.
     */
    suspend fun close() {
        val closeMessage = RunnerMessage.Close()
        actor.send(closeMessage)
        actor.close()
        closeMessage.messageProcessed.await()
    }
}
