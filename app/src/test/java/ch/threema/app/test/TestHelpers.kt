package ch.threema.app.test

import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.utils.DispatcherProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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

fun mockAppReady(): AppStartupMonitor =
    mockk<AppStartupMonitor> {
        every { isReady() } returns true
        every { isReady(any()) } returns true
        coEvery { awaitSystem(any()) } just runs
        coEvery { awaitAll() } just runs
    }

fun mockAppNotReady(): AppStartupMonitor =
    mockk<AppStartupMonitor> {
        every { isReady() } returns false
        every { isReady(any()) } returns false
        coEvery { awaitSystem(any()) } coAnswers { awaitCancellation() }
        coEvery { awaitAll() } coAnswers { awaitCancellation() }
    }
