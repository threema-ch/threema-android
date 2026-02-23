package ch.threema.app.voip.groupcall

/**
 * The intention when creating or joining a call
 */
enum class GroupCallIntention {
    /**
     * Join an existing call. If there is no call considered running in this group, start
     * a new call.
     */
    JOIN_OR_CREATE,

    /**
     * Join an existing call. If there is no call considered running in this group, do not
     * start a new call.
     */
    JOIN,
}
