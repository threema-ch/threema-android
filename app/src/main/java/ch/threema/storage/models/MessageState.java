package ch.threema.storage.models;

/**
 * Internal message sending state.
 */
public enum MessageState {
    /**
     * Message was created, but not yet sent.
     */
    PENDING,

    /**
     * Media is being transcoded.
     */
    TRANSCODING,

    /**
     * Media is being uploaded. Note that this state is used between transcoding and sending. As
     * soon as a task to send the message is created and persisted, the state must be updated to
     * sending. Only use this state for file messages.
     */
    UPLOADING,

    /**
     * Message is being sent, but was not yet ACKed by the server.
     */
    SENDING,

    /**
     * Sending the message failed.
     */
    SENDFAILED,

    /**
     * Message was sent and ACKed by the server (but not yet delivered).
     */
    SENT,

    /**
     * Message was delivered to the recipient.
     */
    DELIVERED,

    /**
     * Message was read by the recipient.
     */
    READ,

    /**
     * Media mssage (e.g. audio message) was consumed by the recipient.
     */
    CONSUMED,

    /**
     * A "thumbs up" reaction was sent by the recipient.
     */
    @Deprecated
    USERACK,

    /**
     * A "thumbs down" reaction was sent by the recipient.
     */
    @Deprecated
    USERDEC,

    /**
     * The FS key has changed for this message.
     */
    FS_KEY_MISMATCH,
}
