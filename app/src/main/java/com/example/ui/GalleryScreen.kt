package com.example.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import com.example.R
import com.example.media.CapturedMediaItem
import com.example.media.CapturedMediaRepository
import com.example.media.GalleryFilter
import com.example.media.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
  items: List<CapturedMediaItem>,
  filter: GalleryFilter,
  selectedUri: Uri?,
  isLoading: Boolean,
  onSelectFilter: (GalleryFilter) -> Unit,
  onSelectItem: (Uri?) -> Unit,
  onRefresh: () -> Unit,
  onShareItem: (CapturedMediaItem) -> Unit,
  onDeleteItem: (CapturedMediaItem) -> Unit,
  onBackToCamera: () -> Unit,
) {
  val context = LocalContext.current
  val colors = MaterialTheme.colorScheme

  val filteredItems =
    remember(items, filter) {
      when (filter) {
        GalleryFilter.ALL -> items
        GalleryFilter.PHOTOS -> items.filter { it.type == MediaType.PHOTO }
        GalleryFilter.VIDEOS -> items.filter { it.type == MediaType.VIDEO }
      }
    }

  val photoCount = remember(items) { items.count { it.type == MediaType.PHOTO } }
  val videoCount = remember(items) { items.count { it.type == MediaType.VIDEO } }

  val mediaImageLoader =
    remember(context) {
      ImageLoader.Builder(context)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
    }

  DisposableEffect(mediaImageLoader) {
    onDispose { mediaImageLoader.shutdown() }
  }

  var pendingDeleteItem by remember { mutableStateOf<CapturedMediaItem?>(null) }

  BackHandler {
    if (selectedUri != null) {
      onSelectItem(null)
    } else {
      onBackToCamera()
    }
  }

  if (selectedUri != null && filteredItems.isNotEmpty()) {
    val initialIndex =
      filteredItems.indexOfFirst { it.uri == selectedUri }.let { if (it >= 0) it else 0 }
    GalleryMediaDetailViewer(
      items = filteredItems,
      initialIndex = initialIndex,
      onActivePageChanged = { pageItem -> onSelectItem(pageItem.uri) },
      onShare = onShareItem,
      onRequestDelete = { pendingDeleteItem = it },
      onClose = { onSelectItem(null) },
    )
  } else {
    Scaffold(
      containerColor = colors.background,
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = stringResource(R.string.gallery_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
              )
              Text(
                text = "$photoCount photos • $videoCount videos",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
              )
            }
          },
          navigationIcon = {
            IconButton(
              onClick = onBackToCamera,
              modifier = Modifier.testTag("gallery_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back_to_camera),
                tint = colors.onSurface,
              )
            }
          },
          actions = {
            IconButton(
              onClick = onRefresh,
              modifier = Modifier.testTag("gallery_refresh_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.cd_refresh_gallery),
                tint = colors.primary,
              )
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = colors.surface,
              titleContentColor = colors.onSurface,
            ),
        )
      },
    ) { innerPadding ->
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.TopCenter,
      ) {
        Column(
          modifier = Modifier.fillMaxSize().widthIn(max = 1000.dp),
        ) {
          // Filter bar: All / Photos / Videos
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            GalleryFilterChip(
              label = "${stringResource(R.string.gallery_filter_all)} (${items.size})",
              selected = filter == GalleryFilter.ALL,
              onClick = { onSelectFilter(GalleryFilter.ALL) },
              testTag = "gallery_filter_all",
            )
            GalleryFilterChip(
              label = "${stringResource(R.string.gallery_filter_photos)} ($photoCount)",
              selected = filter == GalleryFilter.PHOTOS,
              onClick = { onSelectFilter(GalleryFilter.PHOTOS) },
              testTag = "gallery_filter_photos",
            )
            GalleryFilterChip(
              label = "${stringResource(R.string.gallery_filter_videos)} ($videoCount)",
              selected = filter == GalleryFilter.VIDEOS,
              onClick = { onSelectFilter(GalleryFilter.VIDEOS) },
              testTag = "gallery_filter_videos",
            )
          }

          when {
            isLoading && filteredItems.isEmpty() -> {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
              }
            }
            filteredItems.isEmpty() -> {
              GalleryEmptyState(onBackToCamera = onBackToCamera)
            }
            else -> {
              LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 114.dp),
                modifier = Modifier.fillMaxSize().testTag("gallery_grid"),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                itemsIndexed(
                  items = filteredItems,
                  key = { _, item -> "${item.type.name}_${item.uri}" },
                ) { index, item ->
                  GalleryGridTile(
                    item = item,
                    imageLoader = mediaImageLoader,
                    onClick = { onSelectItem(item.uri) },
                    modifier = Modifier.testTag("gallery_item_$index"),
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  pendingDeleteItem?.let { target ->
    AlertDialog(
      onDismissRequest = { pendingDeleteItem = null },
      containerColor = colors.surface,
      titleContentColor = colors.onSurface,
      textContentColor = colors.onSurfaceVariant,
      title = { Text(stringResource(R.string.delete_confirm_title)) },
      text = { Text(stringResource(R.string.delete_confirm_message)) },
      confirmButton = {
        TextButton(
          onClick = {
            pendingDeleteItem = null
            onDeleteItem(target)
          },
          modifier = Modifier.testTag("confirm_delete_button"),
        ) {
          Text(
            text = stringResource(R.string.delete_action),
            color = colors.error,
            fontWeight = FontWeight.Bold,
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteItem = null }) {
          Text(stringResource(R.string.cancel_action), color = colors.onSurfaceVariant)
        }
      },
    )
  }
}

@Composable
private fun GalleryFilterChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val colors = MaterialTheme.colorScheme
  FilterChip(
    selected = selected,
    onClick = onClick,
    modifier = Modifier.testTag(testTag),
    label = {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
      )
    },
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.primary.copy(alpha = 0.18f),
        selectedLabelColor = colors.primary,
        labelColor = colors.onSurfaceVariant,
      ),
    border =
      FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = colors.onSurface.copy(alpha = 0.16f),
        selectedBorderColor = colors.primary,
      ),
  )
}

