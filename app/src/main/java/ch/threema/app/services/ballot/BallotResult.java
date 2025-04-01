/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

package ch.threema.app.services.ballot;


import java.util.ArrayList;
import java.util.List;

abstract class BallotResult {
    private List<Integer> messages = new ArrayList<Integer>();
    private boolean success = false;

    public boolean isSuccess() {
        return this.success;
    }

    public List<Integer> getMessageResources() {
        return this.messages;
    }

    protected BallotResult error(int messageResourceId) {
        this.success = false;
        this.messages.add(messageResourceId);
        return this;
    }

    public BallotResult success() {
        this.success = true;
        return this;
    }

}
