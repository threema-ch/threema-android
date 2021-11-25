/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

package ch.threema.base.utils;

import java.net.InetAddress;
import java.util.concurrent.*;

public class AsyncResolver implements Callable<InetAddress[]> {
	private final String host;

	public AsyncResolver(String host) {
		this.host = host;
	}

	@Override
	public InetAddress[] call() throws Exception {
		return InetAddress.getAllByName(host);
	}

	public static InetAddress[] getAllByName(String host) throws ExecutionException, InterruptedException {
		AsyncResolver resolver = new AsyncResolver(host);
		ExecutorService executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r);
				thread.setDaemon(true);
				return thread;
			}
		});
		Future<InetAddress[]> future = executorService.submit(resolver);
		return future.get();
	}
}
