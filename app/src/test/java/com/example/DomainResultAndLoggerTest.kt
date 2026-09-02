package com.example

import com.example.core.AppLogger
import com.example.core.DomainResult
import com.example.core.onFailure
import com.example.core.onSuccess
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DomainResultAndLoggerTest {

    @Test
    fun testDomainResultSuccess() {
        val result: DomainResult<String> = DomainResult.Success("Louis")

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("Louis", result.getOrNull())

        var callbackExecuted = false
        result.onSuccess {
            assertEquals("Louis", it)
            callbackExecuted = true
        }.onFailure { _, _ ->
            fail("Failure callback should not execute on Success")
        }

        assertTrue(callbackExecuted)
    }

    @Test
    fun testDomainResultFailure() {
        val exception = IllegalStateException("Network unreachable")
        val result: DomainResult<String> = DomainResult.Failure(exception)

        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())

        var failureExecuted = false
        result.onSuccess {
            fail("Success callback should not execute on Failure")
        }.onFailure { error, msg ->
            assertEquals(exception, error)
            assertEquals("Network unreachable", msg)
            failureExecuted = true
        }

        assertTrue(failureExecuted)
    }

    @Test
    fun testAppLoggerExecution() {
        AppLogger.d("UnitTest") { "Debug message test" }
        AppLogger.i("UnitTest") { "Info message test" }
        AppLogger.w("UnitTest") { "Warning message test" }
        AppLogger.e("UnitTest", "Error message test", RuntimeException("Sample error"))
    }
}
