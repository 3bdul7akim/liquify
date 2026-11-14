package com.hakim.liquify.catalog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // The catalog can be light or dark apart from the phone (Looks & motion), and `uiMode` is
            // in configChanges, so the bar icons follow the catalog's own choice from composition.
            CatalogApp(
                onDarkThemeChange = { dark ->
                    val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
            )
        }
        // The wallpaper covers every pixel, so the window's own background was one more full-screen
        // fill under it on every frame.
        window.setBackgroundDrawable(null)
    }
}
