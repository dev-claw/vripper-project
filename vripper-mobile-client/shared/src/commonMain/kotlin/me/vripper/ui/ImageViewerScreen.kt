package me.vripper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.vripper.api.EndpointApi
import me.vripper.data.Image as ImageModel
import me.vripper.image.BoundedCache
import me.vripper.image.ImageLoadResult
import me.vripper.image.ImageLoader

private const val MAX_IMAGE_DIMENSION = 2048
private const val MAX_PREVIEW_DIMENSION = 640
private const val FULL_IMAGE_CACHE_ENTRIES = 4
private const val PREVIEW_CACHE_ENTRIES = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    images: List<ImageModel>,
    initialIndex: Int,
    api: EndpointApi,
    onBack: () -> Unit,
) {
    if (images.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    ImmersiveMode(enabled = true)

    val scope = rememberCoroutineScope()
    val startIndex = initialIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }
    val current = images.getOrNull(pagerState.currentPage) ?: images.first()

    val loader = remember(api) {
        ImageLoader(
            api = api,
            maxImageDimension = MAX_IMAGE_DIMENSION,
            maxPreviewDimension = MAX_PREVIEW_DIMENSION,
        )
    }
    val fullCache = remember { BoundedCache<Long, ImageBitmap>(FULL_IMAGE_CACHE_ENTRIES) }
    val previewCache = remember { BoundedCache<Long, ImageBitmap>(PREVIEW_CACHE_ENTRIES) }

    DisposableEffect(Unit) {
        onDispose {
            fullCache.clear()
            previewCache.clear()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(VripperIcons.Back, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = current.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (images.size > 1) {
                            Text(
                                text = "${pagerState.currentPage + 1} of ${images.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.LightGray,
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { page -> images[page].id },
                // Keep off-screen pages out of composition so their bitmaps can be released.
                beyondViewportPageCount = 0,
            ) { page ->
                val isActive = abs(page - pagerState.currentPage) <= 1
                ImagePage(
                    image = images[page],
                    loader = loader,
                    fullCache = fullCache,
                    previewCache = previewCache,
                    isActive = isActive,
                )
            }

            if (images.size > 1) {
                if (pagerState.currentPage > 0) {
                    PageButton(
                        icon = VripperIcons.ChevronLeft,
                        description = "Previous image",
                        modifier = Modifier.align(Alignment.CenterStart),
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                    )
                }
                if (pagerState.currentPage < images.lastIndex) {
                    PageButton(
                        icon = VripperIcons.ChevronRight,
                        description = "Next image",
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePage(
    image: ImageModel,
    loader: ImageLoader,
    fullCache: BoundedCache<Long, ImageBitmap>,
    previewCache: BoundedCache<Long, ImageBitmap>,
    isActive: Boolean,
) {
    var full by remember(image.id) { mutableStateOf(fullCache[image.id]) }
    var preview by remember(image.id) { mutableStateOf(previewCache[image.id]) }
    var loading by remember(image.id) { mutableStateOf(full == null) }
    var missing by remember(image.id) { mutableStateOf(false) }
    var error by remember(image.id) { mutableStateOf<String?>(null) }
    var attempt by remember(image.id) { mutableStateOf(0) }

    LaunchedEffect(image.id, attempt, isActive) {
        if (!isActive) return@LaunchedEffect
        if (full != null) {
            loading = false
            return@LaunchedEffect
        }

        loading = true
        missing = false
        error = null

        val previewJob = launch {
            if (preview == null) {
                val bitmap = runCatching { loader.loadPreview(image) }.getOrNull()
                if (bitmap != null) {
                    previewCache.put(image.id, bitmap)
                    preview = bitmap
                }
            }
        }

        try {
            when (val result = loader.loadFull(image)) {
                is ImageLoadResult.Success -> {
                    fullCache.put(image.id, result.bitmap)
                    full = result.bitmap
                }

                ImageLoadResult.Missing -> missing = true
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            error = t.userMessage("Unable to load this image")
        } finally {
            loading = false
            if (full != null) {
                previewJob.cancel()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bitmap = full
        val previewBitmap = preview
        val errorMessage = error
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = image.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            previewBitmap != null -> {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = image.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                ViewerOverlay {
                    when {
                        missing -> Message(
                            title = "Image not available",
                            description = "This image has not been downloaded or the file was " +
                                "removed from the server.",
                            actionLabel = "Retry",
                            onAction = { attempt++ },
                        )

                        errorMessage != null -> Message(
                            title = "Could not load the full image",
                            description = errorMessage,
                            actionLabel = "Retry",
                            onAction = { attempt++ },
                        )

                        else -> LoadingContent(label = "Loading full resolution…")
                    }
                }
            }

            missing -> Message(
                title = "Image not available",
                description = "This image has not been downloaded or the file was removed from " +
                    "the server.",
                actionLabel = "Retry",
                onAction = { attempt++ },
            )

            errorMessage != null -> Message(
                title = "Could not load the image",
                description = errorMessage,
                actionLabel = "Retry",
                onAction = { attempt++ },
            )

            loading -> LoadingContent(label = "Loading image…")

            else -> Unit
        }
    }
}

@Composable
private fun ViewerOverlay(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun LoadingContent(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = Color.White)
        Text(label, color = Color.White)
    }
}

@Composable
private fun PageButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(8.dp)
            .background(Color(0x66000000), shape = MaterialTheme.shapes.large),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun Message(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
