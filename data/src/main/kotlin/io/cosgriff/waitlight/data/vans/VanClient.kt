package io.cosgriff.waitlight.data.vans

import io.cosgriff.waitlight.data.aircare.AirCareClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import javax.inject.Inject

/**
 * GETs of AirCare's van page and its tabs' tables, on AirCare's own host (ADR-0001). Nothing about the phone or its
 * user is sent; the user agent names the app, as for the wait-times page.
 */
class VanClient @Inject constructor(private val http: HttpClient) {
    /**
     * The van page's HTML. A broken tab makes AirCare answer HTTP 500 with the other tabs still in the body, so a
     * 500 is read like a 200; any other error status throws, as an unreachable host does.
     */
    suspend fun page(): String = get(PAGE_URL, acceptServerError = true)

    /** The table of tab [day] (`vanlocationstabledisplay_new.php?day=N`, the id read from the page's iframe). */
    suspend fun table(day: Int): String = get("$TABLE_URL?day=$day", acceptServerError = false)

    private suspend fun get(url: String, acceptServerError: Boolean): String {
        val response = http.get(url) {
            expectSuccess = false
            header(HttpHeaders.UserAgent, AirCareClient.USER_AGENT)
        }
        val ok = response.status.isSuccess() || (acceptServerError && response.status == HttpStatusCode.InternalServerError)
        val body = response.bodyAsText()
        if (!ok) throw ResponseException(response, body.take(200))
        return body
    }

    companion object {
        /** Without the trailing slash, which AirCare redirects away. */
        const val PAGE_URL = "https://aircarecolorado.com/locations/van-locations"
        const val TABLE_URL = "https://aircarecolorado.com/packages/customaircare/vanlocationstabledisplay_new.php"
    }
}
