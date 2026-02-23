package ch.threema.domain.protocol.csp.messages.fs;

public enum ForwardSecurityMode {
    /**
     * The message was sent without forward security.
     */
    NONE(0),

    /**
     * The message was sent with 2DH. This is only set for contact messages.
     */
    TWODH(1),

    /**
     * The message was sent with 4DH. This is only set for contact messages.
     */
    FOURDH(2),

    /**
     * The message was sent to each member of the group with 2DH or 4DH. This is only set for group
     * messages.
     */
    ALL(3),

    /**
     * The message was sent with 2DH or 4DH to some members of the group and without forward security to others. This is only set for group
     * messages.
     */
    PARTIAL(4);


    private final int value;

    ForwardSecurityMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ForwardSecurityMode getByValue(int value) {
        for (ForwardSecurityMode forwardSecurityMode : ForwardSecurityMode.values()) {
            if (forwardSecurityMode.value == value) {
                return forwardSecurityMode;
            }
        }

        return null;
    }
}
