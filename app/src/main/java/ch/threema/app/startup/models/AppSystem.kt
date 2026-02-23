package ch.threema.app.startup.models

/**
 * All the different systems that the app might need to wait for before being considered 'ready'.
 * Note that the (partial) order matters here, as it is used to decide what to show to the user while waiting.
 * Systems which depend on other systems or are expected to become ready after those should be listed later.
 */
enum class AppSystem {
    REMOTE_SECRET,
    UNLOCKED_MASTER_KEY,
    DATABASE_UPDATES,
    SYSTEM_UPDATES,
}
