/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.multidevice

import ch.threema.domain.protocol.D2mProtocolDefines
import ch.threema.domain.protocol.connection.data.DeviceId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

internal data class PersistedMultiDeviceProperties(
    val registrationTime: ULong?,
    val deviceLabel: String,
    val mediatorDeviceId: DeviceId,
    val cspDeviceId: DeviceId,
    val dgk: ByteArray

) {

    class DeserializeException(msg: String) : Error(msg)
    companion object {

        fun deserialize(byteArray: ByteArray): PersistedMultiDeviceProperties {
            val minimalPropertiesSize = (
                Long.SIZE_BYTES // registration time
                    + Int.SIZE_BYTES  // device label size
                    + Long.SIZE_BYTES // mediator device id
                    + Long.SIZE_BYTES // csp device id
                    + D2mProtocolDefines.DGK_LENGTH_BYTES // dgk
                )

            if (byteArray.size < minimalPropertiesSize) {
                throw DeserializeException("Invalid data size. expected >= $minimalPropertiesSize, actual=${byteArray.size}")
            }

            return DataInputStream(ByteArrayInputStream(byteArray)).use {
                // 1. registration time (8 bytes)
                val time = it.readLong().toULong()
                val registrationTime = if (time > 0UL) {
                    time
                } else {
                    null
                }
                // 2. device label (variable bytes)
                val deviceLabelSize = it.readInt()
                val expectedLabelSize = byteArray.size - minimalPropertiesSize
                if (deviceLabelSize != expectedLabelSize) {
                    throw DeserializeException("Invalid data: expectedLabelSize=$expectedLabelSize, provided=$deviceLabelSize")
                }
                val deviceLabelBytes = ByteArray(deviceLabelSize)
                it.read(deviceLabelBytes)
                val deviceLabel = String(deviceLabelBytes, StandardCharsets.UTF_8)
                // 3. mediator device id (8bytes)
                val mediatorDeviceId = DeviceId(it.readLong().toULong())
                // 4. csp device id (8 bytes)
                val cspDeviceId = DeviceId(it.readLong().toULong())
                // 5. dgk
                val dgk = ByteArray(D2mProtocolDefines.DGK_LENGTH_BYTES)
                it.read(dgk)
                PersistedMultiDeviceProperties(
                    registrationTime,
                    deviceLabel,
                    mediatorDeviceId,
                    cspDeviceId,
                    dgk
                )
            }
        }
    }

    fun serialize(): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use {
            // 1. registration time
            it.writeLong(registrationTime?.toLong() ?: 0L)
            // 2. device label
            val deviceLabelBytes = deviceLabel.encodeToByteArray()
            it.writeInt(deviceLabelBytes.size)
            it.write(deviceLabelBytes)
            // 3. mediator device id
            it.writeLong(mediatorDeviceId.id.toLong())
            // 4. csp device id
            it.writeLong(cspDeviceId.id.toLong())
            // 5. dgk
            it.write(dgk)
        }
        return bos.toByteArray()

    }

    fun withRegistrationTime(registrationTime: ULong?): PersistedMultiDeviceProperties {
        return PersistedMultiDeviceProperties(
            registrationTime,
            deviceLabel,
            mediatorDeviceId,
            cspDeviceId,
            dgk
        )
    }

    fun withDeviceLabel(deviceLabel: String): PersistedMultiDeviceProperties {
        return PersistedMultiDeviceProperties(
            registrationTime,
            deviceLabel,
            mediatorDeviceId,
            cspDeviceId,
            dgk
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PersistedMultiDeviceProperties) return false

        if (!dgk.contentEquals(other.dgk)) return false
        if (mediatorDeviceId != other.mediatorDeviceId) return false
        if (cspDeviceId != other.cspDeviceId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dgk.contentHashCode()
        result = 31 * result + mediatorDeviceId.hashCode()
        result = 31 * result + cspDeviceId.hashCode()
        return result
    }

    override fun toString(): String {
        return "PersistedMultiDeviceProperties(registrationTime=$registrationTime, deviceLabel='$deviceLabel', mediatorDeviceId=$mediatorDeviceId, cspDeviceId=$cspDeviceId, dgk=********)"
    }
}
