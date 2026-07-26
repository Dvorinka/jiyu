package com.haise.jiyu.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haise.jiyu.R
import com.haise.jiyu.data.db.entity.ChapterEntity
import com.haise.jiyu.source.LanguageMap
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowBack
import compose.icons.tablericons.ArrowRight
import compose.icons.tablericons.Check
import compose.icons.tablericons.Eye
import compose.icons.tablericons.EyeOff
import compose.icons.tablericons.Language
import compose.icons.tablericons.LayoutRows
import compose.icons.tablericons.ListCheck
import compose.icons.tablericons.Moon
import compose.icons.tablericons.Sun
import compose.icons.tablericons.WifiOff
import compose.icons.tablericons.X

// ── Horní lišta čtečky (kapitola, postup, akce) ──────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    chapterTitle: String,
    currentPage: Int,
    pageCount: Int,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    isOfflineChapter: Boolean,
    sessionElapsed: Long,
    panelMode: Boolean,
    incognitoMode: Boolean,
    translateMode: Boolean,
    isTranslating: Boolean,
    chapterProgress: Float,
    allChapters: List<ChapterEntity>,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    onTogglePanelMode: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onJumpToChapter: (String) -> Unit,
    onToggleIncognito: () -> Unit,
    onToggleTranslate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Předchozí kapitola
            IconButton(
                onClick = onNavigatePrev,
                enabled = hasPrevChapter,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    TablerIcons.ArrowBack,
                    contentDescription = stringResource(R.string.reader_prev_chapter_desc),
                    tint = if (hasPrevChapter) Color.White else Color.White.copy(alpha = 0.25f),
                )
            }

            // Název + stránka
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chapterTitle,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isOfflineChapter) {
                        Icon(
                            TablerIcons.WifiOff,
                            contentDescription = stringResource(R.string.reader_offline_desc),
                            tint = Color(0xFF4FC3F7),
                            modifier = Modifier.size(13.dp).padding(start = 4.dp),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${currentPage + 1} / $pageCount",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                    val sessionMinutes = sessionElapsed / 60000
                    val sessionSeconds = (sessionElapsed % 60000) / 1000
                    if (sessionMinutes > 0) {
                        Text(
                            text = "· ${sessionMinutes}:${sessionSeconds.toString().padStart(2, '0')}",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            // Panel mode toggle (#38)
            IconButton(onClick = onTogglePanelMode, modifier = Modifier.size(40.dp)) {
                Icon(
                    TablerIcons.LayoutRows,
                    contentDescription = stringResource(R.string.reader_panel_mode_desc),
                    tint = if (panelMode) Color(0xFFCE93D8) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // Sleep timer (#42)
            IconButton(onClick = onSleepTimerClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    TablerIcons.Moon,
                    contentDescription = stringResource(R.string.reader_sleep_timer_title),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // Chapter picker
            if (allChapters.isNotEmpty()) {
                var showChapterSheet by remember { mutableStateOf(false) }
                IconButton(onClick = { showChapterSheet = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        TablerIcons.ListCheck,
                        contentDescription = stringResource(R.string.reader_pick_chapter_desc),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (showChapterSheet) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { showChapterSheet = false },
                        sheetState = sheetState,
                        containerColor = Color(0xFF111B35),
                    ) {
                        Text(
                            text = stringResource(R.string.reader_chapters_sheet_title, allChapters.size),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                        ) {
                            items(allChapters, key = { it.id }) { chapter ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onJumpToChapter(chapter.id); showChapterSheet = false }
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chapter.name,
                                            color = if (chapter.read) Color.White.copy(alpha = 0.45f) else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (chapter.read) {
                                        Icon(
                                            TablerIcons.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF8B5CF6).copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            }

            // Incognito mode
            IconButton(onClick = onToggleIncognito, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (incognitoMode) TablerIcons.EyeOff else TablerIcons.Eye,
                    contentDescription = stringResource(if (incognitoMode) R.string.reader_incognito_off_desc else R.string.reader_incognito_on_desc),
                    tint = if (incognitoMode) Color(0xFFCE93D8) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // Překlad
            IconButton(onClick = onToggleTranslate, modifier = Modifier.size(40.dp)) {
                Icon(
                    TablerIcons.Language,
                    contentDescription = stringResource(when {
                        isTranslating -> R.string.reader_stop_translation_desc
                        translateMode -> R.string.reader_hide_translation_desc
                        else          -> R.string.reader_translate_chapter_action_desc
                    }),
                    tint = when {
                        isTranslating -> Color(0xFFFFB74D)
                        translateMode -> Color(0xFF4FC3F7)
                        else          -> Color.White
                    },
                )
            }

            // Další kapitola
            IconButton(
                onClick = onNavigateNext,
                enabled = hasNextChapter,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    TablerIcons.ArrowRight,
                    contentDescription = stringResource(R.string.reader_next_chapter_desc),
                    tint = if (hasNextChapter) Color.White else Color.White.copy(alpha = 0.25f),
                )
            }
        }
        // Postup v rámci manga (počet kapitol)
        if (chapterProgress > 0f) {
            LinearProgressIndicator(
                progress = { chapterProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color(0xFF8B5CF6).copy(alpha = 0.6f),
                trackColor = Color.Transparent,
            )
        }
    }
}

// ── Spodní panel (jazyky, jas, scrubber, orientace, hromadný překlad) ────────

@Composable
fun ReaderBottomPanel(
    sourceLanguage: String,
    targetLanguage: String,
    onSourceLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onShowGlossary: () -> Unit,
    pageCount: Int,
    currentPage: Int,
    onJumpToPage: (Int) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    readerOrientation: String,
    onSetReaderOrientation: (String) -> Unit,
    translateMode: Boolean,
    batchTranslating: Boolean,
    batchProgress: TranslationProgress?,
    showOriginal: Boolean,
    onToggleShowOriginal: () -> Unit,
    onTranslateAll: () -> Unit,
    onCancelBatch: () -> Unit,
    translationProgress: TranslationProgress?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.75f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        // Výběr zdrojového a cílového jazyka překladu
        var showSourceMenu by remember { mutableStateOf(false) }
        var showTargetMenu by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.reader_translation_label), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Box {
                Text(
                    text = sourceLanguage,
                    color = Color(0xFF4FC3F7),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { showSourceMenu = true }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                DropdownMenu(
                    expanded = showSourceMenu,
                    onDismissRequest = { showSourceMenu = false },
                    modifier = Modifier.background(Color(0xFF1A2340)),
                ) {
                    LanguageMap.displayNames.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang, color = if (lang == sourceLanguage) Color(0xFF4FC3F7) else Color.White, fontSize = 13.sp) },
                            onClick = { onSourceLanguageChange(lang); showSourceMenu = false },
                        )
                    }
                }
            }
            Text(" → ", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            Box {
                Text(
                    text = targetLanguage,
                    color = Color(0xFF81C784),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { showTargetMenu = true }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                DropdownMenu(
                    expanded = showTargetMenu,
                    onDismissRequest = { showTargetMenu = false },
                    modifier = Modifier.background(Color(0xFF1A2340)),
                ) {
                    LanguageMap.displayNames.filter { it != "Auto" }.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang, color = if (lang == targetLanguage) Color(0xFF81C784) else Color.White, fontSize = 13.sp) },
                            onClick = { onTargetLanguageChange(lang); showTargetMenu = false },
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.reader_glossary_button),
                color = Color(0xFF8B5CF6),
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onShowGlossary)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        // Page scrubber
        if (pageCount > 1) {
            var sliderPage by remember(currentPage) { mutableStateOf(currentPage.toFloat()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${(sliderPage + 1).toInt()}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                )
                Slider(
                    value = sliderPage,
                    onValueChange = { sliderPage = it },
                    onValueChangeFinished = { onJumpToPage(sliderPage.toInt()) },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    steps = 0,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF8B5CF6),
                        activeTrackColor = Color(0xFF8B5CF6),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                Text(
                    text = "$pageCount",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Slider jasu
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(TablerIcons.Moon, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            Slider(
                value = if (brightness < 0f) 0.5f else brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.05f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = "Jas obrazovky" },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF4FC3F7),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
            )
            Icon(TablerIcons.Sun, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
        }

        // Orientace + volume klávesy
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.reader_orientation_label), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            listOf(
                "free" to stringResource(R.string.reader_orientation_auto),
                "portrait" to stringResource(R.string.reader_orientation_portrait),
                "landscape" to stringResource(R.string.reader_orientation_landscape),
            ).forEach { (value, label) ->
                androidx.compose.material3.TextButton(
                    onClick = { onSetReaderOrientation(value) },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (readerOrientation == value) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.45f),
                    ),
                ) { Text(label, fontSize = 11.sp) }
            }
        }

        // Hromadný překlad — tlačítko + progress + přepínač originál/překlad
        if (translateMode && !batchTranslating) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reader_translation_word), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = !showOriginal,
                        onCheckedChange = { onToggleShowOriginal() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4FC3F7),
                            checkedTrackColor = Color(0xFF4FC3F7).copy(alpha = 0.4f),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.reader_original_toggle), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }

        if (batchTranslating) {
            batchProgress?.let { progress ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reader_translate_all_progress, progress.done, progress.total), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCancelBatch, modifier = Modifier.size(28.dp)) {
                        Icon(TablerIcons.X, contentDescription = stringResource(R.string.common_cancel), tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                    }
                }
                LinearProgressIndicator(
                    progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    color = Color(0xFFFFB74D),
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        } else if (!translateMode) {
            OutlinedButton(
                onClick = onTranslateAll,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.6f)),
            ) {
                Icon(TablerIcons.Language, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(16.dp).padding(end = 4.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.reader_translate_all_button), color = Color(0xFF4FC3F7), fontSize = 13.sp)
            }
        }

        // Progress překladu aktuální stránky (pokud aktivní)
        if (translationProgress != null) {
            translationProgress.let { progress ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reader_translating_progress, progress.done, progress.total), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text("${(progress.done * 100f / progress.total).toInt()} %", color = Color(0xFF4FC3F7), style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(progress = { progress.done.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Color(0xFF4FC3F7), trackColor = Color.White.copy(alpha = 0.2f))
            }
        }
    }
}
