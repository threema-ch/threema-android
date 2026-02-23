package ch.threema.domain.protocol.api.work;

import java.util.ArrayList;
import java.util.List;

public class WorkDirectoryFilter {
    public static final int SORT_BY_FIRST_NAME = 1;
    public static final int SORT_BY_LAST_NAME = 2;

    private String query;
    private int page = 0;
    private int sortBy = SORT_BY_FIRST_NAME;
    private boolean sortAscending = true;
    private final List<WorkDirectoryCategory> categories = new ArrayList<>();

    public WorkDirectoryFilter query(String query) {
        this.query = query;
        return this;
    }

    public String getQuery() {
        return this.query;
    }

    public WorkDirectoryFilter page(int page) {
        this.page = page;
        return this;
    }

    public int getPage() {
        return this.page;
    }

    public WorkDirectoryFilter sortBy(int sortBy, boolean sortAscending) {
        switch (this.sortBy) {
            case SORT_BY_FIRST_NAME:
            case SORT_BY_LAST_NAME:
                this.sortBy = sortBy;
                this.sortAscending = sortAscending;
                break;
        }
        return this;
    }

    public int getSortBy() {
        return this.sortBy;
    }

    public boolean isSortAscending() {
        return this.sortAscending;
    }

    public WorkDirectoryFilter addCategory(WorkDirectoryCategory category) {
        if (!this.categories.contains(category)) {
            this.categories.add(category);
        }
        return this;
    }

    public List<WorkDirectoryCategory> getCategories() {
        return this.categories;
    }

    public WorkDirectoryFilter copy() {
        WorkDirectoryFilter newFilter = new WorkDirectoryFilter();
        newFilter.sortBy = this.sortBy;
        newFilter.sortAscending = this.sortAscending;
        newFilter.page = this.page;
        newFilter.query = this.query;
        // Copy categories
        for (WorkDirectoryCategory c : this.categories) {
            newFilter.categories.add(c);
        }
        return newFilter;
    }
}
