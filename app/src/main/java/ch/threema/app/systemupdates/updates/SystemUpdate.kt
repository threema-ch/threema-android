package ch.threema.app.systemupdates.updates

/**
 * A [SystemUpdate] is used to migrate non-database data such as files or shared preferences,
 * or to perform one-off maintenance tasks such as scheduling a sync.
 * All [SystemUpdate]s are processed sequentially on a worker thread in the order of their version number,
 * once the database is migrated and all services are available.
 *
 * Important rules for every [SystemUpdate]:
 * - should never access the database directly, but should do so through other services or model factories
 * - should use constructor dependency injection instead of accessing global singletons from [run]
 * - must be idempotent, as the update might be re-run (partially) if it failed
 *
 * Note that some system updates before version 109 might not stick to these rules.
 */
@JvmDefaultWithCompatibility
interface SystemUpdate {
    /**
     * Runs the update. If any kind of exception is thrown, subsequent updates won't be run and the app is stopped.
     * The failed update will be re-run the next time the app is started.
     */
    fun run()

    fun getVersion(): Int

    /**
     * A brief, human-readable description of what this update does. Only used for logging.
     */
    fun getDescription(): String? = null
}
