package com.sethg.app.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.sethg.app.ui.screen.*
import com.sethg.app.ui.viewmodel.AuthViewModel
import com.sethg.app.ui.viewmodel.LanguageViewModel
import com.sethg.app.ui.viewmodel.ProfileViewModel

sealed class Screen(val route: String) {
    object Language      : Screen("language")
    object Login         : Screen("login")
    object Register      : Screen("register")
    object RoleSelection : Screen("role_selection")
    object Dashboard     : Screen("dashboard")
    object NewLot        : Screen("new_lot")
    object LotDetail     : Screen("lot/{lotId}") { fun of(id: String) = "lot/$id" }
    object Handover      : Screen("handover/{lotId}/{declaredKg}") {
        fun of(id: String, declaredKg: Double) = "handover/$id/$declaredKg"
    }
    object Notifications : Screen("notifications")
}

@Composable
fun SethGNavHost() {
    val navController = rememberNavController()

    // ── Bootstrap VMs (always present at nav root) ────────────────────────────
    val langVm:   LanguageViewModel = hiltViewModel()
    val authVm:   AuthViewModel     = hiltViewModel()

    val langState by langVm.uiState.collectAsState()
    val authState by authVm.uiState.collectAsState()

    // Wait for DataStore to finish loading before choosing a start destination
    if (langState.isLoading) return

    // Determine start destination based on stored state:
    //   1. Language not chosen → Language screen
    //   2. Not logged in        → Login screen
    //   3. Logged in            → Dashboard (RoleSelection is handled inside Dashboard entry)
    val startDestination = when {
        !langState.isLanguageAlreadySelected -> Screen.Language.route
        !authState.isLoggedIn                -> Screen.Login.route
        else                                 -> Screen.Dashboard.route
    }

    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {

        // ── Language ──────────────────────────────────────────────────────────
        composable(Screen.Language.route) {
            LanguageScreen(
                onLanguageConfirmed = {
                    if (authState.isLoggedIn) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Language.route) { inclusive = true }
                        }
                    }
                },
                onBack = if (authState.isLoggedIn || navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else null
            )
        }

        // ── Login ─────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    // Always go through role-selection gate after fresh login.
                    // RoleSelection auto-skips if the role is already set.
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Register ──────────────────────────────────────────────────────────
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin  = { navController.popBackStack() },
                onRegisterSuccess  = {
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Role Selection ────────────────────────────────────────────────────
        // Gate: if the user already has a non-"user" role, skip straight to Dashboard.
        composable(Screen.RoleSelection.route) {
            val profileVm: ProfileViewModel = hiltViewModel()
            val profileState by profileVm.uiState.collectAsState()

            val role = profileState.user?.role

            // Once the profile has loaded and role is real, navigate to Dashboard
            LaunchedEffect(profileState.isLoading, role) {
                if (!profileState.isLoading && role != null && role != "user") {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                }
            }

            // While checking or if role is "user" / null, show the picker
            if (role == null || role == "user" || profileState.isLoading) {
                RoleSelectionScreen(
                    onSelectVendor = {
                        profileVm.updateProfile(role = "vendor")
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.RoleSelection.route) { inclusive = true }
                        }
                    },
                    onSelectRecycler = {
                        profileVm.updateProfile(role = "recycler")
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.RoleSelection.route) { inclusive = true }
                        }
                    }
                )
            }
        }

        // ── Main Dashboard ────────────────────────────────────────────────────
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

        // ── Sub-screens ───────────────────────────────────────────────────────
        composable(Screen.NewLot.route) {
            NewLotScreen(onDone = { navController.popBackStack() })
        }

        composable(Screen.LotDetail.route) {
            LotDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Handover.route) {
            HandoverScreen(onDone = { navController.popBackStack() })
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
    }
}
