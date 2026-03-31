package ch.threema.app.startup

/**
 * By the time an activity's onCreate is called, the app may not yet be in a ready state. Most activities handle this by relying on
 * [ch.threema.app.activities.ThreemaToolbarActivity] checking the ready state and closing and later restarting the activity if the app is not
 * yet ready. This is acceptable in most cases but causes problems under certain circumstances. See [finishAndRestartLaterIfNotReady] for details.
 * In such cases, activities may mark themselves with this interface, which allows them to use different ready state logic, such as that
 * provided by [waitUntilReady].
 */
interface AppStartupAware
