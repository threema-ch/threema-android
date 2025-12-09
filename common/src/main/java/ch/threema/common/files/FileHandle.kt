/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.common.files

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * An abstraction around a file in the file system, for the purpose of allowing to extend or alter the way files are accessed.
 * The call-site should remain agnostic of where the actual file is located and how it is stored
 * (e.g. whether it is encrypted, backed by a single file, or is virtual).
 */
interface FileHandle {
    /**
     * Returns true if the file exists (regardless of whether it is empty), false otherwise.
     *
     * @throws SecurityException if read access to the file is not granted
     */
    fun exists(): Boolean

    /**
     * Returns true if the file exists and is empty (i.e., 0 bytes), false otherwise.
     *
     * @throws SecurityException if read access to the file is not granted
     */
    fun isEmpty(): Boolean

    /**
     * Returns an [java.io.InputStream] to read from the file, or `null` if the file does not exist or can otherwise not be read.
     *
     * @throws SecurityException if read access to the file is not granted
     */
    @Throws(IOException::class)
    fun read(): InputStream?

    /**
     * Creates the file if it does not already exist. The resulting file will be empty, or retain its original content if it existed before.
     *
     * @throws SecurityException if write access to the file is not granted
     */
    @Throws(IOException::class)
    fun create()

    /**
     * Returns an [java.io.OutputStream] to write to the file. The file will be created if it does not already exist. If it already exists, its contents
     * will be replaced.
     *
     * @throws SecurityException if write access to the file is not granted
     */
    @Throws(IOException::class)
    fun write(): OutputStream

    /**
     * Deletes the file if it exists. Does nothing if the file does not exist.
     *
     * @throws SecurityException if write access to the file is not granted
     */
    @Throws(IOException::class)
    fun delete()
}
