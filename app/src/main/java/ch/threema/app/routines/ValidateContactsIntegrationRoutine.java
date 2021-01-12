/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.routines;

import java.util.List;

import ch.threema.app.services.ContactService;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.storage.models.ContactModel;

public class ValidateContactsIntegrationRoutine implements Runnable {
	private final ContactService contactService;
	private final OnStatusUpdate onStatusUpdate;

	public interface OnStatusUpdate {
		void init(final int records);
		void progress(final int record, final ContactModel contact);
		void error(final Exception x);
		void finished();
	}

	public ValidateContactsIntegrationRoutine(ContactService contactService, OnStatusUpdate onStatusUpdate) {
		this.contactService = contactService;
		this.onStatusUpdate = onStatusUpdate;
	}


	@Override
	public void run() {
		try {
			List<ContactModel> contacts = this.contactService.getAll(true, true);
			if(this.onStatusUpdate != null) {
				this.onStatusUpdate.init(contacts.size());
			}

			AndroidContactUtil.getInstance().startCache();

			for(int n = 0; n < contacts.size(); n++) {
				ContactModel contactModel = contacts.get(n);
				if(contactModel != null) {
					if(this.onStatusUpdate != null) {
						this.onStatusUpdate.progress(n, contactModel);
					}
					this.contactService.validateContactAggregation(contactModel, true);
				}
			}

			AndroidContactUtil.getInstance().stopCache();
		}
		catch (Exception x) {
			if(this.onStatusUpdate != null) {
				this.onStatusUpdate.error(x);
			}
		}
		if(this.onStatusUpdate != null) {
			this.onStatusUpdate.finished();
		}
	}

}
