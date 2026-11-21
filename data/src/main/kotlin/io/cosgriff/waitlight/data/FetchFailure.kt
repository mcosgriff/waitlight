package io.cosgriff.waitlight.data

import io.cosgriff.waitlight.domain.FetchOutcome
import java.net.UnknownHostException

/**
 * What a fetch that threw [error] came to. No network shows up as a failed DNS lookup, wrapped or not, so that is
 * [FetchOutcome.OFFLINE]; anything else is AirCare's end, [FetchOutcome.FAILED].
 */
internal fun fetchFailure(error: Throwable): FetchOutcome =
    if (generateSequence(error) { it.cause }.any { it is UnknownHostException }) FetchOutcome.OFFLINE else FetchOutcome.FAILED
