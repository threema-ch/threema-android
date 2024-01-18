/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.voip.groupcall

import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.sfu.CallId
import ch.threema.app.voip.groupcall.sfu.GroupCallState
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.storage.models.GroupCallModel
import com.neilalexander.jnacl.NaCl
import java.util.*
import kotlin.math.max

private val logger = LoggingUtil.getThreemaLogger("GroupCallDescription")

@AnyThread
data class GroupCallDescription(
    val protocolVersion: UInt,
    val groupId: LocalGroupId,
    val sfuBaseUrl: String,
    val callId: CallId,
    val gck: ByteArray,
    var startedAt: ULong,
    val processedAt: ULong = startedAt,
    var maxParticipants: UInt? = null
) {
    private val gchk: ByteArray by lazy { gcBlake2b(NaCl.SYMMKEYBYTES, gck, SALT_GCHK) }
    private val gcsk: ByteArray by lazy { gcBlake2b(NaCl.SYMMKEYBYTES, gck, SALT_GCSK)}

    val gckh: ByteArray by lazy { gcBlake2b(NaCl.SYMMKEYBYTES, gck, SALT_GCKH) }

    var callState: GroupCallState? = null

    @WorkerThread
    fun setEncryptedCallState(encryptedCallState: ByteArray?) {
        callState = encryptedCallState?.let {
            if (it.isNotEmpty()) {
                val decryptedBytes = decryptByGcsk(it)
                if (decryptedBytes == null) {
                    logger.warn("Could not decrypt call state")
                    null
                } else {
                    GroupCallState.fromProtobufBytes(decryptedBytes)
                }
            } else {
                null
            }
        }
    }

    @AnyThread
    fun getStartedAtDate(): Date = Date(startedAt.toLong())

    /**
     * Get the running time of this call in milliseconds.
     * The running time is relative to [SystemClock.elapsedRealtime]
     *
     * If [startedAt] is dated in the future, null will be returned.
     *
     * @return The time in milliseconds since this call has been started
     */
    fun getRunningSince(): Long? = startedAt.let {
        val currentTime = Date().time
        val startedAtTime = it.toLong()
        if (currentTime >= startedAtTime) {
            val duration = currentTime - startedAtTime
            SystemClock.elapsedRealtime() - duration
        } else {
            null
        }
    }

    /**
     * Get the time of this call in milliseconds since the group message has been processed.
     * The time is relative to [SystemClock.elapsedRealtime].
     *
     * If [processedAt] is dated in the future, [SystemClock.elapsedRealtime] will be returned.
     *
     * If the device's time is potentially wrong, this method can be used instead of
     * [getRunningSince] as it is relative to this device's time.
     *
     * @return The time in milliseconds since this call has been processed
     */
    fun getRunningSinceProcessed(): Long {
        val duration = max(Date().time - processedAt.toLong(), 0)
        return SystemClock.elapsedRealtime() - duration
    }

    @AnyThread
    fun getGroupIdInt(): Int = groupId.id

    @AnyThread
    fun toGroupCallModel(): GroupCallModel {
        return GroupCallModel(
            protocolVersion.toInt(),
            Utils.byteArrayToHexString(callId.bytes),
            groupId.id,
            sfuBaseUrl,
            Utils.byteArrayToHexString(gck),
            startedAt.toLong(),
            processedAt.toLong()
        )
    }

    /**
     * Encrypts data symmetrically by using GCHK as key and random nonce.
     * The nonce will be prefixed to the encrypted data.
     */
    @WorkerThread
    fun encryptWithGchk(data: ByteArray): ByteArray {
        return encrypt(data, gchk)
    }

    /**
     * Decrypts data which is symmetrically encrypted by GCHK and has a prefixed nonce.
     */
    @WorkerThread
    fun decryptByGchk(data: ByteArray): ByteArray? {
        return decrypt(data, gchk)
    }

    /**
     * Encrypts data symmetrically by using GCSK as key and random nonce.
     * The nonce will be prefixed to the encrypted data.
     */
    @WorkerThread
    fun encryptWithGcsk(data: ByteArray): ByteArray {
        return encrypt(data, gcsk)
    }

    /**
     * Decrypts data which is symmetrically encrypted by GCSK and has a prefixed nonce.
     */
    @WorkerThread
    private fun decryptByGcsk(data: ByteArray): ByteArray? {
        return decrypt(data, gcsk)
    }

    @WorkerThread
    private fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        val nonce = getSecureRandomBytes(NaCl.NONCEBYTES)
        val encrypted = NaCl.symmetricEncryptData(data, key, nonce)
        return nonce + encrypted
    }

    @WorkerThread
    private fun decrypt(encryptedData: ByteArray, key: ByteArray): ByteArray? {
        val nonce = encryptedData.copyOfRange(0, NaCl.NONCEBYTES)
        val data = encryptedData.copyOfRange(NaCl.NONCEBYTES, encryptedData.size)
        return NaCl.symmetricDecryptData(data, key, nonce)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupCallDescription) return false

        if (protocolVersion != other.protocolVersion) return false
        if (groupId != other.groupId) return false
        if (sfuBaseUrl != other.sfuBaseUrl) return false
        if (callId != other.callId) return false
        if (!gck.contentEquals(other.gck)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protocolVersion.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + sfuBaseUrl.hashCode()
        result = 31 * result + callId.hashCode()
        result = 31 * result + gck.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "GroupCallDescription(protocolVersion=$protocolVersion, groupId=$groupId, sfuBaseUrl='$sfuBaseUrl', startedAt=$startedAt, maxParticipants=$maxParticipants, callId=$callId, callState=$callState)"
    }
}

fun GroupCallModel.toGroupCallDescription(): GroupCallDescription {
    return GroupCallDescription(
        getProtocolVersionUnsigned(),
        LocalGroupId(groupId),
        sfuBaseUrl,
        CallId(Utils.hexStringToByteArray(callId)),
        Utils.hexStringToByteArray(gck),
        getStartedAtUnsigned(),
        getProcessedAtUnsigned(),
        null
    )
}
