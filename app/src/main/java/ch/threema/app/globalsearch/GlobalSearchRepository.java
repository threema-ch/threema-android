package ch.threema.app.globalsearch;

import static ch.threema.app.services.MessageServiceImpl.FILTER_CHATS;
import static ch.threema.app.services.MessageServiceImpl.FILTER_GROUPS;
import static ch.threema.app.services.MessageServiceImpl.FILTER_INCLUDE_ARCHIVED;

import android.annotation.SuppressLint;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import ch.threema.app.services.MessageService;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.AbstractMessageModel;

public class GlobalSearchRepository {
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<List<AbstractMessageModel>> messageModels;
    private MessageService messageService;
    private String queryString = "";
    private int filterFlags = 0;
    private boolean sortAscending = false; /* whether to sort the results in ascending or descending order by message creation date */

    GlobalSearchRepository(@NonNull MessageService messageService) {
        this.messageService = messageService;
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
