/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020 Threema GmbH
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import androidx.annotation.NonNull;

/**
 * Wrap a ReadWriteLock and return {@link AutoCloseable} instances when locking.
 *
 * This way, the wrapper can be used in a try-with-resources block to ensure unlocking.
 *
 * Example:
 *
 * <pre>
 *     CloseableReadWriteLock lock = new CloseableReadWriteLock(new ReentrantReadWriteLock());
 *
 *     try (CloseableLock writeLock = lock.write()) {
 *         System.out.println("Looked!");
 *     }
 * </pre>
 */
public class CloseableReadWriteLock {
	private final @NonNull ReadWriteLock lock;

	public static class NotLocked extends Exception { }

	/**
	 * Wrap a ReadWriteLock.
	 *
	 * @param lock The ReadWriteLock
	 */
	public CloseableReadWriteLock(@NonNull ReadWriteLock lock) {
		this.lock = lock;
	}

	/**
	 * Open this lock for reading.
	 */
	public CloseableLock read() throws IllegalStateException {
		final Lock readLock = this.lock.readLock();
		readLock.lock();
		return readLock::unlock;
	}

	/**
	 * Try opening this lock for reading.
	 */
	public CloseableLock tryRead(long time, TimeUnit unit) throws NotLocked {
		final Lock readLock = this.lock.readLock();
		try {
			boolean locked = readLock.tryLock(time, unit);
			if (!locked) {
				throw new NotLocked();
			}
			return readLock::unlock;
		} catch (InterruptedException e) {
			throw new NotLocked();
		}
	}

	/**
	 * Open this lock for writing.
	 */
	public CloseableLock write() {
		final Lock writeLock = this.lock.writeLock();
		writeLock.lock();
		return writeLock::unlock;
	}

	/**
	 * Try opening this lock for writing.
	 */
	public CloseableLock tryWrite(long time, TimeUnit unit) throws NotLocked {
		final Lock writeLock = this.lock.writeLock();
		try {
			boolean locked = writeLock.tryLock(time, unit);
			if (!locked) {
				throw new NotLocked();
			}
			return writeLock::unlock;
		} catch (InterruptedException e) {
			throw new NotLocked();
		}
	}
}
