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

package config

import utils.LocalProperties

/**
 * Can be used to check whether certain features are explicitly enabled or disabled locally, i.e., during development on the current machine,
 * by checking whether the feature flag is set in the 'local.properties' file.
 *
 * To explicitly enable or disable a feature, add *threema.features.<name-of-feature> = [true|false]* to the 'local.properties' file
 * in the project root directory.
 */
object BuildFeatureFlags {
    operator fun get(feature: String): Boolean? =
        LocalProperties.getBoolean("threema.features.$feature")
}
