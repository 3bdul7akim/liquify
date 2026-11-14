package com.hakim.liquify.catalog.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.hakim.liquify.catalog.R

@Composable
fun Wallpaper(modifier: Modifier = Modifier) {
    // The catalog's own light or dark, which the Looks & motion screen can set apart from the phone's.
    val darkMode = !LocalCatalogColors.current.isLight

    Image(
        painter = painterResource(if (darkMode) R.drawable.dark_blue else R.drawable.light_blue),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
