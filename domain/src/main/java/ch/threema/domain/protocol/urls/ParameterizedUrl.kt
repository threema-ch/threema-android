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

package ch.threema.domain.protocol.urls

import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("ParameterizedUrl")

abstract class ParameterizedUrl(
    private val template: String,
    requiredPlaceholders: Array<String>,
) {
    init {
        requiredPlaceholders.forEach { placeholder ->
            if ("{$placeholder}" !in template) {
                logger.error("Placeholder {} not found in template {}", placeholder, template)
            }
        }
    }

    protected fun getUrl(vararg parameters: Pair<String, String>): String {
        var url = template
        parameters.forEach { (placeholder, value) ->
            url = url.replace("{$placeholder}", value)
        }
        return url
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        return template == (other as ParameterizedUrl).template
    }

    override fun hashCode(): Int = template.hashCode()

    override fun toString() = "${super.toString()}($template)"
}
