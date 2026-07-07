package me.vripper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.vripper.api.EndpointApi
import me.vripper.data.Image as ImageModel

private sealed interface ViewerState {
    data object Loading : ViewerState
    data object Missing : ViewerState
    data class Failed(val message: String) : ViewerState
    data class Ready(val bitmap: ImageBitmap) : ViewerState
}

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

    val scope = rememberCoroutineScope()
    val startIndex = initialIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }
    val current = images.getOrNull(pagerState.currentPage) ?: images.first()
    val cache = remember { mutableStateMapOf<Long, ImageBitmap>() }

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
            ) { page ->
                ImagePage(image = images[page], api = api, cache = cache)
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
private fun ImagePage(
    image: ImageModel,
    api: EndpointApi,
    cache: MutableMap<Long, ImageBitmap>,
) {
    var state by remember(image.id) {
        mutableStateOf<ViewerState>(
            cache[image.id]?.let { ViewerState.Ready(it) } ?: ViewerState.Loading,
        )
    }
    var attempt by remember(image.id) { mutableStateOf(0) }

    LaunchedEffect(image.id, attempt) {
        cache[image.id]?.let {
            state = ViewerState.Ready(it)
            return@LaunchedEffect
        }
        state = ViewerState.Loading
        state = try {
            val chunks = ArrayList<ByteArray>()
            var total = 0
            var missing = false
            api.downloadImage(image.id).collect { chunk ->
                when {
                    chunk.missing -> missing = true
                    chunk.data.isNotEmpty() -> {
                        chunks += chunk.data
                        total += chunk.data.size
                    }
                }
            }
            when {
                missing -> ViewerState.Missing
                else -> {
                    val data = concat(chunks, total)
                    val bitmap = withContext(Dispatchers.Default) { data.decodeToImageBitmap() }
                    cache[image.id] = bitmap
                    ViewerState.Ready(bitmap)
                }
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            ViewerState.Failed(t.userMessage("Unable to load this image"))
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = state) {
            ViewerState.Loading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = Color.White)
                Text("Loading image…", color = Color.White)
            }

            ViewerState.Missing -> Message(
                title = "Image not available",
                description = "This image has not been downloaded or the file was removed from " +
                    "the server.",
                actionLabel = "Retry",
                onAction = { attempt++ },
            )

            is ViewerState.Failed -> Message(
                title = "Could not load the image",
                description = current.message,
                actionLabel = "Retry",
                onAction = { attempt++ },
            )

            is ViewerState.Ready -> Image(
                bitmap = current.bitmap,
                contentDescription = image.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
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

private fun concat(chunks: List<ByteArray>, total: Int): ByteArray = when (chunks.size) {
    0 -> ByteArray(0)
    1 -> chunks[0]
    else -> {
        val bytes = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }
        bytes
    }
}
