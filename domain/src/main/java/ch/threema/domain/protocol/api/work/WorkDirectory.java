package ch.threema.domain.protocol.api.work;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

public class WorkDirectory {
    public final List<WorkDirectoryContact> workContacts = new ArrayList<>();
    public final int totalRecord;
    public final int pageSize;
    public final WorkDirectoryFilter currentFilter;

    public final @Nullable WorkDirectoryFilter nextFilter;

    public final @Nullable WorkDirectoryFilter previousFilter;

    public WorkDirectory(int totalRecord,
                         int pageSize,
                         WorkDirectoryFilter currentFilter,
                         @Nullable WorkDirectoryFilter nextFilter,
                         @Nullable WorkDirectoryFilter previousFilter) {
        this.totalRecord = totalRecord;
        this.pageSize = pageSize;
        this.nextFilter = nextFilter;
        this.previousFilter = previousFilter;
        this.currentFilter = currentFilter;
    }
}
