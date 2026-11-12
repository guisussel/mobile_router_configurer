package com.roteador.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler

/** Opens the router's own web page in the browser: the way out for anything the app cannot do. */
@Composable
fun OpenRouterPageButton(url: String, modifier: Modifier = Modifier, text: String = "Abrir a página do roteador") {
    val uriHandler = LocalUriHandler.current
    OutlinedButton(onClick = { uriHandler.openUri(url) }, modifier = modifier.fillMaxWidth()) { Text(text) }
}
