package io.github.hdlee73.insu

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.hdlee73.insu.ui.InsuNavHost
import io.github.hdlee73.insu.ui.theme.InsuTheme
import kotlinx.coroutines.launch

/** BiometricPrompt가 FragmentActivity를 요구하므로 ComponentActivity 대신 쓴다. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as InsuApp).container.settings
        // 첫 화면이 그려지기 전에 캡처 차단을 걸어 둔다.
        if (settings.state.value.secureScreen) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                settings.state.collect { s ->
                    if (s.secureScreen) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
        setContent {
            InsuTheme { InsuNavHost() }
        }
    }
}
