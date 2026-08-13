package com.haise.jiyu.ui.group

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.haise.jiyu.R
import com.haise.jiyu.source.SManga
import com.haise.jiyu.ui.components.JiyuLoadingIndicator
import com.haise.jiyu.ui.theme.GlowCyan
import com.haise.jiyu.ui.theme.TextPrimary
import com.haise.jiyu.ui.theme.TextSecondary
import com.haise.jiyu.ui.theme.Violet
import com.haise.jiyu.ui.theme.screenGradient
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Book

@Composable
fun GroupScreen(
    onBack: () -> Unit,
    onOpenManga: (String) -> Unit,
    viewModel: GroupViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val openingManga by viewModel.openingManga.collectAsState()
    val openError by viewModel.openError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openError) {
        openError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOpenError()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(TablerIcons.ArrowLeft, contentDescription = null, tint = TextPrimary)
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    val info = groupInfo
                    if (info != null) {
                        Text(
                            stringResource(R.string.group_screen_follow_count, info.followCount) +
                                " · " + stringResource(R.string.group_screen_chapter_count, info.chapterCount),
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(screenGradient)
                .padding(innerPadding),
        ) {
            val comics = groupInfo?.comics.orEmpty()
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        JiyuLoadingIndicator()
                        Text(stringResource(R.string.group_screen_loading), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.group_screen_load_failed), color = TextSecondary, fontSize = 14.sp)
                }
                comics.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.group_screen_no_titles), color = TextSecondary, fontSize = 14.sp)
                }
                else -> {
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp + navBottom),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(comics, key = { it.sourceId + it.url }) { manga ->
                            val isOpening = openingManga?.let { it.sourceId == manga.sourceId && it.url == manga.url } == true
                            GroupTitleCard(manga = manga, isLoading = isOpening, onClick = {
                                viewModel.openManga(manga, onOpenManga)
                            })
                        }
                    }
                }
            }
        }
    }
}

/** Stejný vizuální střih jako `SourceBrowseScreen.BrowseMangaCard` - karty se v kódu nesdílí mezi soubory (zavedená konvence). */
@Composable
private fun GroupTitleCard(manga: SManga, isLoading: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "group_card_scale",
    )

    Box(
        modifier = Modifier
            .aspectRatio(0.68f)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GlowCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { if (!isLoading) onClick() },
                )
            },
    ) {
        SubcomposeAsyncImage(
            model = manga.coverUrl,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        ) {
            val state = painter.state
            if (manga.coverUrl.isNullOrBlank() || state is AsyncImagePainter.State.Error) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1526)), contentAlignment = Alignment.Center) {
                    Icon(TablerIcons.Book, contentDescription = null, tint = TextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
                }
            } else {
                SubcomposeAsyncImageContent()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xEA070B14)))),
        )

        Text(
            text = manga.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 7.dp, vertical = 6.dp),
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                JiyuLoadingIndicator(size = 28.dp, strokeWidth = 3.dp)
            }
        }
    }
}
