/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.utils;


import java.util.List;
import java.util.Map;

import ch.threema.base.utils.Utils;

/**
 * Convert Json to X and X to Json
 */
public class ListReader {

    private final List<Object> list;
    private int pos = 0;

    public ListReader(List<Object> list) {
        this.list = list;
    }

    public ListReader rewind() {
        this.pos = 0;
        return this;
    }

    public String nextString() {
        return (String) this.next();
    }

    public byte[] nextStringAsByteArray() {
        String v = this.nextString();
        if (v != null && v.length() > 0) {
            return Utils.hexStringToByteArray(v);
        }
        return null;
    }

    public Integer nextInteger() {
        return (Integer) this.next();
    }

    public Boolean nextBool() {
        return (Boolean) this.next();
    }

    public Map<String, Object> nextMap() {
        Object n = this.next();
        if (n instanceof Map) {
            return (Map<String, Object>) n;
        }

        return null;
    }

    private Object next() {
        if (this.list.size() > this.pos) {
            return this.list.get(this.pos++);
        }

        return null;
    }
}
