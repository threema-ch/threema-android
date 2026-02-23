package ch.threema.domain.protocol.api.work;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WorkDirectoryContact extends WorkContact {
    @Nullable
    public final String csi;
    public final List<String> categoryIds = new ArrayList<>();
    public final WorkOrganization organization = new WorkOrganization();

    public WorkDirectoryContact(
        @NonNull String threemaId,
        @NonNull byte[] publicKey,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String csi,
        @Nullable String jobTitle,
        @Nullable String department
    ) {
        super(threemaId, publicKey, firstName, lastName, jobTitle, department);
        this.csi = csi;
    }

    public String getInitial(boolean sortByFirstName) {
        String name;
        if (sortByFirstName) {
            name = (firstName != null ? firstName + " " : "") +
                (lastName != null ? lastName : "");

        } else {
            name = (lastName != null ? lastName + " " : "") +
                (firstName != null ? firstName : "");
        }

        if (!name.isEmpty()) {
            return name.substring(0, 1);
        }
        return " ";
    }
}
