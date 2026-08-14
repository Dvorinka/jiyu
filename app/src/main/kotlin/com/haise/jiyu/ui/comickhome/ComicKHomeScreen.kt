package com.haise.jiyu.ui.comickhome

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.haise.jiyu.source.comick.ChapterUpdate
import com.haise.jiyu.source.comick.ReviewItem
import com.haise.jiyu.source.comick.TopFeed
import com.haise.jiyu.ui.components.JiyuLoadingIndicator
import com.haise.jiyu.ui.theme.GlowCyan
import com.haise.jiyu.ui.theme.TextPrimary
import com.haise.jiyu.ui.theme.TextSecondary
import com.haise.jiyu.ui.theme.Violet
import com.haise.jiyu.ui.theme.screenGradient
import compose.icons.TablerIcons
import compose.icons.tablericons.Book
import compose.icons.tablericons.Flame
import compose.icons.tablericons.Search
import compose.icons.tablericons.Sun
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ComicKHomeScreen(
    onOpenManga: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSection: (section: String, window: String?, title: String) -> Unit,
    viewModel: ComicKHomeViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsState()
    val topFeed by viewModel.topFeed.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()
    val popularNewWindow by viewModel.popularNewWindow.collectAsState()
    val mostRecentPopularWindow by viewModel.mostRecentPopularWindow.collectAsState()
    val openingManga by viewModel.openingManga.collectAsState()
    val openError by viewModel.openError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(openError) {
        openError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOpenError()
        }
    }

    fun openManga(manga: SManga) {
        viewModel.openManga(manga, onOpenManga)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ComicK", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onOpenSearch) {
                        Icon(TablerIcons.Search, contentDescription = stringResource(R.string.common_search), tint = TextSecondary)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(HomeTab.HOME to stringResource(R.string.comick_home_tab_home), HomeTab.UPDATES to stringResource(R.string.comick_home_tab_updates)).forEach { (t, label) ->
                        val selected = tab == t
                        Button(
                            onClick = { if (!selected) viewModel.setTab(t) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Violet.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (selected) Violet else TextSecondary,
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Violet.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.15f)),
                            elevation = null,
                        ) { Text(label, fontSize = 13.sp) }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().background(screenGradient).padding(innerPadding),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        JiyuLoadingIndicator()
                        Text(stringResource(R.string.comick_home_loading), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(stringResource(R.string.comick_home_load_failed), color = TextSecondary, fontSize = 14.sp)
                        Text(error ?: "", color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                        OutlinedButton(onClick = { viewModel.retry() }) { Text(stringResource(R.string.common_retry), color = Violet) }
                    }
                }
                tab == HomeTab.HOME -> {
                    val feed = topFeed
                    if (feed == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.comick_home_empty), color = TextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp + navBottom)) {
                            item {
                                val recentlyAddedLabel = stringResource(R.string.comick_home_recently_added)
                                val completedLabel = stringResource(R.string.comick_home_completed)
                                ToggleSection(
                                    leftLabel = recentlyAddedLabel,
                                    rightLabel = completedLabel,
                                    rightSelected = showCompleted,
                                    onToggle = { viewModel.setShowCompleted(it) },
                                    comics = (if (showCompleted) feed.completed else feed.recentlyAdded).take(15),
                                    onOpenManga = ::openManga,
                                    onViewAll = { onOpenSection(if (showCompleted) "completed" else "recently_added", null, if (showCompleted) completedLabel else recentlyAddedLabel) },
                                )
                            }
                            item {
                                val label = stringResource(R.string.comick_home_popular_new)
                                WindowSection(
                                    title = label,
                                    window = popularNewWindow,
                                    onWindowChange = { viewModel.setPopularNewWindow(it) },
                                    comics = feed.popularNew[popularNewWindow].orEmpty().take(15),
                                    onOpenManga = ::openManga,
                                    onViewAll = { onOpenSection("popular_new", popularNewWindow, label) },
                                )
                            }
                            item {
                                val label = stringResource(R.string.comick_home_most_recent_popular)
                                WindowSection(
                                    title = label,
                                    window = mostRecentPopularWindow,
                                    onWindowChange = { viewModel.setMostRecentPopularWindow(it) },
                                    comics = feed.mostRecentPopular[mostRecentPopularWindow].orEmpty().take(15),
                                    onOpenManga = ::openManga,
                                    onViewAll = { onOpenSection("most_recent_popular", mostRecentPopularWindow, label) },
                                )
                            }
                            item {
                                val label = stringResource(R.string.comick_home_recent_reviews)
                                ReviewSection(
                                    reviews = feed.recentReviews.take(15),
                                    onOpenManga = ::openManga,
                                    onViewAll = { onOpenSection("recent_reviews", null, label) },
                                )
                            }
                            item {
                                val updates by viewModel.updates.collectAsState()
                                val updatesOrder by viewModel.updatesOrder.collectAsState()
                                UpdatesPreviewSection(
                                    updates = updates.take(6),
                                    order = updatesOrder,
                                    onOrderChange = { viewModel.setUpdatesOrder(it) },
                                    onOpenManga = ::openManga,
                                    onViewAll = { viewModel.setTab(HomeTab.UPDATES) },
                                )
                            }
                        }
                    }
                }
                tab == HomeTab.UPDATES -> {
                    val updates by viewModel.updates.collectAsState()
                    val updatesOrder by viewModel.updatesOrder.collectAsState()
                    val updatesLoading by viewModel.updatesLoading.collectAsState()
                    val updatesError by viewModel.updatesError.collectAsState()
                    val listState = rememberLazyListState()

                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            lastVisible >= totalItems - 5 && totalItems > 0
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && !updatesLoading) viewModel.loadMoreUpdates()
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("hot" to stringResource(R.string.source_browse_popular), "new" to stringResource(R.string.source_browse_latest)).forEach { (value, label) ->
                                val selected = updatesOrder == value
                                Button(
                                    onClick = { if (!selected) viewModel.setUpdatesOrder(value) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selected) Violet.copy(alpha = 0.2f) else Color.Transparent,
                                        contentColor = if (selected) Violet else TextSecondary,
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Violet.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.15f)),
                                    elevation = null,
                                ) { Text(label, fontSize = 13.sp) }
                            }
                        }
                        if (updatesError != null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                                    Text(stringResource(R.string.comick_home_load_failed), color = TextSecondary, fontSize = 14.sp)
                                    Text(updatesError ?: "", color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                                    OutlinedButton(onClick = { viewModel.retry() }) { Text(stringResource(R.string.common_retry), color = Violet) }
                                }
                            }
                        } else if (updates.isEmpty() && !updatesLoading) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.comick_home_empty), color = TextSecondary, fontSize = 14.sp)
                            }
                        } else {
                            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp).let {
                                    PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp + navBottom)
                                },
                            ) {
                                items(updates, key = { it.chapter.sourceId + it.chapter.url }) { update ->
                                    UpdateRow(update = update, onClick = { openManga(update.comic) })
                                }
                                if (updatesLoading) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            JiyuLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (openingManga != null) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    JiyuLoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.comick_home_view_all),
            color = Violet,
            fontSize = 12.sp,
            modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onViewAll() }) },
        )
    }
}

