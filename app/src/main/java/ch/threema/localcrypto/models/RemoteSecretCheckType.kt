package ch.threema.localcrypto.models

enum class RemoteSecretCheckType {
    /**
     * Indicates that the check is happening during the app's startup sequence, i.e., when the [ch.threema.app.home.HomeActivity] is opened
     * or the wizard is completed.
     */
    APP_STARTUP,

    /**
     * Indicates that the check is happening during the app's runtime, e.g., when MDM parameters have changed.
     */
    APP_RUNTIME,
}
