package io.cosgriff.waitlight.data.aircare

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import javax.inject.Inject

/**
 * The only network call Waitlight makes (ADR-0001): a GET of AirCare's public wait-times page. Nothing about the
 * phone or its user is sent; the user agent names the app so AirCare can see who is asking.
 */
class AirCareClient @Inject constructor(private val http: HttpClient) {
    /** The page's HTML. Throws when AirCare can't be reached or answers with an error. */
    suspend fun page(): String = http.get(PAGE_URL) { header(HttpHeaders.UserAgent, USER_AGENT) }.bodyAsText()

    companion object {
        const val PAGE_URL = "https://aircarecolorado.com/locations/wait-times/wait-times-interactive"
        const val USER_AGENT = "Waitlight (+https://github.com/mcosgriff/waitlight)"
    }
}