@Composable
private fun ToggleSection(
    leftLabel: String,
    rightLabel: String,
    rightSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    comics: List<SManga>,
    onOpenManga: (SManga) -> Unit,
    onViewAll: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(
                    leftLabel, color = if (!rightSelected) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onToggle(false) }) },
                )
                Text("/", color = TextSecondary, fontSize = 16.sp)
                Text(
                    rightLabel, color = if (rightSelected) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onToggle(true) }) },
                )
            }
            Text(
                stringResource(R.string.comick_home_view_all), color = Violet, fontSize = 12.sp,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onViewAll() }) },
            )
        }
        MangaRow(comics, onOpenManga)
    }
}

@Composable
private fun WindowSection(
    title: String,
    window: String,
    onWindowChange: (String) -> Unit,
    comics: List<SManga>,
    onOpenManga: (SManga) -> Unit,
    onViewAll: () -> Unit,
) {
    Column {
        SectionHeader(title, onViewAll)
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("7" to R.string.comick_home_window_7d, "30" to R.string.comick_home_window_30d, "90" to R.string.comick_home_window_90d).forEach { (value, labelRes) ->
                val selected = window == value
                Text(
                    stringResource(labelRes),
                    color = if (selected) Violet else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Violet.copy(alpha = 0.15f) else Color.Transparent)
                        .pointerInput(value) { detectTapGestures(onTap = { onWindowChange(value) }) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        MangaRow(comics, onOpenManga)
    }
}

@Composable
private fun MangaRow(comics: List<SManga>, onOpenManga: (SManga) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(comics, key = { it.sourceId + it.url }) { manga ->
            ComicKMangaCard(manga = manga, onClick = { onOpenManga(manga) })
        }
    }
}

@Composable
private fun ReviewSection(reviews: List<ReviewItem>, onOpenManga: (SManga) -> Unit, onViewAll: () -> Unit) {
    Column {
        SectionHeader(stringResource(R.string.comick_home_recent_reviews), onViewAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(reviews, key = { it.comic.sourceId + it.comic.url + it.content.hashCode() }) { review ->
                ReviewCard(review = review, onClick = { onOpenManga(review.comic) })
            }
        }
    }
}

/**
 * Krátký náhled Aktualizací přímo na Domů (uživatelský požadavek - dřív šlo
 * vidět jen po přepnutí na záložku Aktualizace). Hot/New přepínač sdílí stejný
 * [ComicKHomeViewModel.updates]/[ComicKHomeViewModel.updatesOrder] stav jako
 * plná záložka - "Zobrazit vše" na ni jen přepne, nenačítá nic znovu.
 */
@Composable
private fun UpdatesPreviewSection(
    updates: List<ChapterUpdate>,
    order: String,
    onOrderChange: (String) -> Unit,
    onOpenManga: (SManga) -> Unit,
    onViewAll: () -> Unit,
) {
    Column {
        SectionHeader(stringResource(R.string.comick_home_tab_updates), onViewAll)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpdatesOrderChip(
                label = stringResource(R.string.source_browse_popular),
                icon = TablerIcons.Flame,
                selectedColor = Color(0xFFF97316),
                selected = order == "hot",
                onClick = { onOrderChange("hot") },
            )
            UpdatesOrderChip(
                label = stringResource(R.string.source_browse_latest),
                icon = TablerIcons.Sun,
                selectedColor = Violet,
                selected = order == "new",
                onClick = { onOrderChange("new") },
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (updates.isEmpty()) {
                Text(
                    stringResource(R.string.comick_home_empty),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                updates.forEach { update ->
                    UpdateRow(update = update, onClick = { onOpenManga(update.comic) })
                }
            }
        }
    }
}

@Composable
private fun UpdatesOrderChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (selected) selectedColor.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) selectedColor.copy(alpha = 0.5f) else TextSecondary.copy(alpha = 0.15f), RoundedCornerShape(50.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) selectedColor else TextSecondary, modifier = Modifier.size(14.dp))
        Text(
            label,
            color = if (selected) selectedColor else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun ReviewCard(review: ReviewItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GlowCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
                ) {
                    SubcomposeAsyncImage(
                        model = review.comic.coverUrl,
                        contentDescription = review.comic.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val state = painter.state
                        if (review.comic.coverUrl.isNullOrBlank() || state is AsyncImagePainter.State.Error) {
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1526)))
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                }
                Text(
                    review.comic.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (review.title != null) {
                Text(review.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
            Text(review.content, color = TextSecondary, fontSize = 11.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            if (review.authorName != null) {
                Text(stringResource(R.string.comick_home_review_by, review.authorName), color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Stejný vizuální střih jako `GroupScreen.GroupTitleCard`/`SourceBrowseScreen.BrowseMangaCard`, jen fixní šířka pro LazyRow místo mřížky (karty se v kódu nesdílí mezi soubory, zavedená konvence). */
@Composable
internal fun ComicKMangaCard(manga: SManga, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "comick_manga_card_scale",
    )

    Box(
        modifier = Modifier
            .width(110.dp)
            .aspectRatio(0.74f)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GlowCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() },
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
                    Icon(TablerIcons.Book, contentDescription = null, tint = TextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                }
            } else {
                SubcomposeAsyncImageContent()
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xEA070B14)))),
        )
        Text(
            text = manga.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun UpdateRow(update: ChapterUpdate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) {
            SubcomposeAsyncImage(
                model = update.comic.coverUrl,
                contentDescription = update.comic.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                val state = painter.state
                if (update.comic.coverUrl.isNullOrBlank() || state is AsyncImagePainter.State.Error) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1526)))
                } else {
                    SubcomposeAsyncImageContent()
                }
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(update.comic.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(update.chapter.name, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val groupName = update.chapter.scanlationGroup
            if (!groupName.isNullOrBlank()) {
                Text(groupName, color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (update.chapter.dateUpload > 0L) {
                Text(
                    SimpleDateFormat("d. M.", Locale.getDefault()).format(Date(update.chapter.dateUpload)),
                    color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${update.upCount}▲", color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                Text("${update.commentCount}💬", color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}
