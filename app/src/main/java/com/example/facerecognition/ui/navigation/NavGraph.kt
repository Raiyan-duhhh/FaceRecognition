package com.example.facerecognition.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.facerecognition.ui.screens.*
import com.example.facerecognition.viewmodel.AdminViewModel
import com.example.facerecognition.viewmodel.StaffViewModel

// ── Transition Configuration ─────────────────────────────────────────────────
private const val TRANSITION_DURATION = 400

@Composable
fun FaceAttendanceNavGraph() {
    val navController = rememberNavController()

    // Instantiate app-wide ViewModels
    val adminViewModel: AdminViewModel = viewModel()
    val staffViewModel: StaffViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "home",

        // ── Default Transitions (applied to every route) ─────────────
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
                initialOffset = { it / 4 }
            ) + fadeIn(
                animationSpec = tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
                targetOffset = { it / 5 }
            ) + fadeOut(
                animationSpec = tween(TRANSITION_DURATION / 2, easing = FastOutSlowInEasing)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
                initialOffset = { it / 4 }
            ) + fadeIn(
                animationSpec = tween(TRANSITION_DURATION, easing = FastOutSlowInEasing)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(TRANSITION_DURATION, easing = FastOutSlowInEasing),
                targetOffset = { it / 5 }
            ) + fadeOut(
                animationSpec = tween(TRANSITION_DURATION / 2, easing = FastOutSlowInEasing)
            )
        }
    ) {

        // ── Main Entry ────────────────────────────────────────────────

        composable("home") {
            HomeScreen(
                onNavigateToKiosk = { navController.navigate("kiosk") },
                onNavigateToStaffRegistration = { navController.navigate("register_staff") },
                onNavigateToAdminLogin = { navController.navigate("admin_login") },
                onNavigateToStaffLogin = { navController.navigate("staff_login") }
            )
        }

        // ── Kiosk Mode ────────────────────────────────────────────────

        composable("kiosk") {
            KioskScreen()
        }

        // ── Admin Portal ──────────────────────────────────────────────

        composable("admin_login") {
            AdminLoginScreen(
                viewModel = adminViewModel,
                onLoginSuccess = {
                    navController.navigate(route = "admin_map") {
                        popUpTo(route = "admin_login") { inclusive = true }
                    }
                }
            )
        }

        composable("admin_dashboard") {
            AdminDashboardScreen(
                viewModel = adminViewModel,
                onNavigateToMap = { navController.navigate("admin_map") },
                onNavigateToReports = { navController.navigate("reports") },
                onNavigateToManageStaff = { navController.navigate("manage_staff") },
                onLogout = {
                    navController.navigate("home") {
                        popUpTo(0) // Clear back stack
                    }
                }
            )
        }

        composable("admin_map") {
            AdminMapScreen(
                onNavigateToApprovals = { navController.navigate("admin_dashboard") },
                onNavigateToReports = { navController.navigate("reports") },
                onNavigateToManageStaff = { navController.navigate("manage_staff") }
            )
        }

        composable("register_staff") {
            RegisterStaffScreen(
                viewModel = adminViewModel,
                onRegistrationComplete = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Placeholder Admin Routes ──────────────────────────────────

        composable("manage_staff") {
            ManageStaffScreen(
                viewModel = adminViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("reports") {
            ReportsScreen(
                viewModel = adminViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable("holidays") {
            HolidaysScreen(onBack = { navController.popBackStack() })
        }

        // ── Staff Portal ──────────────────────────────────────────────

        composable("staff_login") {
            StaffLoginScreen(
                viewModel = staffViewModel,
                onLoginSuccess = {
                    navController.navigate("staff_dashboard") {
                        popUpTo("staff_login") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("staff_dashboard") {
            StaffDashboardScreen(
                viewModel = staffViewModel,
                onLogout = {
                    staffViewModel.resetLoginState()
                    navController.navigate("home") {
                        popUpTo(0)
                    }
                }
            )
        }
    }
}
