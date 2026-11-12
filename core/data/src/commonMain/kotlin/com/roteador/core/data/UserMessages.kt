package com.roteador.core.data

import com.roteador.core.model.RouterException

/** One place that turns failures into short Portuguese messages for the UI. */
fun Throwable.userMessage(): String = when (this) {
    is RouterException.Unreachable -> "O roteador não respondeu. Confira se o celular está no Wi-Fi dele."
    is RouterException.Unsupported -> "Este modelo de roteador ainda não é suportado."
    is RouterException.InvalidCredentials -> "Usuário ou senha incorretos."
    is RouterException.LockedOut -> "Muitas tentativas. O roteador liberou o login daqui a $secondsLeft s."
    is RouterException.SessionExpired -> "A sessão caiu. Entre de novo."
    is RouterException.Rejected -> "O roteador recusou a mudança: $reason"
    is RouterException.UnexpectedResponse -> "O roteador respondeu algo inesperado. O firmware pode ter mudado."
    else -> message ?: "Algo deu errado."
}
