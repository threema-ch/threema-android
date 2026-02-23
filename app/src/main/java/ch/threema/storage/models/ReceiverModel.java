package ch.threema.storage.models;

import java.util.Date;

import androidx.annotation.Nullable;

/**
 * Base interface for ContactModel, GroupModel and DistributionListModel.
 */
public interface ReceiverModel {
    /**
     * Set the last conversation update timestamp.
     * <p>
     * If the value is set to `null`, then the conversation will disappear
     * from the conversation list.
     */
    ReceiverModel setLastUpdate(@Nullable Date lastUpdate);

    /**
     * Return the `lastUpdate` timestamp for this receiver.
     */
    @Nullable
    Date getLastUpdate();

    /**
     * Return whether or not the conversation with this receiver is archived.
     */
    boolean isArchived();

    /**
     * Return whether the conversation with this receiver should be hidden from the conversation
     * list.
     * <p>
     * Potential use cases for this flag are contacts with acquaintance level GROUP, or ad-hoc
     * distribution lists.
     */
    boolean isHidden();
}
