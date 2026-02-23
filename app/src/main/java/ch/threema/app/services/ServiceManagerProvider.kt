package ch.threema.app.services

import ch.threema.app.managers.ServiceManager

@Deprecated("Avoid accessing the service manager directly. Use Koin instead to get dependencies")
interface ServiceManagerProvider {

    fun getServiceManagerOrNull(): ServiceManager?
}
