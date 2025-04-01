/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.managers;

import junit.framework.Assert;

import org.junit.Test;

public class ListenerManagerTest {

    interface TestListener {
        void call();
    }

    /**
     * Make sure that the handle method cannot cause a deadlock.
     */
    @Test
    public void handleDeadlock() throws InterruptedException {
        // Create a test listener manager
        final ListenerManager.TypedListenerManager<TestListener> testListeners = new ListenerManager.TypedListenerManager<>();

        // Add a listener that modifies the list of listeners
        testListeners.add(new TestListener() {
            @Override
            public void call() {
                // Add another listener from another thread
                final Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        testListeners.add(new TestListener() {
                            @Override
                            public void call() {
                                // No-op
                            }
                        });
                    }
                });

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
            }
        });

        // Handle
        testListeners.handle(new ListenerManager.HandleListener<TestListener>() {
            @Override
            public void handle(TestListener listener) {
                listener.call();
            }
        });
    }

}
