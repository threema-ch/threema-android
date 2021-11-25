package ch.threema.taskmanager.task

/**
 * Parameters that are required for a tasks execution and continuation
 */
interface PersistableTaskState

/**
 * Execution state of a (currently active section of a) Task.
 *
 * Later tasks should be greater than (>) than former.
 */
interface TaskLifecycle<T> : Comparable<T>

/**
 * Handler of task state persisting and loading.
 */
interface TaskPersistenceHandler<S : PersistableTaskState, L : TaskLifecycle<L>> {
    /**
     * Persist a tasks state and value state
     */
    suspend fun persistTaskState(lifecycleState: L, taskState: S)

    /**
     * Get the last persisted task state of this task.
     */
    suspend fun getPersistedTaskState(): S?

    /**
     * Get the last persisted lifecycle state of this task.
     */
    suspend fun getPersistedLifecycleState(): L?
}

/**
 * Denotes a Task of which the current execution state is persisted on cancellation and may be
 * resumed at a later time.
 */
interface PersistableTask<R, S, L> :
    TaskPersistenceHandler<S, L>, Task<R>
        where S : PersistableTaskState,
              L : TaskLifecycle<L> {
    /**
     * Get the currently (active) state of this task.
     */
    suspend fun getCurrentTaskState(): S
}

/**
 * A section denotes a persistable subunit of a task.
 *
 * Sections must be idempotent (i.e., if the execution of a task is forcefully cancelled, the
 * currently active section is repeated.) and multiple sections of the same task may **not** run
 * concurrently or nested.
 */
suspend fun <R, S, L> PersistableTask<R, S, L>.persistableSection(
    sectionLifecycleState: L,
    block: suspend () -> Unit
) where S : PersistableTaskState,
        L : TaskLifecycle<L> {
    val lifecycleState = getPersistedLifecycleState()
    if (lifecycleState == null || sectionLifecycleState >= lifecycleState) {
        this.persistTaskState(sectionLifecycleState, this.getCurrentTaskState())
        block.invoke()
    }
}
