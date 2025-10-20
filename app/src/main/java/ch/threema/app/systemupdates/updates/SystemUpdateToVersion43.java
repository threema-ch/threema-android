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

package ch.threema.app.systemupdates.updates;

import android.database.Cursor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import ch.threema.app.drafts.DraftManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DeadlineListServiceImpl;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.stores.EncryptedPreferenceStore;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.Base32;

/**
 * add profile pic field to normal, group and distribution list message models
 */
public class SystemUpdateToVersion43 implements SystemUpdate {
    private @NonNull final ServiceManager serviceManager;

    public SystemUpdateToVersion43(
        @NonNull ServiceManager serviceManager
    ) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        DeadlineListService mutedChatsService = new DeadlineListServiceImpl("list_muted_chats", serviceManager.getPreferenceService());
        ConversationCategoryService conversationCategoryService = serviceManager.getConversationCategoryService();
        RingtoneService ringtoneService = serviceManager.getRingtoneService();

        String mutedChatsPrefs = "list_muted_chats";
        String hiddenChatsPrefs = "list_hidden_chats";
        String ringtonePrefs = "pref_individual_ringtones";
        String messageDraftPrefs = "pref_message_drafts";

        PreferenceStore preferenceStore = serviceManager.getPreferenceStore();
        EncryptedPreferenceStore encryptedPreferenceStoreInterface = serviceManager.getEncryptedPreferenceStore();

        Map<Integer, String> oldMutedChatsMap = preferenceStore.getIntMap(mutedChatsPrefs);
        Map<Integer, String> oldHiddenChatsMap = preferenceStore.getIntMap(hiddenChatsPrefs);
        Map<Integer, String> oldRingtoneMap = preferenceStore.getIntMap(ringtonePrefs);
        Map<Integer, String> oldMessageDraftsMap = encryptedPreferenceStoreInterface.getIntMap(messageDraftPrefs);
        preferenceStore.remove(messageDraftPrefs);

        HashMap<String, String> newMutedChatsMap = new HashMap<>();
        HashMap<String, String> newHiddenChatsMap = new HashMap<>();
        HashMap<String, String> newRingtoneMap = new HashMap<>();

        var database = serviceManager.getDatabaseService().getReadableDatabase();
        Cursor contacts = database.rawQuery("SELECT identity FROM contacts", null);
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
                        DraftManager.putMessageDraft(getNewUid(rawUid), oldMessageDraftsMap.get(oldUid), null);
                    }
                }
            }
            contacts.close();
        }

        Cursor groups = database.rawQuery("SELECT id FROM m_group", null);
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
                        DraftManager.putMessageDraft(getNewUid(rawUid), oldMessageDraftsMap.get(oldUid), null);
                    }
                }
            }
            groups.close();
        }

        preferenceStore.remove(mutedChatsPrefs);
        preferenceStore.save(mutedChatsPrefs, newMutedChatsMap);
        mutedChatsService.init();

        preferenceStore.remove(hiddenChatsPrefs);
        preferenceStore.save(hiddenChatsPrefs, newHiddenChatsMap);
        conversationCategoryService.invalidateCache();

        preferenceStore.remove(ringtonePrefs);
        preferenceStore.save(ringtonePrefs, newRingtoneMap);
        ringtoneService.init();
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
    public int getVersion() {
        return 43;
    }
}
