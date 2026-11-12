package com.roteador.core.router

import co.touchlab.kermit.Logger
import com.roteador.core.model.RouterException
import io.ktor.client.HttpClient

/** Opens a driver for the router at [host] (identified, not logged in). */
fun interface RouterConnector {
    suspend fun open(host: String): RouterDriver
}

/**
 * Picks the driver for the router at a given address. Adding support for a new
 * router = a new `drivers/<name>` module whose factory is added to this list.
 */
class RouterDriverRegistry(
    private val factories: List<RouterDriverFactory>,
    private val httpClientFactory: () -> HttpClient = ::createRouterHttpClient,
) : RouterConnector {
    private val log = Logger.withTag("RouterDriverRegistry")

    /** Returns a driver that is identified but not yet logged in. */
    override suspend fun open(host: String): RouterDriver {
        val baseUrl = normaliseBaseUrl(host)
        val http = httpClientFactory()
        try {
            for (factory in factories) {
                val info = routerCall { factory.identify(http, baseUrl) } ?: continue
                log.i { "Router at $baseUrl handled by ${factory.id}: ${info.model} ${info.firmware}" }
                return factory.create(http, baseUrl, info)
            }
            throw RouterException.Unsupported("nenhum driver reconheceu $baseUrl")
        } catch (e: Throwable) {
            http.close()
            throw e
        }
    }

    companion object {
        fun normaliseBaseUrl(host: String): String {
            val trimmed = host.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        }
    }
}
