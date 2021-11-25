/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021 Threema GmbH
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

package ch.threema.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java8.util.Optional;

/**
 * Represents a success or failed return value.
 *
 * May only be created with one of the static helper functions.
 *
 * Inspired by to the Kotlin equivalent from {@link kotlin.Result}
 *
 * @param <T> Type of the success value
 * @param <E> Type of the error value
 */
public class Result<T, E extends Throwable> {

	private final @Nullable T value;
	private final @Nullable E error;

	/**
	 * Simplified constructor for success value.
	 * @param value Success value (might by null)
	 */
	private Result(@Nullable T value) {
		this(value, null);
	}

	/**
	 *
	 *
	 * @param value success value (might by null)
	 * @param error Throwable error (or null, if successful)
	 */
	private Result(@Nullable T value, @Nullable  E error) {
		this.value = value;
		this.error = error;
	}

	public @Nullable E getError() {
		return this.error;
	}

	public @Nullable T getValue() {
		return this.value;
	}

	public T getOrThrow() throws E {
		if(this.error != null) {
			throw this.error;
		}

		return this.value;
	}

	public boolean isSuccess() {
		return this.error == null;
	}

	public boolean isFailure() {
		return !this.isSuccess();
	}

	public static @NonNull <U, R extends Throwable> Result<U, R> ofNullable(@Nullable U nullableValue) {
		return new Result<>(nullableValue);
	}

	public static @NonNull <U, R extends Throwable> Result<U, R> ofOptional( @NonNull Optional<U> optional, @NonNull R error) {
		return new Result<>(optional.orElse(null), optional.isPresent() ? null : error);
	}

	public static @NonNull <U, R extends Throwable> Result<U, R> success(@Nullable U value) {
		return new Result<>(value);
	}

	public static @NonNull <U, R extends Throwable> Result<U, R> failure(@NonNull R error) {
		return new Result<>(null, error);
	}
}
