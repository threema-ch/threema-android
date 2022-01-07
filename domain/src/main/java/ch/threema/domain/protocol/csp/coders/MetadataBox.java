/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

package ch.threema.domain.protocol.csp.coders;

import ch.threema.base.ThreemaException;

import java.io.Serializable;
import java.util.Arrays;

public class MetadataBox implements Serializable {
	private final byte[] box;

	public MetadataBox(byte[] box) throws ThreemaException {
		if (box.length > Short.MAX_VALUE) {
			throw new ThreemaException("Metadata box is too long");
		}
		this.box = box;
	}

	public byte[] getBox() {
		return box;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		MetadataBox that = (MetadataBox) o;
		return Arrays.equals(box, that.box);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(box);
	}
}
