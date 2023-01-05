/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2023 Threema GmbH
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

import java.util.HashMap;

import ch.threema.base.utils.LoggingUtil;

public class DeadlineListServiceImpl implements DeadlineListService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("DeadlineListServiceImpl");

	private final Object lock = new Object();
	private HashMap<String, String> hashMap;
	private final String uniqueListName;
	private final PreferenceService preferenceService;

	public DeadlineListServiceImpl(String uniqueListName, PreferenceService preferenceService) {
		this.uniqueListName = uniqueListName;
		this.preferenceService = preferenceService;
		init();
	}

	@Override
	public void init() {
		this.hashMap = preferenceService.getStringHashMap(this.uniqueListName, false);
	}

	@Override
	public boolean has(String uid) {
		if(this.hashMap != null && uid != null) {
			synchronized (this.lock) {
				if (this.hashMap.containsKey(uid)) {
					long deadlineTime = 0;
					try {
						deadlineTime = Long.parseLong(this.hashMap.get(uid));
					} catch (NumberFormatException e) {
						logger.error("Exception", e);
					}

					if (deadlineTime == DEADLINE_INDEFINITE || System.currentTimeMillis() < deadlineTime) {
						return true;
					} else {
						this.remove(uid);
					}
				}
			}
		}
		return false;
	}

	@Override
	public void remove(String uid) {
		if(this.hashMap != null && uid != null) {
			synchronized (this.lock) {
				if(this.hashMap.containsKey(uid)) {
					this.hashMap.remove(uid);
					this.preferenceService.setStringHashMap(this.uniqueListName, this.hashMap);
				}
			}
		}
	}

	@Override
	public long getDeadline(String uid) {
		if(this.hashMap != null && uid != null) {
			synchronized (this.lock) {
				if (this.hashMap.containsKey(uid)) {
					return Long.parseLong(this.hashMap.get(uid));
				}
			}
		}
		return 0;
	}

	@Override
	public int getSize() {
		if (this.hashMap != null) {
			return this.hashMap.size();
		}
		return 0;
	}

	@Override
	public void clear() {
		if (this.hashMap != null) {
			this.hashMap.clear();
			this.preferenceService.setStringHashMap(this.uniqueListName, this.hashMap);
		}
	}

	@Override
	public void add(String uid, long timeout) {
		if(this.hashMap != null && uid != null) {
			synchronized (this.lock) {
				this.hashMap.put(uid, String.valueOf(timeout));
				this.preferenceService.setStringHashMap(this.uniqueListName, this.hashMap);
			}
		}
	}
}
