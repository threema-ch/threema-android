/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.utils

import android.os.Looper
import ch.threema.app.utils.executor.BackgroundExecutor
import ch.threema.app.utils.executor.BackgroundTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class BackgroundExecutorTest {

    @Rule
    @JvmField
    val timeout: Timeout = Timeout.seconds(10)

    private val executor = BackgroundExecutor()

    @Test
    fun testCorrectThreads() {
        val initialThread = Thread.currentThread()

        executor.execute(object : BackgroundTask<Unit> {
            override fun runBefore() {
                Assert.assertEquals(initialThread.id, Thread.currentThread().id)
            }

            override fun runInBackground() {
                val currentThreadId = Thread.currentThread().id
                Assert.assertNotEquals(initialThread.id, currentThreadId)
                Assert.assertNotEquals(Looper.getMainLooper().thread.id, currentThreadId)
            }

            override fun runAfter(result: Unit) {
                Assert.assertEquals(Looper.getMainLooper().thread.id, Thread.currentThread().id)
            }
        })
    }

    @Test
    fun testReturnValue() {
        executor.execute(object : BackgroundTask<Int> {
            override fun runInBackground() = 42
            override fun runAfter(result: Int) = Assert.assertEquals(42, result)
        })
    }

    @Test
    fun testOrder() = runBlocking {
        val methodExecutionList = mutableListOf<Int>()
        val expected = arrayOf(0, 1, 2, 3, 4, 5)

        executor.executeDeferred(object : BackgroundTask<Unit> {
            override fun runBefore() {
                methodExecutionList.add(0)
                Thread.sleep(500)
                methodExecutionList.add(1)
            }

            override fun runInBackground() {
                methodExecutionList.add(2)
                Thread.sleep(300)
                methodExecutionList.add(3)
            }

            override fun runAfter(result: Unit) {
                methodExecutionList.add(4)
                Thread.sleep(200)
                methodExecutionList.add(5)

                // Sleep again to test that the completable is completed after the runAfter method
                // is run.
                Thread.sleep(500)
            }
        }).await()

        Assert.assertArrayEquals(
            expected,
            methodExecutionList.toTypedArray()
        )
    }

    @Test
    fun testFailingBefore() {
        val deferred = executor.executeDeferred(object : BackgroundTask<Unit> {
            override fun runBefore() {
                throw IllegalStateException()
            }

            override fun runInBackground() {
                // This should never be executed as run before failed
                Assert.fail()
            }

            override fun runAfter(result: Unit) {
                // This should never be executed as run before failed
                Assert.fail()
            }
        })

        Assert.assertThrows(IllegalStateException::class.java) {
            runBlocking {
                deferred.await()
            }
        }
    }

    @Test
    fun testFailingBackground() {
        val deferred = executor.executeDeferred(object : BackgroundTask<Unit> {
            override fun runInBackground() {
                throw IllegalStateException()
            }

            override fun runAfter(result: Unit) {
                // This should never be executed as run in background failed
                Assert.fail()
            }
        })

        Assert.assertThrows(IllegalStateException::class.java) {
            runBlocking {
                deferred.await()
            }
        }
    }

    @Test
    fun testFailingAfter() {
        val deferred = executor.executeDeferred(object : BackgroundTask<Unit> {
            override fun runInBackground() {
                // Nothing to do
            }

            override fun runAfter(result: Unit) {
                throw IllegalStateException()
            }
        })

        Assert.assertThrows(IllegalStateException::class.java) {
            runBlocking {
                deferred.await()
            }
        }
    }

}
