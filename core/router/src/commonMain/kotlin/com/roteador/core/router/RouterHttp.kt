package com.roteador.core.router

import com.roteador.core.model.RouterException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/**
 * HTTP client for talking to a router on the LAN: its own cookie jar (one per
 * router session), short timeouts, redirects followed. The engine comes from the
 * platform source set (OkHttp on Android, Darwin on iOS).
 */
fun createRouterHttpClient(): HttpClient = HttpClient {
    expectSuccess = false
    followRedirects = true
    install(HttpCookies) { storage = AcceptAllCookiesStorage() }
    install(HttpTimeout) {
        connectTimeoutMillis = 4_000
        requestTimeoutMillis = 10_000
        socketTimeoutMillis = 10_000
    }
}

/** Runs a network call and turns transport failures into [RouterException.Unreachable]. */
suspend fun <T> routerCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: RouterException) {
    throw e
} catch (e: HttpRequestTimeoutException) {
    throw RouterException.Unreachable(e)
} catch (e: IOException) {
    throw RouterException.Unreachable(e)
}
