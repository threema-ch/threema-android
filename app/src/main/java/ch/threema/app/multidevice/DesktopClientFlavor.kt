package ch.threema.app.multidevice

import androidx.annotation.StringRes
import ch.threema.app.R

enum class DesktopClientFlavor(@StringRes val downloadLink: Int) {
    Consumer(
        downloadLink = R.string.desktop_client_download_link_threema_consumer,
    ),
    Work(
        downloadLink = R.string.desktop_client_download_link_threema_work,
    ),
    OnPrem(
        downloadLink = R.string.desktop_client_download_link_threema_on_prem,
    ),
    Green(
        downloadLink = R.string.desktop_client_download_link_threema_consumer,
    ),
    Blue(
        downloadLink = R.string.desktop_client_download_link_threema_work,
    ),
}
