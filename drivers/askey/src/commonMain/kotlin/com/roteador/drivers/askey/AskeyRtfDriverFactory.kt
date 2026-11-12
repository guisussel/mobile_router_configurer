package com.roteador.drivers.askey

import com.roteador.core.model.RouterInfo
import com.roteador.core.router.RouterDriver
import com.roteador.core.router.RouterDriverFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Askey RTF family as shipped by Vivo Fibra (RTF8225VW and siblings). The
 * "about" page is public, so the model is known before asking for a password.
 */
class AskeyRtfDriverFactory : RouterDriverFactory {
    override val id = "askey-rtf"

    override suspend fun identify(http: HttpClient, baseUrl: String): RouterInfo? {
        val response = http.get("$baseUrl/${AskeyPaths.ABOUT}")
        if (!response.status.isSuccess()) return null
        return AskeyParsers.routerInfo(response.bodyAsText())
            ?.takeIf { it.manufacturer.equals("Askey", ignoreCase = true) && it.model.startsWith("RTF", ignoreCase = true) }
    }

    override fun create(http: HttpClient, baseUrl: String, info: RouterInfo): RouterDriver =
        AskeyRtfDriver(http, baseUrl, info)
}
