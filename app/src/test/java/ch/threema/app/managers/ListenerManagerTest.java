package ch.threema.app.managers;

import org.junit.Assert;
import org.junit.Test;

public class ListenerManagerTest {

    interface TestListener {
        void call();
    }

    /**
     * Make sure that the handle method cannot cause a deadlock.
     */
    @Test
    public void handleDeadlock() {
        // Create a test listener manager
        final ListenerManager.TypedListenerManager<TestListener> testListeners = new ListenerManager.TypedListenerManager<>();

        // Add a listener that modifies the list of listeners
        testListeners.add(() -> {
            // Add another listener from another thread
            final Thread thread = new Thread(() -> testListeners.add(() -> {
                // No-op
            }));

            // Start thread
            thread.start();

            // Check whether thread has finished
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (thread.isAlive()) {
                Assert.fail("Thread is still active: Deadlock detected!");
            }
            // Yeah, no deadlock!
        });

        // Handle
        testListeners.handle(TestListener::call);
    }

}
