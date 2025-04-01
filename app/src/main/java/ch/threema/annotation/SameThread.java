/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Denotes that the annotated method or class can only be safely called from a
 * single thread (e.g. it is NOT "thread safe").
 * <p>
 * If it is a class, all of its method must be called from the same thread,
 * except where other thread annotations have been used. If you require
 * calling from multiple threads, synchronise on the instance object.
 * <p>
 * If it is a method, the above description applies to the method.
 * <p>
 * Note: This annotation is not checked by the IDE! It is meant as a helper
 * to annotate classes consistently in addition to UiThread and co.
 */
@Documented
@Retention(CLASS)
@Target({METHOD, CONSTRUCTOR, TYPE, PARAMETER})
public @interface SameThread {
}
