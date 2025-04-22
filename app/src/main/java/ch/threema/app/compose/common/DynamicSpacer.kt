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
    Spacer(Modifier.size(dimensionResource(R.dimen.spacing_one_grid_unit)))
}

@Composable
fun DynamicSpacerSize1_5() {
    Spacer(Modifier.size(dimensionResource(R.dimen.spacing_one_and_a_half_grid_unit)))
}

@Composable
fun DynamicSpacerSize2() {
    Spacer(Modifier.size(dimensionResource(R.dimen.spacing_two_grid_unit)))
}

@Composable
fun DynamicSpacerSize3() {
    Spacer(Modifier.size(dimensionResource(R.dimen.spacing_three_grid_unit)))
}

@Composable
fun DynamicSpacerSize4() {
    Spacer(Modifier.size(dimensionResource(R.dimen.spacing_four_grid_unit)))
}
