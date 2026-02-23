package ch.threema.app.webclient;

/**
 * Send mode to be used by the web client.
 */
public enum SendMode {
    /**
     * Will either complete the send operation immediately or queue it to be
     * sent.
     */
    ASYNC,

    /**
     * Will block the thread until the sending operation has been completed.
     * <p>
     * Important: This bypasses the message queue and is therefore dangerous!
     * Only use this to dispatch messages when you know that the
     * queue is empty or when you don't care (e.g. for *last will*
     * messages)
     */
    UNSAFE_SYNC,
}
