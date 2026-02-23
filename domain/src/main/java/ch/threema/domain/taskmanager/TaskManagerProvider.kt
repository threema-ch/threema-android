package ch.threema.domain.taskmanager

import ch.threema.domain.protocol.connection.csp.DeviceCookieManager

data class TaskManagerConfiguration(
    val taskArchiver: () -> TaskArchiver,
    val deviceCookieManager: DeviceCookieManager,
    val assertContext: Boolean,
    val getDebugString: Task<*, *>.() -> String,
)

interface TaskManagerProvider {
    companion object {
        @JvmStatic
        fun getTaskManager(configuration: TaskManagerConfiguration): TaskManager =
            TaskManagerImpl(
                taskArchiverCreator = configuration.taskArchiver,
                deviceCookieManager = configuration.deviceCookieManager,
                dispatchers = TaskManagerImpl.TaskManagerDispatchers(
                    executorDispatcher = SingleThreadedTaskManagerDispatcher(
                        assertContext = configuration.assertContext,
                        threadName = "ExecutorDispatcher",
                    ),
                    scheduleDispatcher = SingleThreadedTaskManagerDispatcher(
                        assertContext = configuration.assertContext,
                        threadName = "ScheduleDispatcher",
                    ),
                ),
                getDebugString = configuration.getDebugString,
            )
    }
}
