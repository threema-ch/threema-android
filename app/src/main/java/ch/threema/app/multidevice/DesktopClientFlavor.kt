/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