@Composable
private fun GalleryGridTile(
  item: CapturedMediaItem,
  imageLoader: ImageLoader,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val dateLabel =
    remember(item.dateAddedMillis) {
      if (item.dateAddedMillis > 0L) {
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.dateAddedMillis))
      } else {
        ""
      }
    }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color = colors.surfaceVariant,
    border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.1f)),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      AsyncImage(
        model = item.uri,
        imageLoader = imageLoader,
        contentDescription = item.displayName,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )

      if (item.type == MediaType.VIDEO) {
        Surface(
          shape = CircleShape,
          color = Color.Black.copy(alpha = 0.58f),
          border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
          modifier = Modifier.align(Alignment.Center).size(38.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.PlayArrow,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp),
            )
          }
        }

        if (item.durationMillis > 0L) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.72f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Videocam,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp),
              )
              Text(
                text = CapturedMediaRepository.formatDuration(item.durationMillis),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      if (dateLabel.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = Color.Black.copy(alpha = 0.62f),
          modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
        ) {
          Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun GalleryEmptyState(
  onBackToCamera: () -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = colors.surface,
      border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)),
      modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = colors.primary.copy(alpha = 0.14f),
          modifier = Modifier.size(64.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.PhotoLibrary,
              contentDescription = null,
              tint = colors.primary,
              modifier = Modifier.size(30.dp),
            )
          }
        }
        Text(
          text = stringResource(R.string.gallery_empty_title),
          style = MaterialTheme.typography.titleLarge,
          color = colors.onSurface,
          textAlign = TextAlign.Center,
        )
        Text(
          text = stringResource(R.string.gallery_empty_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
          onClick = onBackToCamera,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("empty_back_to_camera_button"),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = colors.primary,
              contentColor = colors.onPrimary,
            ),
        ) {
          Icon(
            imageVector = Icons.Rounded.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(text = stringResource(R.string.retake), fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun GalleryMediaDetailViewer(
  items: List<CapturedMediaItem>,
  initialIndex: Int,
  onActivePageChanged: (CapturedMediaItem) -> Unit,
  onShare: (CapturedMediaItem) -> Unit,
  onRequestDelete: (CapturedMediaItem) -> Unit,
  onClose: () -> Unit,
) {
  val pagerState =
    rememberPagerState(
      initialPage = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
      pageCount = { items.size },
    )

  LaunchedEffect(pagerState.currentPage, items) {
    items.getOrNull(pagerState.currentPage)?.let(onActivePageChanged)
  }

  val currentItem = items.getOrNull(pagerState.currentPage) ?: return
  val formattedDate =
    remember(currentItem.dateAddedMillis) {
      if (currentItem.dateAddedMillis > 0L) {
        SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault())
          .format(Date(currentItem.dateAddedMillis))
      } else {
        currentItem.displayName
      }
    }

  Box(
    modifier = Modifier.fillMaxSize().background(Color.Black).testTag("gallery_detail_viewer"),
  ) {
    HorizontalPager(
      state = pagerState,
      modifier = Modifier.fillMaxSize(),
    ) { page ->
      val item = items[page]
      val isActive = page == pagerState.currentPage
      if (item.type == MediaType.PHOTO) {
        ZoomablePhotoPage(uri = item.uri, contentDescription = item.displayName)
      } else {
        VideoPlayerPage(uri = item.uri, isActive = isActive)
      }
    }

    // Top overlay action bar
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .align(Alignment.TopCenter)
          .background(Color.Black.copy(alpha = 0.52f))
          .statusBarsPadding()
          .padding(horizontal = 8.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      IconButton(
        onClick = onClose,
        modifier = Modifier.testTag("viewer_close_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = stringResource(R.string.close),
          tint = Color.White,
        )
      }

      Column(
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
      ) {
        Text(
          text = formattedDate,
          style = MaterialTheme.typography.titleMedium,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${pagerState.currentPage + 1} / ${items.size} • ${currentItem.displayName}",
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.72f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = { onShare(currentItem) },
          modifier = Modifier.testTag("viewer_share_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = stringResource(R.string.cd_share_media),
            tint = Color.White,
          )
        }
        IconButton(
          onClick = { onRequestDelete(currentItem) },
          modifier = Modifier.testTag("viewer_delete_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete_media),
            tint = Color(0xFFFCA5A5),
          )
        }
      }
    }

    // Bottom hint pill for swipe navigation
    if (items.size > 1) {
      Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.56f),
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 18.dp),
      ) {
        Text(
          text = "${pagerState.currentPage + 1} / ${items.size}",
          style = MaterialTheme.typography.labelMedium,
          color = Color.White,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
      }
    }
  }
}

