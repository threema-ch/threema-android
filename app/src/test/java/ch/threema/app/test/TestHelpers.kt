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

package ch.threema.app.test

import ch.threema.app.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.test.KoinTestRule

fun TestScope.testDispatcherProvider(): DispatcherProvider {
    val testDispatcher: TestDispatcher = StandardTestDispatcher(testScheduler)
    return object : DispatcherProvider {
        override val main: CoroutineDispatcher
            get() = testDispatcher
        override val worker: CoroutineDispatcher
            get() = testDispatcher
        override val io: CoroutineDispatcher
            get() = testDispatcher
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.unconfinedTestDispatcherProvider(): DispatcherProvider {
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(testScheduler)
    return object : DispatcherProvider {
        override val main: CoroutineDispatcher
            get() = testDispatcher
        override val worker: CoroutineDispatcher
            get() = testDispatcher
        override val io: CoroutineDispatcher
            get() = testDispatcher
    }
}

fun koinTestModuleRule(moduleDeclaration: Module.() -> Unit) =
    KoinTestRule.create {
        modules(
            module(moduleDeclaration = moduleDeclaration),
        )
    }
