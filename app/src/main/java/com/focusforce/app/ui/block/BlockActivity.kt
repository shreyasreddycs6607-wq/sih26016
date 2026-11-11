package com.focusforce.app.ui.block

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.focusforce.app.R
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.domain.blocking.BlockReason
import com.focusforce.app.ui.components.LocalAppInfoProvider
import com.focusforce.app.ui.theme.FocusForceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The full-screen hero block screen. It runs in its own task, is not listed in Recents, and Back goes
 * home (never back into the blocked app).
 */
@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    @Inject lateinit var appInfo: AppInfoProvider
    private val viewModel: BlockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        handle(intent)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            val ui by viewModel.state.collectAsStateWithLifecycle()
            val current = ui
            if (current == null) {
                Box(Modifier.fillMaxSize().background(Color.Black))
            } else {
                FocusForceTheme(current.hero) {
                    CompositionLocalProvider(LocalAppInfoProvider provides appInfo) {
                        BlockScreen(current, onHome = ::goHome, onPass = ::usePass, onLeave = ::leave)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val reason = runCatching { BlockReason.valueOf(intent.getStringExtra(EXTRA_REASON).orEmpty()) }
            .getOrDefault(BlockReason.BLOCKED_APP)
        viewModel.setTarget(pkg, reason, intent.getStringExtra(EXTRA_DETAIL), intent.getStringExtra(EXTRA_PASS_KEY))
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    private fun leave() {
        viewModel.leave()
        finishAndRemoveTask()
    }

    private fun usePass() {
        lifecycleScope.launch {
            val ui = viewModel.state.value ?: return@launch
            if (viewModel.takePass()) {
                // A pass for a feed or a site just lets you carry on where you were; a pass for an app reopens it.
                if (ui.reason != BlockReason.SHORT_VIDEO && ui.reason != BlockReason.WEBSITE) {
                    packageManager.getLaunchIntentForPackage(ui.packageName)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                finishAndRemoveTask()
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_REASON = "reason"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_PASS_KEY = "pass_key"
    }
}
