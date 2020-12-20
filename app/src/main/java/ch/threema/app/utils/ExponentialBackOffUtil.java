/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ExponentialBackOffUtil {
	private static final Logger logger = LoggerFactory.getLogger(ExponentialBackOffUtil.class);
	protected final static ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
	private Random random;

	// Singleton stuff
	private static ExponentialBackOffUtil sInstance = null;

	public static synchronized ExponentialBackOffUtil getInstance() {
		if (sInstance == null) {
			sInstance = new ExponentialBackOffUtil();
		}
		return sInstance;
	}

	public ExponentialBackOffUtil() {
		this.random = new Random();
	}

	/**
	 * Run a Runnable in a ExponentialBackoff
	 * @param runnable Method
	 * @param exponentialBackOffCount Count of Retries
	 * @return Future
	 */
	public Future run(final BackOffRunnable runnable, final int exponentialBackOffCount) {
		return singleThreadExecutor.submit(new Runnable() {
			@Override
			public void run() {
				for (int n = 0; n < exponentialBackOffCount; ++n) {
					logger.debug("run " + String.valueOf(n));
					try {
						runnable.run(n);

						//its ok, do not retry
						return;
					} catch (Exception e) {
						logger.error("Exception", e);
						if(n >= exponentialBackOffCount-1) {
							//last
							runnable.exception(e, n);
						}
						else {
							try {
								Thread.sleep((2 << n) * 1000 + random.nextInt(1001));
							} catch (InterruptedException e1) {
								//do nothing
							}
						}
					}
				}
			}
		});
	}

	public interface BackOffRunnable {
		void run(int currentRetry) throws Exception;
		void finished(int currentRetry);
		void exception(Exception e, int currentRetry);
	}
}
