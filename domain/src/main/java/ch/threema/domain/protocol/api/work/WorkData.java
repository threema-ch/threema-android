package ch.threema.domain.protocol.api.work;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WorkData {
    public final List<WorkContact> workContacts = new ArrayList<>();

    @NonNull
    public final WorkMDMSettings mdm = new WorkMDMSettings();
    public final WorkDirectorySettings directory = new WorkDirectorySettings();
    public final WorkOrganization organization = new WorkOrganization();
    @Nullable
    public String logoDark;
    @Nullable
    public String logoLight;
    public String supportUrl;
    public int checkInterval;
    public int responseCode;
}
