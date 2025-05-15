/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.domain.onprem

import java.time.Instant

data class OnPremConfig(
    val validUntil: Instant,
    val license: OnPremLicense,
    val domains: OnPremConfigDomains?,
    val chat: OnPremConfigChat,
    val directory: OnPremConfigDirectory,
    val blob: OnPremConfigBlob,
    val work: OnPremConfigWork,
    val avatar: OnPremConfigAvatar,
    val safe: OnPremConfigSafe,
    val web: OnPremConfigWeb?,
    val mediator: OnPremConfigMediator?,
    val maps: OnPremConfigMaps?,
)
