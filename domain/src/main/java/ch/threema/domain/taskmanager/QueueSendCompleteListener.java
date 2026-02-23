package ch.threema.domain.taskmanager;

/**
 * Interface for objects that wish to be notified when the server has signalled that the message
 * queue has been flushed completely.
 *
 * Note: This implies that the D2M ReflectionQueueDry has also been processed because the CSP
 * queue will not be opened until the reflection process is complete.
 */
public interface QueueSendCompleteListener {
    void queueSendComplete();
}
