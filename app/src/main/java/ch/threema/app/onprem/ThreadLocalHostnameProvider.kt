package ch.threema.app.onprem

class ThreadLocalHostnameProvider : HostnameProvider {
    private val hostname = ThreadLocal<String>()

    override fun getHostname(): String = hostname.get()
        ?: error("Hostname not set")

    fun setHostname(hostname: String) {
        this.hostname.set(hostname)
    }
}
