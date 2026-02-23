package ch.threema.app.services;

public interface DeadlineListService {
    long DEADLINE_INDEFINITE = -1;
    long DEADLINE_INDEFINITE_EXCEPT_MENTIONS = -2;

    void add(String uid, long timeout);

    void init();

    boolean has(String uid);

    void remove(String uid);

    /**
     * Return the deadline timestamp for this uid.
     * If no entry is found, 0 is returned.
     * For indefinite settings, DeadlineListService.DEADLINE_INDEFINITE is returned.
     */
    long getDeadline(String uid);

    int getSize();

    void clear();
}
