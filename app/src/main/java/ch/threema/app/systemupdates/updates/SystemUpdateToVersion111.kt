package ch.threema.app.systemupdates.updates

import ch.threema.app.managers.ServiceManager

class SystemUpdateToVersion111(
    private val serviceManager: ServiceManager,
) : SystemUpdate {
    override fun run() {
        serviceManager.preferenceStore.remove("pref_group_request_overview_hidden")
    }

    override fun getVersion() = VERSION

    override fun getDescription() =
        "remove group link settings"

    companion object {
        const val VERSION = 111
    }
}
