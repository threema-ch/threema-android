/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.fs;

public enum ForwardSecurityMode {
    /**
     * The message was sent without forward security.
     */
    NONE(0),

    /**
     * The message was sent with 2DH. This is only set for contact messages.
     */
    TWODH(1),

    /**
     * The message was sent with 4DH. This is only set for contact messages.
     */
    FOURDH(2),

    /**
     * The message was sent to each member of the group with 2DH or 4DH. This is only set for group
     * messages.
     */
    ALL(3),

    /**
     * The message was sent with 2DH or 4DH to some members of the group and without forward security to others. This is only set for group
     * messages.
     */
    PARTIAL(4);


    private final int value;

    ForwardSecurityMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ForwardSecurityMode getByValue(int value) {
        for (ForwardSecurityMode forwardSecurityMode : ForwardSecurityMode.values()) {
            if (forwardSecurityMode.value == value) {
                return forwardSecurityMode;
            }
        }

        return null;
    }
}
