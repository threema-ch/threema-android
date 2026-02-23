package ch.threema.app.services;

import ch.threema.base.SessionScoped;

@SessionScoped
public interface ProfilePictureRecipientsService {
    void add(String id);

    boolean has(String id);

    void remove(String id);

    String[] getAll();

    void removeAll();

    void replaceAll(String[] ids);
}
