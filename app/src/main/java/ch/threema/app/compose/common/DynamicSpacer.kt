/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

@file:Suppress("unused")

package ch.threema.app.compose.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import ch.threema.app.R

// These [Spacer] composables will add more or less spacing depending on the device screen size.

@Composable
fun DynamicSpacerSize1() {
    Spacer(Modifier.size(dimensionResource(R.dimen.grid_unit_x1)))
}

@Composable
fun DynamicSpacerSize1_5() {
    Spacer(Modifier.size(dimensionResource(R.dimen.grid_unit_x1_5)))
}

@Composable
fun DynamicSpacerSize2() {
    Spacer(Modifier.size(dimensionResource(R.dimen.grid_unit_x2)))
}

@Composable
fun DynamicSpacerSize3() {
    Spacer(Modifier.size(dimensionResource(R.dimen.grid_unit_x3)))
}

@Composable
fun DynamicSpacerSize4() {
    Spacer(Modifier.size(dimensionResource(R.dimen.grid_unit_x4)))
}
