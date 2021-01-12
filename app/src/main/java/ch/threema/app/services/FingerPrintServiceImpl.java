/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.app.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.HashMap;

import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.LogUtil;
import ch.threema.client.Utils;
import ch.threema.storage.models.ContactModel;

public class FingerPrintServiceImpl implements FingerPrintService {
	private static final Logger logger = LoggerFactory.getLogger(FingerPrintServiceImpl.class);

	private final ContactService contactService;
	private final IdentityStore identityStore;
	private HashMap<String, String> fingerPrintCache = new HashMap<>();

	public FingerPrintServiceImpl(ContactService contactService, IdentityStore identityStore) {
		this.contactService = contactService;
		this.identityStore = identityStore;
	}

	public String getFingerPrint(String identity) {
		return this.getFingerPrint(identity, false);
	}

	public String getFingerPrint(String identity, boolean reload) {
		if (!fingerPrintCache.containsKey(identity) || reload) {
			byte[] key = null;
			String storeIdentity = this.identityStore.getIdentity();
			if ((storeIdentity != null) && storeIdentity.equals(identity)) {
				//fingerprint of my identity
				key = this.identityStore.getPublicKey();
			} else {
				ContactModel contact = this.contactService.getByIdentity(identity);
				if (contact != null) {
					key = contact.getPublicKey();
				}
			}

			if (key != null) {
				String fingerPrint = "undefined/failed";

				try {
					MessageDigest md = MessageDigest.getInstance("SHA-256");
					md.update(key);
					byte byteData[] = md.digest();
					fingerPrint = Utils.byteArrayToHexString(byteData).toLowerCase().substring(0, 32);
				} catch (Exception e) {
					logger.error("Exception", e);
				}

				this.fingerPrintCache.put(identity, fingerPrint);
				return fingerPrint;
			}
		}

		return fingerPrintCache.get(identity);
	}
}
