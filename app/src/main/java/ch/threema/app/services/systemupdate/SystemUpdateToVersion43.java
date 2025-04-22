/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.services.systemupdate;

import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import org.slf4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DeadlineListServiceImpl;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.Base32;
import ch.threema.base.utils.LoggingUtil;

/**
 * add profile pic field to normal, group and distribution list message models
 */
public class SystemUpdateToVersion43 implements UpdateSystemService.SystemUpdate {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion43");

    private final SQLiteDatabase sqLiteDatabase;

    public SystemUpdateToVersion43(SQLiteDatabase sqLiteDatabase) {
        this.sqLiteDatabase = sqLiteDatabase;
    }

    @Override
    public boolean runDirectly() {
        return true;
    }

    @Override
    public boolean runAsync() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            logger.error("update script 43 failed, no service manager available");
            return false;
        }

        DeadlineListService mutedChatsService = new DeadlineListServiceImpl("list_muted_chats", serviceManager.getPreferenceService());
        ConversationCategoryService conversationCategoryService = serviceManager.getConversationCategoryService();
        RingtoneService ringtoneService = serviceManager.getRingtoneService();

        if (ThreemaApplication.getMasterKey() == null) {
            logger.error("update script 43 failed, No Master key");
            return false;
        }

        String mutedChatsPrefs = "list_muted_chats";
        String hiddenChatsPrefs = "list_hidden_chats";
        String ringtonePrefs = "pref_individual_ringtones";
        String messageDraftPrefs = "pref_message_drafts";

        PreferenceStore preferenceStore = new PreferenceStore(ThreemaApplication.getAppContext(), ThreemaApplication.getMasterKey());

        HashMap<Integer, String> oldMutedChatsMap = preferenceStore.getHashMap(mutedChatsPrefs, false);
        HashMap<Integer, String> oldHiddenChatsMap = preferenceStore.getHashMap(hiddenChatsPrefs, false);
        HashMap<Integer, String> oldRingtoneMap = preferenceStore.getHashMap(ringtonePrefs, false);
        HashMap<Integer, String> oldMessageDraftsMap = preferenceStore.getHashMap(messageDraftPrefs, true);
        preferenceStore.remove(messageDraftPrefs);

        HashMap<String, String> newMutedChatsMap = new HashMap<>();
        HashMap<String, String> newHiddenChatsMap = new HashMap<>();
        HashMap<String, String> newRingtoneMap = new HashMap<>();

        Cursor contacts = this.sqLiteDatabase.rawQuery("SELECT identity FROM contacts", null);
        if (contacts != null) {
            while (contacts.moveToNext()) {
                final String identity = contacts.getString(0);

                if (!TestUtil.isEmptyOrNull(identity)) {
                    String rawUid = "c-" + identity;
                    int oldUid = (rawUid).hashCode();

                    if (oldMutedChatsMap.containsKey(oldUid)) {
                        newMutedChatsMap.put(getNewUid(rawUid), oldMutedChatsMap.get(oldUid));
                    }

                    if (oldHiddenChatsMap.containsKey(oldUid)) {
                        newHiddenChatsMap.put(getNewUid(rawUid), oldHiddenChatsMap.get(oldUid));
                    }

                    if (oldRingtoneMap.containsKey(oldUid)) {
                        newRingtoneMap.put(getNewUid(rawUid), oldRingtoneMap.get(oldUid));
                    }

                    if (oldMessageDraftsMap.containsKey(oldUid)) {
                        ThreemaApplication.putMessageDraft(getNewUid(rawUid), oldMessageDraftsMap.get(oldUid), null);
                    }
                }
            }
            contacts.close();
        }

        Cursor groups = this.sqLiteDatabase.rawQuery("SELECT id FROM m_group", null);
        if (groups != null) {
            while (groups.moveToNext()) {
                final int id = groups.getInt(0);

                if (id >= 0) {
                    String rawUid = "g-" + String.valueOf(id);
                    int oldUid = (rawUid).hashCode();

                    if (oldMutedChatsMap.containsKey(oldUid)) {
                        newMutedChatsMap.put(getNewUid(rawUid), oldMutedChatsMap.get(oldUid));
                    }

                    if (oldHiddenChatsMap.containsKey(oldUid)) {
                        newHiddenChatsMap.put(getNewUid(rawUid), oldHiddenChatsMap.get(oldUid));
                    }

                    if (oldRingtoneMap.containsKey(oldUid)) {
                        newRingtoneMap.put(getNewUid(rawUid), oldRingtoneMap.get(oldUid));
                    }

                    if (oldMessageDraftsMap.containsKey(oldUid)) {
                        ThreemaApplication.putMessageDraft(getNewUid(rawUid), oldMessageDraftsMap.get(oldUid), null);
                    }
                }
            }
            groups.close();
        }

        preferenceStore.remove(mutedChatsPrefs);
        preferenceStore.saveStringHashMap(mutedChatsPrefs, newMutedChatsMap, false);
        mutedChatsService.init();

        preferenceStore.remove(hiddenChatsPrefs);
        preferenceStore.saveStringHashMap(hiddenChatsPrefs, newHiddenChatsMap, false);
        conversationCategoryService.invalidateCache();

        preferenceStore.remove(ringtonePrefs);
        preferenceStore.saveStringHashMap(ringtonePrefs, newRingtoneMap, false);
        ringtoneService.init();

        return true;
    }

    private String getNewUid(String rawUid) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update((rawUid).getBytes());
            return Base32.encode(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    @Override
    public String getText() {
        return "version 43";
    }
}
