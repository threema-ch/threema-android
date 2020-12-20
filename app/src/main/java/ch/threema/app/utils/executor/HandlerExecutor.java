/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2020 Threema GmbH
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

package ch.threema.app.utils.executor;

import android.os.Handler;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java8.util.concurrent.CompletableFuture;

/**
 * An executor handler is a simple wrapper around a handler that is to be used as a
 * {@link SingleThreadExecutor}. It therefore has a reduced set of methods to prevent reordering
 * of runnables.
 */
@AnyThread
public class HandlerExecutor {
	@NonNull final private Handler parent;
	@Nullable private SingleThreadExecutor executor;

	public HandlerExecutor(@NonNull final Handler parent) {
		this.parent = parent;
	}

	/**
	 * Return an executor that schedules execution on the underlying handler
	 * thread.
	 */
	@NonNull public SingleThreadExecutor getExecutor() {
		if (this.executor == null) {
			this.executor = new SingleThreadExecutor() {
				@Override
				@AnyThread
				public void execute(Runnable runnable) {
					HandlerExecutor.this.post(runnable);
				}
			};
		}
		return this.executor;
	}

	/**
	 * Same as {@link Handler#post(java.lang.Runnable)}.
	 */
	public boolean post(@NonNull final Runnable runnable) {
		return this.parent.post(runnable);
	}

	/**
	 * Like {@link #post(Runnable)}, but return a CompletableFuture that resolves
	 * once the runnable has finished running and that is cancelled if the runnable
	 * could not be scheduled.
	 */
	public CompletableFuture<Void> postFuture(@NonNull final Runnable runnable) {
		final CompletableFuture<Void> future = new CompletableFuture<>();
		final boolean posted = this.post(() -> {
			try {
				runnable.run();
			} catch (Exception e) {
				future.completeExceptionally(e);
			}
			future.complete(null);
		});
		if (!posted) {
			future.cancel(false);
		}
		return future;
	}

	/**
	 * Same as {@link Handler#postDelayed(Runnable, long)}.
	 */
	public boolean postDelayed(@NonNull final Runnable runnable, final long delayMs) {
		return this.parent.postDelayed(runnable, delayMs);
	}
}
