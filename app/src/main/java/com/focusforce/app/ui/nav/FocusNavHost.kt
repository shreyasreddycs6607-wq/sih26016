package com.focusforce.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.focusforce.app.ui.AppViewModel
import com.focusforce.app.ui.debug.GalleryScreen
import com.focusforce.app.ui.main.MainScaffold
import com.focusforce.app.ui.onboarding.OnboardingFlow
import com.focusforce.app.ui.permissions.PermissionStatusScreen
import kotlinx.serialization.Serializable

/** Type-safe routes (Navigation 2.8+). */
@Serializable data object Onboarding
@Serializable data object Main
@Serializable data object PermissionStatus
@Serializable data object Gallery

@Composable
fun FocusNavHost(onboardingComplete: Boolean, appViewModel: AppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    // Decided once at first composition; later changes to the flag must not re-route a running session.
    val start: Any = remember { if (onboardingComplete) Main else Onboarding }

    NavHost(navController = navController, startDestination = start) {
        composable<Onboarding> {
            OnboardingFlow { hero, goals, minutes ->
                appViewModel.completeOnboarding(hero, goals, minutes)
                navController.navigate(Main) { popUpTo<Onboarding> { inclusive = true } }
            }
        }
        composable<Main> {
            MainScaffold(
                onOpenPermissions = { navController.navigate(PermissionStatus) },
                onOpenGallery = { navController.navigate(Gallery) },
            )
        }
        composable<PermissionStatus> { PermissionStatusScreen(onBack = { navController.popBackStack() }) }
        composable<Gallery> { GalleryScreen(onBack = { navController.popBackStack() }) }
    }
}
