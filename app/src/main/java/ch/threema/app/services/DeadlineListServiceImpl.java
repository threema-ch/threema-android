package ch.threema.app.services;

import org.slf4j.Logger;

import java.util.HashMap;

import ch.threema.app.preference.service.PreferenceService;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class DeadlineListServiceImpl implements DeadlineListService {
    private static final Logger logger = getThreemaLogger("DeadlineListServiceImpl");

    private final Object lock = new Object();
    private HashMap<String, String> hashMap;
    private final String uniqueListName;
    private final PreferenceService preferenceService;

    public DeadlineListServiceImpl(String uniqueListName, PreferenceService preferenceService) {
        this.uniqueListName = uniqueListName;
        this.preferenceService = preferenceService;
        init();
    }

    @Override
    public void init() {
        this.hashMap = new HashMap<>(preferenceService.getStringMap(this.uniqueListName));
    }

    @Override
    public boolean has(String uid) {
        if (this.hashMap != null && uid != null) {
            synchronized (this.lock) {
                if (this.hashMap.containsKey(uid)) {
                    long deadlineTime = 0;
                    try {
                        deadlineTime = Long.parseLong(this.hashMap.get(uid));
                    } catch (NumberFormatException e) {
                        logger.error("Exception", e);
                    }

                    if (deadlineTime == DEADLINE_INDEFINITE || System.currentTimeMillis() < deadlineTime) {
                        return true;
                    } else {
                        this.remove(uid);
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void remove(String uid) {
        if (this.hashMap != null && uid != null) {
            synchronized (this.lock) {
                if (this.hashMap.containsKey(uid)) {
                    this.hashMap.remove(uid);
                    preferenceService.setStringMap(uniqueListName, hashMap);
                }
            }
        }
    }

    @Override
    public long getDeadline(String uid) {
        if (this.hashMap != null && uid != null) {
            synchronized (this.lock) {
                if (this.hashMap.containsKey(uid)) {
                    return Long.parseLong(this.hashMap.get(uid));
                }
            }
        }
        return 0;
    }

    @Override
    public int getSize() {
        if (this.hashMap != null) {
            return this.hashMap.size();
        }
        return 0;
    }

    @Override
    public void clear() {
        if (this.hashMap != null) {
            this.hashMap.clear();
            preferenceService.setStringMap(uniqueListName, hashMap);
        }
    }

    @Override
    public void add(String uid, long timeout) {
        if (this.hashMap != null && uid != null) {
            synchronized (this.lock) {
                this.hashMap.put(uid, String.valueOf(timeout));
                preferenceService.setStringMap(uniqueListName, hashMap);
            }
        }
    }
}
