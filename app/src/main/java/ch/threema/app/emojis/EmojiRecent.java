/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app.emojis;

import java.util.LinkedList;

import ch.threema.app.services.PreferenceService;

public class EmojiRecent {

	private final PreferenceService preferenceService;
	private static final int RECENT_SIZE_LIMIT = 100;
	private static LinkedList<String> recentList = new LinkedList<>(), recentListNew = new LinkedList<>();
	private static boolean modified = false;

	public EmojiRecent(PreferenceService preferenceService) {
		this.preferenceService = preferenceService;
		readFromPrefs();
	}

	public boolean add(String emojiSequence) {
		synchronized (recentListNew) {
			if (recentListNew.contains(emojiSequence)) {
				recentListNew.removeLastOccurrence(emojiSequence);
			}

			//resize list
			while (recentListNew.size() >= RECENT_SIZE_LIMIT) {
				recentListNew.removeLast();
			}

			recentListNew.addFirst(emojiSequence);

			modified = true;
		}
		return true;
	}

	public boolean remove(String emojiSequence) {
		synchronized (recentListNew) {
			if (recentListNew.contains(emojiSequence)) {
				recentListNew.remove(emojiSequence);
				modified = true;
				syncRecents();
				return true;
			}
		}
		return false;
	}

	public LinkedList<String> getRecentList() {
		return recentList;
	}

	public int getNumberOfRecentEmojis() {
		return recentList.size();
	}

	public void saveToPrefs() {
		preferenceService.setRecentEmojis2(recentListNew);
		syncRecents();
	}

	public void readFromPrefs() {
		if (preferenceService != null) {
			recentList = preferenceService.getRecentEmojis2();
			if (recentList != null) {
				recentListNew = (LinkedList<String>) recentList.clone();
			}
		}
	}

	public boolean syncRecents() {
		if (modified) {
			recentList = (LinkedList<String>) recentListNew.clone();
			modified = false;
			return true;
		}
		return false;
	}
}
