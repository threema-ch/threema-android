/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.backuprestore.csv;

import java.util.ArrayList;
import java.util.List;

public class RestoreSettings {
	/**
	 *
	 * 7: Added local contact avatar
	 * 8: Add file message support
	 * 9: add queued field to every message
	 * 10: add captions to message model
	 * 11: add profile pics
	 * 12: voip status messages (not implemented)
	 * 13: add hidden flag to contacts
	 * 15: add quoted message id to messages
	 * 16: added read and delivered date
	 */
	public static final int CURRENT_VERSION = 16;
	private int version = 1;

	public RestoreSettings(int version) {
		this.version = version;
	}

	public RestoreSettings() {
		this(1);
	}

	public int getVersion() {
		return this.version;
	}
	public void parse(List<String[]> strings) {
		for(String[] row: strings) {
			if(row.length == 2) {
				if(row[0].equals(Tags.TAG_INFO_VERSION)) {
					this.version = Integer.valueOf(row[1]);
				}
			}
		}
	}

	public List<String[]> toList() {
		List<String[]> l = new ArrayList<String[]>();
		l.add(new String[]{Tags.TAG_INFO_VERSION, String.valueOf(this.version)});
		return l;
	}
};
