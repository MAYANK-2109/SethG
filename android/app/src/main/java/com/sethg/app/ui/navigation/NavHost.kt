package com.sethg.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.sethg.app.ui.screen.*
import com.sethg.app.ui.viewmodel.AuthViewModel
import com.sethg.app.ui.viewmodel.LanguageViewModel

sealed class Screen(val route: String) {
    object Language  : Screen("language")
    object Login     : Screen("login")
    object Register  : Screen("register")
    object Dashboard : Screen("dashboard")
    object Profile   : Screen("profile")
    object EditProfile : Screen("edit_profile")
}

@Composable
fun SethGNavHost() {
    val navController = rememberNavController()
    val langVm: LanguageViewModel = hiltViewModel()
    val langState by langVm.uiState.collectAsState()

    // Wait for DataStore to initialise before routing
    if (langState.isLoading) return

    val startDestination = when {
        !langState.isLanguageAlreadySelected -> Screen.Language.route
        else -> {
            val authVm: AuthViewModel = hiltViewModel()
            val authState by authVm.uiState.collectAsState()
            if (authState.isLoggedIn) Screen.Dashboard.route else Screen.Login.route
        }
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Language.route) {
            LanguageScreen(
                onLanguageConfirmed = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Language.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            MainScreen(
                navController = navController,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
