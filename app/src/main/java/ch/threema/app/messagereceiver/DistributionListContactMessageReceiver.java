/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.messagereceiver;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.SymmetricEncryptionResult;
import ch.threema.storage.models.MessageModel;

public class DistributionListContactMessageReceiver extends ContactMessageReceiver {
	private SymmetricEncryptionResult fileEncryptionResult;
	private byte[] thumbnailBlobId;
	private byte[] fileBlobId;

	public DistributionListContactMessageReceiver(ContactMessageReceiver contactMessageReceiver) {
		super(contactMessageReceiver);
	}

	@Override
	public boolean sendMediaData() {
		return false;
	}

	@Override
	public boolean createBoxedFileMessage(
		byte[] thumbnailBlobIdIgnored,
		byte[] fileBlobIdIgnored,
		SymmetricEncryptionResult encryptionResultIgnored,
		MessageModel messageModel
	) throws ThreemaException {
		if (thumbnailBlobId == null || fileBlobId == null || fileEncryptionResult == null) {
			throw new ThreemaException("Required values have not been set by responsible DistributionListMessageReceiver");
		}
		return super.createBoxedFileMessage(thumbnailBlobId, fileBlobId, fileEncryptionResult, messageModel);
	}

	public void setFileMessageParameters(
		@NonNull byte[] thumbnailBlobId,
		@NonNull byte[] fileBlobId,
		@NonNull SymmetricEncryptionResult fileEncryptionResult
	) {
		this.fileBlobId = fileBlobId;
		this.thumbnailBlobId = thumbnailBlobId;
		this.fileEncryptionResult = fileEncryptionResult;
	}

	@Override
	public boolean equals(Object o) {
		// There are no distinguishing properties added in this subclass
		// equality is based on the contact model which is handled by the super-implementation
		return super.equals(o);
	}

	@Override
	public int hashCode() {
		// There are no distinguishing properties added in this subclass
		// hashCode is based on the contact model which is handled by the super-implementation
		return super.hashCode();
	}
}
