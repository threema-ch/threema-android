/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.android

import androidx.core.util.AtomicFile
import ch.threema.annotation.SameThread
import ch.threema.common.NoCloseOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Writes a file atomically, i.e., it is either fully written or not at all.
 * If the file or its parent directory did not previously exist, it will be created.
 * If the file did previously exist, it will be replaced.
 *
 * The output stream passed into [performWrite] does not need to be closed.
 */
@SameThread
fun File.writeAtomically(performWrite: (OutputStream) -> Unit) {
    val atomicKeyFile = AtomicFile(this)
    val fos = atomicKeyFile.startWrite()
    try {
        // Note: stream *must not* be closed explicitly (see AtomicFile documentation)
        val dos = NoCloseOutputStream(DataOutputStream(fos))
        performWrite(dos)
        dos.flush()
    } catch (e: IOException) {
        atomicKeyFile.failWrite(fos)
        throw e
    }
    atomicKeyFile.finishWrite(fos)
}
