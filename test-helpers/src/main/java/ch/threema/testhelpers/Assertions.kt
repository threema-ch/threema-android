package ch.threema.testhelpers

import ch.threema.common.waitAtMost
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope

/**
 * @return The thrown exception from the receiver lambda, or [AssertionError] if the exception was actually not thrown.
 */
infix fun (() -> Any?).willThrow(throwableClass: KClass<out Throwable>): Throwable =
    assertFailsWith(throwableClass) {
        try {
            this()
        } catch (throwable: Throwable) {
            if (throwable::class == throwableClass) {
                println("PASSED ${throwable::class.java.simpleName}: ${throwable.message}")
            }
            throw throwable
        }
    }

/**
 * @return The thrown exception from the receiver suspendable lambda, or [AssertionError] if the exception was actually not thrown.
 */
suspend infix fun (suspend () -> Any?).willThrow(throwableClass: KClass<out Throwable>): Throwable =
    assertFailsWith(throwableClass) {
        try {
            this()
        } catch (throwable: Throwable) {
            if (throwable::class == throwableClass) {
                println("PASSED ${throwable::class.java.simpleName}: ${throwable.message}")
            }
            throw throwable
        }
    }

/**
 *  Asserts the class type of [expectedThrowable] and the [Throwable.message] value to be the same as the actual throwable.
 */
infix fun (() -> Any?).willThrowExactly(expectedThrowable: Throwable) {
    val actualThrowable = assertFailsWith(
        exceptionClass = expectedThrowable::class,
        block = { this() },
    )
    assertEquals(
        expected = expectedThrowable.message,
        actual = actualThrowable.message,
    )
}

/**
 *  Asserts that [regex] has **at least** one match in the message of this [Throwable].
 *
 *  - If the [regex] is `null` this will assert this message to be also `null`.
 */
infix fun Throwable.withMessage(regex: Regex?) {
    if (regex == null) {
        assertNull(message)
        return
    }
    val messageNotNull: String = message ?: run {
        fail("Expected message to match '$regex' but it was actually null.")
    }
    assertTrue(
        actual = messageNotNull.contains(regex),
        message = "Throwable message of value '$messageNotNull' has no match from '$regex'.",
    )
}

suspend fun assertSuspendsForever(timeout: Duration = 1000.seconds, block: suspend () -> Unit) = coroutineScope {
    waitAtMost(timeout) {
        block()
        fail("expected to suspend forever, but the block unexpectedly unsuspended")
    }
}
