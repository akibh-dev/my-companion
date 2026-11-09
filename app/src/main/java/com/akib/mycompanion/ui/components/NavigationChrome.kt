package com.akib.mycompanion.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/** Destinations report actual list position, including restored positions and fling completion. */
val LocalNavigationScrolled = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
fun rememberChromeListState(): LazyListState {
    val state = rememberLazyListState()
    val report = LocalNavigationScrolled.current
    val scrolled by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 12 }
    }
    LaunchedEffect(scrolled, report) { report(scrolled) }
    return state
}
