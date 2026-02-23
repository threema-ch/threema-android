package ch.threema.domain.taskmanager

enum class TriggerSource {
    /**
     * An update triggered by synchronisation from another device.
     *
     * This should never trigger further messages to other devices.
     */
    SYNC,

    /**
     * An update triggered locally, e.g. by a user interaction.
     *
     * This will always trigger messages to other devices.
     */
    LOCAL,

    /**
     * An update triggered remotely, e.g. by an incoming message.
     *
     * The task that was triggered by the remote message will take care of reflection, but further
     * side effects (e.g. implicit contact creation) will need to be reflected separately.
     */
    REMOTE,
}
