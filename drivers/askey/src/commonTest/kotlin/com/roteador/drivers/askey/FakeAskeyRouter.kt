package com.roteador.drivers.askey

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters

/**
 * In-memory stand-in for the router, enforcing the rules the real one has:
 * cookie session, redirect to login.asp when logged out, 400 on CGI calls
 * without a form Content-Type, one-shot session keys.
 */
internal class FakeAskeyRouter(
    private val user: String = "admin",
    private val password: String = "certa",
) {
    var loggedIn = false
    val calls = mutableListOf<String>()
    var lastWifiApply: Map<String, String>? = null
    var rebooted = false
    var validationAnswer = "0"
    private var sessionKey = 1111

    val client = HttpClient(MockEngine { request -> handle(request) }) {
        followRedirects = true
        expectSuccess = false
        install(HttpCookies) { storage = AcceptAllCookiesStorage() }
    }

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath.trimStart('/')
        calls += "${request.method.value} $path"
        val query = request.url.parameters
        val isCgi = path.startsWith("cgi-bin/")

        if (isCgi && request.method == HttpMethod.Post && request.body.contentType?.match(ContentType.Application.FormUrlEncoded) != true) {
            return respond("<H2>Access Error: 400 -- Bad Request</H2>", HttpStatusCode.BadRequest)
        }

        return when (path) {
            "about-power-box.asp" -> html(AskeyFixtures.about)
            "login.asp" -> html(AskeyFixtures.login(), cookie = "_httpdSessionId_=abc; Path=/")
            "cgi-bin/te_acceso_router.cgi" -> {
                val form = String(request.body.toByteArray()).parseUrlEncodedParameters()
                loggedIn = AskeyCodec.obfuscate(form["loginUsername"].orEmpty()) == user &&
                    AskeyCodec.obfuscate(form["loginPassword"].orEmpty()) == password
                html("<script>location.href = \"/index_cliente.asp\";</script>")
            }
            "cgi-bin/te_logout.cgi" -> { loggedIn = false; redirectToLogin() }
            "cgi-bin/sv_getvar.cmd" -> guarded { html(if (query["wifi_id"] == "1") "36,40,44,48" else "1,6,11") }
            "cgi-bin/cbReboot.xml" -> guarded {
                if (query["sessionKey"] != sessionKey.toString()) respond("bad key", HttpStatusCode.Forbidden)
                else { rebooted = true; html("<ok/>") }
            }
            "cgi-bin/te_wifi.wl", "cgi-bin/te_wifi_5ghz.wl" -> guarded {
                if (query["sessionKey"] != sessionKey.toString()) return@guarded respond("bad key", HttpStatusCode.Forbidden)
                if (query["validation_only"] == "1") html(validationAnswer)
                else {
                    lastWifiApply = query.entries().associate { it.key to it.value.first() } + ("path" to path)
                    html("<html>ok</html>")
                }
            }
            "index_cliente.asp" -> guarded { html(AskeyFixtures.status) }
            "device-management-statistics.asp" -> guarded { html(AskeyFixtures.statistics) }
            "settings-wireless-network-u.asp" -> guarded { html(AskeyFixtures.wifi.replace("1111", sessionKey.toString())) }
            "popup-reboot.asp" -> guarded { html(AskeyFixtures.rebootPopup.replace("2222", sessionKey.toString())) }
            else -> respond("not found", HttpStatusCode.NotFound)
        }
    }

    private suspend fun MockRequestHandleScope.guarded(block: suspend MockRequestHandleScope.() -> HttpResponseData) =
        if (loggedIn) block() else redirectToLogin()

    private fun MockRequestHandleScope.redirectToLogin() =
        respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/login.asp"))

    private fun MockRequestHandleScope.html(body: String, cookie: String? = null): HttpResponseData {
        val headers = if (cookie == null) headersOf(HttpHeaders.ContentType, "text/html")
        else headersOf(HttpHeaders.ContentType to listOf("text/html"), HttpHeaders.SetCookie to listOf(cookie))
        return respond(body, HttpStatusCode.OK, headers)
    }

    fun rotateSessionKey() { sessionKey++ }
}
