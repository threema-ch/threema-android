package ch.threema.domain.protocol.connection.csp;

import ch.threema.base.SessionScoped;

@SessionScoped
public interface DeviceCookieManager {
    /**
     * Obtain an existing or new device cookie.
     *
     * @return the device cookie (16 bytes)
     */
    byte[] obtainDeviceCookie();

    /**
     * Inform the manager that a device cookie change indication has been received from the server.
     */
    void changeIndicationReceived();

    /**
     * Delete the stored device cookie, if any.
     */
    void deleteDeviceCookie();
}
