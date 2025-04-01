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

package ch.threema.app.globalsearch;

import static ch.threema.app.services.MessageServiceImpl.FILTER_CHATS;
import static ch.threema.app.services.MessageServiceImpl.FILTER_GROUPS;
import static ch.threema.app.services.MessageServiceImpl.FILTER_INCLUDE_ARCHIVED;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.AbstractMessageModel;

public class GlobalSearchRepository {
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<List<AbstractMessageModel>> messageModels;
    private MessageService messageService;
    private String queryString = "";
    private int filterFlags = 0;
    private boolean sortAscending = false; /* whether to sort the results in ascending or descending order by message creation date */

    GlobalSearchRepository() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            messageService = null;
            try {
                messageService = serviceManager.getMessageService();
            } catch (ThreemaException e) {
                return;
            }
            messageModels = new MutableLiveData<>() {
                @Nullable
                @Override
                public List<AbstractMessageModel> getValue() {
                    return getMessagesForText(
                        queryString,
                        FILTER_CHATS | FILTER_GROUPS | FILTER_INCLUDE_ARCHIVED,
                        sortAscending
                    );
                }
            };
        }
    }

    LiveData<List<AbstractMessageModel>> getMessageModels() {
        return messageModels;
    }

    private List<AbstractMessageModel> getMessagesForText(String queryString, @MessageService.MessageFilterFlags int filterFlags, boolean sortAscending) {
        return messageService.getMessagesForText(queryString, filterFlags, sortAscending);
    }

    /**
     * @param query         Query string to look for in message body and captions
     * @param filterFlags   MessageFilterFlags describing how to filter messages
     * @param allowEmpty    If set to true, an empty query string means "match everything" otherwise "match nothing"
     * @param sortAscending Whether to sort the results in ascending or descending order by message creation date
     */
    @SuppressLint("StaticFieldLeak")
    void onQueryChanged(String query, @MessageService.MessageFilterFlags int filterFlags, boolean allowEmpty, boolean sortAscending) {
        this.queryString = query;
        this.filterFlags = filterFlags;
        this.sortAscending = sortAscending;

        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... strings) {
                if (messageService != null) {
                    if (!allowEmpty && TestUtil.isEmptyOrNull(query)) {
                        messageModels.postValue(new ArrayList<>());
                        isLoading.postValue(false);
                    } else {
                        isLoading.postValue(true);
                        messageModels.postValue(getMessagesForText(query, filterFlags, sortAscending));
                        isLoading.postValue(false);
                    }
                }
                return null;
            }
        }.execute();
    }

    LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    @SuppressLint("StaticFieldLeak")
    public void onDataChanged() {
        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... strings) {
                messageModels.postValue(getMessagesForText(queryString, filterFlags, sortAscending));
                return null;
            }
        }.execute();
    }
}
