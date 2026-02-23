package ch.threema.app.services

import ch.threema.app.managers.ServiceManager

class ServiceManagerProviderImpl : ServiceManagerProvider {
    private var serviceManager: ServiceManager? = null

    fun setServiceManager(serviceManager: ServiceManager?) {
        this.serviceManager = serviceManager
    }

    override fun getServiceManagerOrNull(): ServiceManager? =
        serviceManager
}
