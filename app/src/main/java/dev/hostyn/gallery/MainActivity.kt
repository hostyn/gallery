package dev.hostyn.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hostyn.gallery.ui.AppNavigation
import dev.hostyn.gallery.ui.PermissionGate
import dev.hostyn.gallery.ui.theme.GalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GalleryTheme {
                PermissionGate { access -> AppNavigation(access) }
            }
        }
    }
}
