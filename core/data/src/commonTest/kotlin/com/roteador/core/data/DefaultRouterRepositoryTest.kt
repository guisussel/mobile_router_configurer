package com.roteador.core.data

import com.roteador.core.model.RouterException
import com.roteador.core.model.WifiLink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultRouterRepositoryTest {
    private val store = MemorySecureStore()
    private val driver = FakeDriver()
    private val repository = DefaultRouterRepository(
        connector = { driver },
        credentials = CredentialsStore(store),
        wifiLink = { WifiLink(connected = true, gatewayIpv4 = "10.0.0.1") },
    )

    @Test
    fun suggestsGatewayBeforeAnythingIsSaved() {
        assertEquals("10.0.0.1", repository.suggestedHost())
    }

    @Test
    fun connectSavesPasswordOnlyWhenAsked() = runTest {
        repository.connect(LoginRequest("192.168.15.1", "admin", "certa", remember = false))
        assertIs<ConnectionState.Connected>(repository.connection.value)
        assertEquals(false, repository.savedLogin()?.hasPassword)
        assertEquals("192.168.15.1", repository.suggestedHost())

        repository.connect(LoginRequest("192.168.15.1", "admin", "certa", remember = true))
        assertEquals(true, repository.savedLogin()?.hasPassword)
    }

    @Test
    fun wrongPasswordClosesDriverAndStaysDisconnected() = runTest {
        assertFailsWith<RouterException.InvalidCredentials> {
            repository.connect(LoginRequest("192.168.15.1", "admin", "errada", remember = true))
        }
        assertTrue(driver.closed)
        assertEquals(ConnectionState.Disconnected, repository.connection.value)
        assertNull(repository.savedLogin())
    }

    @Test
    fun expiredSessionLogsInAgainOnce() = runTest {
        repository.connect(LoginRequest("192.168.15.1", "admin", "certa", remember = false))
        driver.expireNextCall = true
        repository.status()
        assertEquals(2, driver.logins)
    }

    @Test
    fun callsWithoutConnectionFail() = runTest {
        assertFailsWith<RouterException.SessionExpired> { repository.status() }
    }

    @Test
    fun restoreSessionUsesSavedPassword() = runTest {
        assertFalse(repository.restoreSession())
        CredentialsStore(store).save("192.168.15.1", "admin", "certa")
        assertTrue(repository.restoreSession())
        assertIs<ConnectionState.Connected>(repository.connection.value)
    }

    @Test
    fun disconnectCanForgetPassword() = runTest {
        repository.connect(LoginRequest("192.168.15.1", "admin", "certa", remember = true))
        repository.disconnect(forgetPassword = true)
        assertEquals(ConnectionState.Disconnected, repository.connection.value)
        assertEquals(false, repository.savedLogin()?.hasPassword)
    }
}
