package me.vripper.ui

import androidx.compose.runtime.Composable

/**
 * Requests platform "immersive" system UI while [enabled] is true.
 *
 * Desktop and iOS are no-ops; Android hides the status and navigation bars so the
 * image viewer is truly edge to edge.
 */
@Composable
expect fun ImmersiveMode(enabled: Boolean)
