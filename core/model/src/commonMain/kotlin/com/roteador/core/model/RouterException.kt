package com.roteador.core.model

/** Every failure a driver can report. The UI maps each one to a message. */
sealed class RouterException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Nothing answered at the address (wrong network, router off, timeout). */
    class Unreachable(cause: Throwable? = null) : RouterException("Roteador não respondeu", cause)

    /** It answered, but no driver knows this model. */
    class Unsupported(val description: String) : RouterException("Roteador não suportado: $description")

    class InvalidCredentials : RouterException("Usuário ou senha inválidos")

    /** Too many wrong passwords; the router refuses logins for a while. */
    class LockedOut(val secondsLeft: Int) : RouterException("Login bloqueado por $secondsLeft s")

    /** The router dropped our session (timeout or someone else logged in). Repositories re-login once. */
    class SessionExpired : RouterException("Sessão expirada")

    /** The router refused a change (validation failed). */
    class Rejected(val reason: String) : RouterException("Configuração recusada: $reason")

    /** A page did not look like what the driver expects (firmware update?). */
    class UnexpectedResponse(detail: String) : RouterException("Resposta inesperada: $detail")
}