@Composable
private fun ZoomablePhotoPage(
  uri: Uri,
  contentDescription: String,
) {
  var scale by remember(uri) { mutableFloatStateOf(1f) }
  var offset by remember(uri) { mutableStateOf(Offset.Zero) }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .pointerInput(uri) {
          detectTapGestures(
            onDoubleTap = {
              if (scale > 1.2f) {
                scale = 1f
                offset = Offset.Zero
              } else {
                scale = 2.4f
              }
            }
          )
        }
        .pointerInput(uri) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
              val event = awaitPointerEvent()
              val zoomChange = event.calculateZoom()
              val panChange = event.calculatePan()
              val isZooming = zoomChange != 1f || event.changes.size > 1
              if (isZooming || scale > 1.05f) {
                val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
                scale = nextScale
                offset =
                  if (nextScale > 1.05f) {
                    offset + panChange
                  } else {
                    Offset.Zero
                  }
                event.changes.forEach { change ->
                  if (change.pressed) {
                    change.consume()
                  }
                }
              }
            } while (event.changes.any { it.pressed })
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    AsyncImage(
      model = uri,
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier =
        Modifier.fillMaxSize()
          .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y,
          ),
    )
  }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerPage(
  uri: Uri,
  isActive: Boolean,
) {
  val context = LocalContext.current
  val exoPlayer =
    remember(uri) {
      ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(uri))
        prepare()
      }
    }

  LaunchedEffect(isActive, exoPlayer) {
    exoPlayer.playWhenReady = isActive
  }

  DisposableEffect(exoPlayer) {
    onDispose {
      exoPlayer.release()
    }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    AndroidView(
      factory = { ctx ->
        PlayerView(ctx).apply {
          player = exoPlayer
          useController = true
          layoutParams =
            FrameLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
      },
      update = { playerView ->
        if (playerView.player !== exoPlayer) {
          playerView.player = exoPlayer
        }
      },
      modifier = Modifier.fillMaxSize(),
    )
  }
}
