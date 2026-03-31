package ch.threema.app.systemupdates.updates;

import android.database.Cursor;

import org.koin.java.KoinJavaComponent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DeadlineListServiceImpl;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.Base32;
import ch.threema.storage.DatabaseProvider;
import kotlin.Lazy;

public class SystemUpdateToVersion43 implements SystemUpdate {

    private final Lazy<PreferenceService> preferenceServiceLazy = KoinJavaComponent.inject(PreferenceService.class);
    private final Lazy<ConversationCategoryService> conversationCategoryServiceLazy = KoinJavaComponent.inject(ConversationCategoryService.class);
    private final Lazy<RingtoneService> ringtoneServiceLazy = KoinJavaComponent.inject(RingtoneService.class);
    private final Lazy<PreferenceStore> preferenceStoreLazy = KoinJavaComponent.inject(PreferenceStore.class);
    private final Lazy<DatabaseProvider> databaseProviderLazy = KoinJavaComponent.inject(DatabaseProvider.class);

    @Override
    public void run() {
        var preferenceService = preferenceServiceLazy.getValue();
        var conversationCategoryService = conversationCategoryServiceLazy.getValue();
        var ringtoneService = ringtoneServiceLazy.getValue();
        var preferenceStore = preferenceStoreLazy.getValue();

        DeadlineListService mutedChatsService = new DeadlineListServiceImpl("list_muted_chats", preferenceService);

        String mutedChatsPrefs = "list_muted_chats";
        String hiddenChatsPrefs = "list_hidden_chats";
        String ringtonePrefs = "pref_individual_ringtones";
        String messageDraftPrefs = "pref_message_drafts";

        Map<Integer, String> oldMutedChatsMap = preferenceStore.getIntMap(mutedChatsPrefs);
        Map<Integer, String> oldHiddenChatsMap = preferenceStore.getIntMap(hiddenChatsPrefs);
        Map<Integer, String> oldRingtoneMap = preferenceStore.getIntMap(ringtonePrefs);
        preferenceStore.remove(messageDraftPrefs);

        HashMap<String, String> newMutedChatsMap = new HashMap<>();
        HashMap<String, String> newHiddenChatsMap = new HashMap<>();
        HashMap<String, String> newRingtoneMap = new HashMap<>();

        var database = databaseProviderLazy.getValue().getReadableDatabase();
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
