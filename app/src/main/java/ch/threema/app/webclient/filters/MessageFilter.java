package ch.threema.app.webclient.filters;

import ch.threema.annotation.SameThread;
import ch.threema.app.services.MessageService;
import ch.threema.storage.models.MessageType;

@SameThread
public class MessageFilter implements MessageService.MessageFilter {
    private static int MESSAGE_PAGE_SIZE = 50;
    private Integer referenceId = null;

    @Override
    public long getPageSize() {
        //load one more to check if more messages are available
        return this.getRealPageSize() + 1;
    }

    public long getRealPageSize() {
        return MESSAGE_PAGE_SIZE;
    }

    @Override
    public Integer getPageReferenceId() {
        return this.referenceId;
    }

    public MessageFilter setPageReferenceId(Integer id) {
        this.referenceId = id;
        return this;
    }

    @Override
    public boolean withStatusMessages() {
        return true;
    }

    @Override
    public boolean withUnsaved() {
        return false;
    }

    @Override
    public boolean onlyUnread() {
        return false;
    }

    @Override
    public boolean onlyDownloaded() {
        return false;
    }

    @Override
    public MessageType[] types() {
        return null;
    }

    @Override
    public int[] contentTypes() {
        return null;
    }

    @Override
    public int[] displayTags() {
        return null;
    }
}
