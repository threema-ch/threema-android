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

package ch.threema.domain.identitybackup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class IdentityBackupTest {
    @Test
    fun `generateBackup must generate v1 id export`() {
        val backup = IdentityBackup.encryptIdentityBackup(
            password = IDENTITY_BACKUP_PASSWORD,
            backupData = IdentityBackup.PlainBackupData(IDENTITY, PRIVATE_KEY),
        )

        // Expected length for id export V1 is 99 (20 groups of four characters separated by dash)
        assertEquals(99, backup.data.length)

        // Since a random salt is used in id export creation, we cannot compare the export with an
        // expected output.
        // Therefore, we check whether decoding the backup yields the identity and private key used to
        // generate the backup.
        val backupData = IdentityBackup.decryptIdentityBackup(
            password = IDENTITY_BACKUP_PASSWORD,
            encryptedBackup = backup,
        )
        assertEquals(IDENTITY, backupData.threemaId)
        assertContentEquals(PRIVATE_KEY, backupData.clientKey)
    }

    @Test
    fun `decode v1 id export`() {
        val backupData = IdentityBackup.decryptIdentityBackup(
            password = IDENTITY_BACKUP_PASSWORD,
            encryptedBackup = IdentityBackup.EncryptedIdentityBackup(IDENTITY_BACKUP_V1),
        )
        assertEquals(IDENTITY, backupData.threemaId)
        assertContentEquals(PRIVATE_KEY, backupData.clientKey)
    }

    @Test
    fun `decode v2 id export`() {
        val backupData = IdentityBackup.decryptIdentityBackup(
            password = IDENTITY_BACKUP_PASSWORD,
            encryptedBackup = IdentityBackup.EncryptedIdentityBackup(IDENTITY_BACKUP_V2),
        )
        assertEquals(IDENTITY, backupData.threemaId)
        assertContentEquals(PRIVATE_KEY, backupData.clientKey)
    }

    private companion object {
        const val IDENTITY_BACKUP_V1 = "MXC5-DNNQ-TD67-FLG3-ERIL-XZTX-EZH7-XD4B-X2IF-HJZN-744J-BPZS-S7U7-WNKL-IVN2-J7RX-STEU-X6ZX-VYH5-IHMQ"
        const val IDENTITY_BACKUP_V2 =
            "AEMW-DRSQ-XRIX-N2HH-J5KC-F7OC-VZQD-BXRI-QCR4-ZUHC-DSQM-3QMN-7M4B-WIEW-MI3V-LODW-XRWS-ZJ3M-DAW2-JJDP-TPDQ-JAXN-SVB3-AKQP-46PL-T2ZL"
        const val IDENTITY_BACKUP_PASSWORD = "superS3cr3tpaZZword"

        const val IDENTITY = "ABCD1234"
        val PRIVATE_KEY = byteArrayOf(
            -52, -52, -52, -52, -52, -52, -52, -52,
            -52, -52, -52, -52, -52, -52, -52, -52,
            -52, -52, -52, -52, -52, -52, -52, -52,
            -52, -52, -52, -52, -52, -52, -52, -52,
        )
    }
}
