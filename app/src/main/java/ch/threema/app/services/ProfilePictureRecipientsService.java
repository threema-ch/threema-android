package ch.threema.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.SessionScoped;

@SessionScoped
public interface ProfilePictureRecipientsService {
    void add(@Nullable String id);

    boolean has(@Nullable String id);

    void remove(@Nullable String id);

    @NonNull
    String[] getAll();

    void removeAll();

    void replaceAll(@Nullable String[] ids);
}
