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

package ch.threema.app.compose.theme.dimens

import androidx.compose.ui.unit.dp

private const val GRID_BASE = 8

@Suppress("unused")
object GridUnit {
    val x0 = 0.dp
    val x0_5 = GRID_BASE.dp / 2 // 4
    val x1 = GRID_BASE.dp // 8
    val x1_5 = GRID_BASE.dp * 1.5f // 12
    val x2 = GRID_BASE.dp * 2 // 16
    val x2_5 = GRID_BASE.dp * 2.5f // 20
    val x3 = GRID_BASE.dp * 3 // 24
    val x4 = GRID_BASE.dp * 4 // 32
    val x5 = GRID_BASE.dp * 5 // 40
    val x6 = GRID_BASE.dp * 6 // 48
    val x7 = GRID_BASE.dp * 7 // 56
    val x8 = GRID_BASE.dp * 8 // 64
    val x9 = GRID_BASE.dp * 9 // 72
    val x10 = GRID_BASE.dp * 10 // 80
}
