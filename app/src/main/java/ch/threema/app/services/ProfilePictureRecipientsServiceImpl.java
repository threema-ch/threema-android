package ch.threema.app.services;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.preference.service.PreferenceService;

public class ProfilePictureRecipientsServiceImpl implements ProfilePictureRecipientsService {
    private static final String LIST_NAME = "identity_list_profilepics";

    private final Object lock = new Object();
    @NonNull
    private String[] ids;
    @NonNull
    private final PreferenceService preferenceService;

    public ProfilePictureRecipientsServiceImpl(@NonNull PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
        this.ids = preferenceService.getEncryptedList(LIST_NAME);
    }

    @Override
    public boolean has(@Nullable String id) {
        synchronized (lock) {
            return Arrays.asList(ids).contains(id);
        }
    }

    @Override
    public void remove(@Nullable String id) {
        synchronized (lock) {
            List<String> idList = Arrays.asList(ids);
            if (idList.contains(id)) {
                String[] newIdentities = new String[idList.size() - 1];
                int pos = 0;
                for (String other : idList) {
                    if (other != null && !other.equals(id)) {
                        newIdentities[pos++] = other;
                    }
                }
                preferenceService.setList(LIST_NAME, newIdentities);
                ids = newIdentities;
            }
        }
    }

    @Override
    public void add(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        synchronized (lock) {
            List<String> idList = Arrays.asList(ids);
            if (!idList.contains(id)) {
                ids = Arrays.copyOf(ids, ids.length + 1);
                ids[ids.length - 1] = id;
                preferenceService.setList(LIST_NAME, ids);
            }
        }
    }

    @Override
    @NonNull
    public synchronized String[] getAll() {
        return ids;
    }

    @Override
    public void removeAll() {
        ids = new String[0];
        preferenceService.setList(LIST_NAME, ids);
    }

    @Override
    public void replaceAll(@Nullable String[] ids) {
        this.ids = ids != null ? ids : new String[0];
        this.preferenceService.setList(LIST_NAME, this.ids);
    }
}
