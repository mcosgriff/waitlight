package io.cosgriff.waitlight.data

import io.cosgriff.waitlight.domain.FetchOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class FetchFailureTest {
    @Test
    fun `a failed DNS lookup is offline, wrapped or not`() {
        assertEquals(FetchOutcome.OFFLINE, fetchFailure(UnknownHostException("www.aircarecolorado.com")))
        assertEquals(FetchOutcome.OFFLINE, fetchFailure(IOException("wrapped", IllegalStateException(UnknownHostException()))))
    }

    @Test
    fun `anything else is AirCare's end`() {
        assertEquals(FetchOutcome.FAILED, fetchFailure(SocketTimeoutException()))
        assertEquals(FetchOutcome.FAILED, fetchFailure(IOException("HTTP 503", IllegalStateException())))
    }
}
