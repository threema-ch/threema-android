/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.storage.databaseupdate;

import android.database.Cursor;
import android.database.SQLException;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.json.JSONArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static ch.threema.storage.databaseupdate.DatabaseUpdateExtensionsKt.fieldExists;

/**
 * add caption field to normal, group and distribution list message models
 */
public class DatabaseUpdateToVersion61 implements DatabaseUpdate {

    @NonNull
    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion61(@NonNull SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public void run() throws SQLException {
        //add new messageContentsType field to message model table
        for (String table : new String[]{
            "message",
            "m_group_message",
            "distribution_list_message"
        }) {
            if (!fieldExists(this.sqLiteDatabase, table, "messageContentsType")) {
                sqLiteDatabase.rawExecSQL("ALTER TABLE " + table
                    + " ADD COLUMN messageContentsType TINYINT DEFAULT 0");
            }
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = 1 WHERE type = 0");
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = 2 WHERE type = 1");
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = 3 WHERE type = 2");
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = 5 WHERE type = 3");
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = 8 WHERE type = 7");
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = 6 WHERE type = 4");
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = 7 WHERE type = 6");
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = 10 WHERE type = 9");

            // check all file messages, extract mime type from json, add correct messagecontentstype
            try (Cursor fileMessages = sqLiteDatabase.rawQuery("SELECT id, body FROM " + table + " WHERE type = 8", null)) {
                if (fileMessages != null) {
                    while (fileMessages.moveToNext()) {
                        final int id = fileMessages.getInt(0);
                        final String body = fileMessages.getString(1);
                        if (body != null && !body.isEmpty()) {
                            var mimeType = getMimeTypeFromFileModelBody(body);
                            var renderingType = getRenderingTypeFromFileModelBody(body);
                            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + getContentTypeFromMimeType(mimeType, renderingType) + " WHERE id = " + id);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String getDescription() {
        return "add mime type column";
    }

    @Override
    public int getVersion() {
        return 61;
    }

    @Nullable
    private static String getMimeTypeFromFileModelBody(@NonNull String fileModelBody) {
        try {
            return new JSONArray(fileModelBody).getString(2);
        } catch (Exception e) {
            return null;
        }
    }

    private static int getRenderingTypeFromFileModelBody(@NonNull String fileModelBody) {
        try {
            return new JSONArray(fileModelBody).getInt(5);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int getContentTypeFromMimeType(@Nullable String mimeType, int renderingType) {
        if (mimeType != null && !mimeType.isEmpty()) {
            if (mimeType.startsWith("image/gif")) {
                return 11;
            }
            if (mimeType.startsWith("image/") && !mimeType.startsWith("image/svg+xml")) {
                return 2;
            }
            if (mimeType.startsWith("video/")) {
                return 3;
            }
            if (mimeType.startsWith("audio/")) {
                if (renderingType == 1) {
                    return 5;
                }
                return 4;
            }
            if (mimeType.startsWith("text/x-vcard") || mimeType.startsWith("text/vcard")) {
                return 12;
            }
        }
        return 9;
    }
}
