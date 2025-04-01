/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.utils;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import androidx.annotation.NonNull;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class CloseableReadWriteLockTest {
    static class WriteLocker extends Thread {
        private final @NonNull CloseableReadWriteLock lock;
        private volatile boolean shutdown = false;
        private volatile boolean running = false;

        public WriteLocker(@NonNull CloseableReadWriteLock writeLock) {
            this.lock = writeLock;
        }

        @Override
        public void run() {
            try (CloseableLock writeLock = this.lock.write()) {
                running = true;
                while (!shutdown) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            running = false;
        }

        public void awaitRunning() {
            while (!this.running) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ignored) {
                }
            }
        }

        public void shutdown() {
            this.shutdown = true;
        }
    }

    @Test
    public void testLockingType() {
        final ReentrantReadWriteLock innerLock = new ReentrantReadWriteLock();
        final CloseableReadWriteLock lock = new CloseableReadWriteLock(innerLock);

        // Lock for reading
        try (CloseableLock readLock = lock.read()) {
            assertFalse(innerLock.isWriteLocked());
        }

        // Lock for writing
        try (CloseableLock writeLock = lock.write()) {
            assertTrue(innerLock.isWriteLocked());
            assertTrue(innerLock.isWriteLockedByCurrentThread());
        }
    }

    @Test
    public void testWriteLock() throws InterruptedException {
        final ReentrantReadWriteLock innerLock = new ReentrantReadWriteLock();
        final Lock readLock = innerLock.readLock();
        final Lock writeLock = innerLock.writeLock();

        final WriteLocker t = new WriteLocker(new CloseableReadWriteLock(innerLock));

        // Thread hasn't been started yet, locking should still work
        assertFalse(innerLock.isWriteLocked());
        writeLock.lock();
        assertTrue(innerLock.isWriteLocked());
        writeLock.unlock();
        assertFalse(innerLock.isWriteLocked());

        // Start thread, now the lock should be locked
        t.start();
        t.awaitRunning();
        assertTrue(innerLock.isWriteLocked());
        assertFalse(readLock.tryLock(50, TimeUnit.MILLISECONDS));
        assertFalse(writeLock.tryLock(50, TimeUnit.MILLISECONDS));

        // Stop thread, this should unlock the lock
        t.shutdown();
        assertTrue(readLock.tryLock(100, TimeUnit.MILLISECONDS));
        readLock.unlock();
        assertTrue(writeLock.tryLock(50, TimeUnit.MILLISECONDS));
        writeLock.unlock();
    }

    @Test
    public void testAutoUnlocking() {
        final ReentrantReadWriteLock innerLock = new ReentrantReadWriteLock();
        final CloseableReadWriteLock lock = new CloseableReadWriteLock(innerLock);

        try {
            try (CloseableLock writeLock = lock.write()) {
                assertTrue(innerLock.isWriteLocked());
                assertTrue(innerLock.isWriteLockedByCurrentThread());
                throw new RuntimeException("Oh no!!! Will it be unlocked?");
            }
        } catch (RuntimeException ignored) {
        }
        assertFalse(innerLock.isWriteLocked());
    }

    @Test
    public void testTry() throws InterruptedException {
        final ReentrantReadWriteLock innerLock = new ReentrantReadWriteLock();
        final CloseableReadWriteLock lock = new CloseableReadWriteLock(innerLock);

        // Lock lock
        final WriteLocker t = new WriteLocker(lock);
        t.start();
        t.awaitRunning();

        // Ensure it's locked
        assertTrue(innerLock.isWriteLocked());
        assertFalse(innerLock.isWriteLockedByCurrentThread());

        // Try locking
        try (CloseableLock locked = lock.tryWrite(100, TimeUnit.MILLISECONDS)) {
            fail("Lock should not have been locked");
        } catch (CloseableReadWriteLock.NotLocked notLocked) {
            // Yep
        }

        // Unlock
        t.shutdown();

        // Try locking
        try (CloseableLock locked = lock.tryWrite(100, TimeUnit.MILLISECONDS)) {
            // Yep
        } catch (CloseableReadWriteLock.NotLocked notLocked) {
            fail("Lock should have been locked");
        }
    }
}
