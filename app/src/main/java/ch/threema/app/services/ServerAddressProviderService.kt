package ch.threema.app.services

import ch.threema.base.SessionScoped
import ch.threema.domain.protocol.ServerAddressProvider

@SessionScoped
interface ServerAddressProviderService {
    val serverAddressProvider: ServerAddressProvider
}
