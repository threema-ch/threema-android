package ch.threema.app.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;
import ch.threema.domain.protocol.api.work.WorkDirectory;
import ch.threema.domain.protocol.api.work.WorkDirectoryContact;

public class DirectoryDataSourceFactory extends DataSource.Factory<WorkDirectory, WorkDirectoryContact> {
    private boolean init;

    // Used to hold a reference to the data source
    @Nullable
    public MutableLiveData<DirectoryDataSource> postLiveData;

    public DirectoryDataSourceFactory() {
        this.init = true;
    }

    @NonNull
    @Override
    public DataSource<WorkDirectory, WorkDirectoryContact> create() {
        DirectoryDataSource dataSource = new DirectoryDataSource();

        if (this.init) {
            dataSource.setQueryText(null);
            this.init = false;
        }

        // Keep reference to the data source with a MutableLiveData reference
        postLiveData = new MutableLiveData<>();
        postLiveData.postValue(dataSource);

        return dataSource;
    }
}
