package ch.threema.domain.protocol.api.work;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class WorkDirectorySettings implements Serializable {
    public boolean enabled = false;
    @NonNull
    public List<WorkDirectoryCategory> categories = new ArrayList<>();
}
