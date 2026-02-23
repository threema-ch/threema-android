package ch.threema.domain.protocol.connection.csp.socket

import java.net.InetSocketAddress

interface ChatServerAddressProvider {
    /**
     * Move the internal pointer to the next available address.
     * If the last address is reached, the pointer will wrap around and start with the first address again.
     */
    fun advance()

    /**
     * Get the [InetSocketAddress] the internal pointer is currently pointing to.
     * If no addresses are available (e.g. when [update] has not been called prior to this call),
     * `null` is returned
     */
    fun get(): InetSocketAddress?

    /**
     * Update the available [InetSocketAddress]es
     */
    fun update()
}
