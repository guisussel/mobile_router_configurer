package com.roteador.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.roteador.feature.login.LoginScreen
import kotlinx.serialization.Serializable

/** Login screen. [autoLogin] = use the saved password without asking (false right after "Sair"). */
@Serializable
data class LoginRoute(val autoLogin: Boolean = true)

/** Everything behind the login: the five tabs. */
@Serializable
data object HomeRoute

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        NavHost(navController, startDestination = LoginRoute()) {
            composable<LoginRoute> { entry ->
                LoginScreen(
                    autoLogin = entry.toRoute<LoginRoute>().autoLogin,
                    onLoggedIn = {
                        navController.navigate(HomeRoute) { popUpTo<LoginRoute> { inclusive = true } }
                    },
                )
            }
            composable<HomeRoute> {
                HomeScreen(
                    onLoggedOut = {
                        navController.navigate(LoginRoute(autoLogin = false)) { popUpTo<HomeRoute> { inclusive = true } }
                    },
                )
            }
        }
    }
}
