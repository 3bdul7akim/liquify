package com.hakim.liquify.catalog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // `uiMode` is listed in configChanges, so the activity is never recreated when the
            // system flips between light and dark — which means the status bar icons have to be
            // re-applied from composition rather than once in onCreate.
            val dark = isSystemInDarkTheme()
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle(dark),
                    navigationBarStyle = systemBarStyle(dark)
                )
            }
            CatalogApp()
        }
    }
}

private fun systemBarStyle(dark: Boolean): SystemBarStyle =
    if (dark) SystemBarStyle.dark(Color.BLACK)
    else SystemBarStyle.light(Color.WHITE, Color.GRAY)
