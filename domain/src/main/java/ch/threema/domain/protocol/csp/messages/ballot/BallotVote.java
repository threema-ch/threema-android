/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.ballot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.protocol.csp.messages.BadMessageException;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class BallotVote {

    private final static int POS_CHOICE_ID = 0;
    private final static int POS_CHOICE_VALUE = 1;

    private int id;
    private int value;

    public BallotVote(int id, int value) {
        this.id = id;
        this.value = value;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @NonNull
    public static BallotVote parse(@Nullable JSONArray jsonArray) throws BadMessageException {
        try {
            if (jsonArray == null) {
                throw new BadMessageException("TM036");
            }
            int id = jsonArray.getInt(POS_CHOICE_ID);
            int value = jsonArray.getInt(POS_CHOICE_VALUE);
            return new BallotVote(id, value);
        } catch (JSONException e) {
            throw new BadMessageException("TM033", e);
        }
    }

    public JSONArray getJsonArray() throws BadMessageException {
        JSONArray jsonArray = new JSONArray();
        try {
            jsonArray.put(POS_CHOICE_ID, this.id);
            jsonArray.put(POS_CHOICE_VALUE, this.value);
        } catch (Exception e) {
            throw new BadMessageException("TM036", e);
        }
        return jsonArray;
    }

    public void write(@NonNull ByteArrayOutputStream bos) throws Exception {
        bos.write(this.generateString().getBytes(StandardCharsets.US_ASCII));
    }

    public String generateString() throws BadMessageException {
        return this.getJsonArray().toString();
    }
}
