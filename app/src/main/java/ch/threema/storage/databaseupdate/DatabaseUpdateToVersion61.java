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

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import android.database.SQLException;

import ch.threema.app.utils.MimeUtil;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.media.FileDataModel;

import static ch.threema.storage.DatabaseExtensionsKt.fieldExists;

/**
 * add caption field to normal, group and distribution list message models
 */
public class DatabaseUpdateToVersion61 implements DatabaseUpdate {

    private final SQLiteDatabase sqLiteDatabase;

    public DatabaseUpdateToVersion61(SQLiteDatabase sqLiteDatabase) {
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
                    + " ADD COLUMN messageContentsType TINYINT DEFAULT " + MessageContentsType.UNDEFINED);
            }
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + MessageContentsType.TEXT + " WHERE type = " + MessageType.TEXT.ordinal());
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + MessageContentsType.IMAGE + " WHERE type = " + MessageType.IMAGE.ordinal());
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + MessageContentsType.VIDEO + " WHERE type = " + MessageType.VIDEO.ordinal());
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + MessageContentsType.VOICE_MESSAGE + " WHERE type = " + MessageType.VOICEMESSAGE.ordinal());
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + MessageContentsType.BALLOT + " WHERE type = " + MessageType.BALLOT.ordinal());
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + MessageContentsType.LOCATION + " WHERE type = " + MessageType.LOCATION.ordinal());
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + MessageContentsType.STATUS + " WHERE type = " + MessageType.STATUS.ordinal());
            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + MessageContentsType.VOIP_STATUS + " WHERE type = " + MessageType.VOIP_STATUS.ordinal());

            // check all file messages, extract mime type from json, add correct messagecontentstype
            try (Cursor fileMessages = sqLiteDatabase.rawQuery("SELECT id, body FROM " + table + " WHERE type = " + MessageType.FILE.ordinal(), null)) {
                if (fileMessages != null) {
                    while (fileMessages.moveToNext()) {
                        final int id = fileMessages.getInt(0);
                        final String body = fileMessages.getString(1);
                        if (body != null && !body.isEmpty()) {
                            FileDataModel fileDataModel = FileDataModel.create(body);
                            sqLiteDatabase.rawExecSQL("UPDATE " + table + " SET messageContentsType = " + MimeUtil.getContentTypeFromFileData(fileDataModel) + " WHERE id = " + id);
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
}
