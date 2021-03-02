/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
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

package ch.threema.client;

import java.net.InetAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		Future<InetAddress[]> future = executorService.submit(resolver);
		return future.get();
	}
}
