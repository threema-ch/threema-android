package ch.threema.domain.protocol.api.work;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WorkContact {
    public final @Nullable String firstName;
    public final @Nullable String lastName;
    public final @NonNull byte[] publicKey;
    public final @NonNull String threemaId;
    public final @Nullable String jobTitle;
    public final @Nullable String department;

    public WorkContact(
        @NonNull String threemaId,
        @NonNull byte[] publicKey,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String jobTitle,
        @Nullable String department
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.publicKey = publicKey;
        this.threemaId = threemaId;
        this.jobTitle = jobTitle;
        this.department = department;
    }
}
