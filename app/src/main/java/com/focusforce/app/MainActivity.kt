package com.focusforce.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.AppViewModel
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.nav.FocusNavHost
import com.focusforce.app.ui.theme.FocusForceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every hero theme is dark-first, so system bar icons are always light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { FocusForceRoot() }
    }
}

@Composable
private fun FocusForceRoot(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded = settings
    FocusForceTheme(hero = loaded?.hero ?: HeroTheme.VECTOR) {
        if (loaded == null) {
            // First DataStore read is in flight; show just the themed backdrop (no flash of wrong screen).
            ThemedBackground(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
        } else {
            FocusNavHost(onboardingComplete = loaded.onboardingComplete)
        }
    }
}
