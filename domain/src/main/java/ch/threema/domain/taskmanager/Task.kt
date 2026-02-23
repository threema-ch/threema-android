package ch.threema.domain.taskmanager

/**
 * A Task that executes a specific application action according to a complex execution plan.
 */
interface Task<out R, in TaskCodecType> {
    /**
     * Run the task.
     */
    suspend fun invoke(handle: TaskCodecType): R

    /**
     * The type of the task. This is only used for logging purposes.
     */
    val type: String

    /**
     * Tasks may provide some short information that will be appended to the [type] when logging the task.
     */
    val shortLogInfo: String?
        get() = null
}

/**
 * An active task may send messages.
 */
interface ActiveTask<out R> : Task<R, ActiveTaskCodec>

/**
 * A passive task can only retrieve messages.
 */
interface PassiveTask<out R> : Task<R, PassiveTaskCodec>

/**
 * A task that will be dropped when the current primary connection (i.e. a connection to the Chat Server / Mediator Server) becomes closed. This task
 * will be immediately completed (exceptionally) when there is no connection at the time of being scheduled.
 */
interface DropOnDisconnectTask<out R> : Task<R, ActiveTaskCodec>
