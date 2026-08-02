package com.haise.jiyu.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.haise.jiyu.R
import com.haise.jiyu.data.db.entity.MangaEntity
import com.haise.jiyu.ui.theme.NightBlue
import com.haise.jiyu.ui.theme.TextSecondary
import com.haise.jiyu.ui.theme.screenGradient
import com.haise.jiyu.ui.theme.titleGradient
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowBack

/**
 * Sekce knihovny, kterou umí [LibrarySectionScreen] zobrazit celou. Název konstanty je i tím,
 * co se předává v navigační cestě, takže se nesmí přejmenovávat bez úpravy [Routes.librarySection].
 */
enum class LibrarySection(val titleRes: Int) {
    CONTINUE_READING(R.string.library_continue_reading),
    RECENTLY_ADDED(R.string.library_recently_added),
    COMPLETED(R.string.library_completed),
}

/**
 * Celá sekce knihovny v mřížce - to, co se otevře po klepnutí na "Zobrazit vše".
 *
 * Proč to vzniklo: "Zobrazit vše" na Knihovně byl obyčejný `Text` s šipkou BEZ jakéhokoli
 * `clickable`. Vypadalo to jako odkaz, chovalo se to jako výplň.
 *
 * Karusely přitom nejsou zkrácené - obsahují všechny položky sekce. Přínos téhle obrazovky
 * proto není "víc položek", ale způsob zobrazení: mřížka po třech místo vodorovného
 * posuvníku, ve kterém se u dvaceti titulů nedá nic najít.
 */
@Composable
fun LibrarySectionScreen(
    section: LibrarySection,
    onBack: () -> Unit,
    onOpenManga: (String) -> Unit,
    onOpenChapter: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val continueReading by viewModel.continueReading.collectAsState()
    val recentlyAdded   by viewModel.recentlyAdded.collectAsState()
    val completed       by viewModel.completed.collectAsState()

    val items: List<MangaEntity> = when (section) {
        LibrarySection.CONTINUE_READING -> continueReading.map { it.manga }
        LibrarySection.RECENTLY_ADDED -> recentlyAdded
        LibrarySection.COMPLETED -> completed
    }

    // U rozečtených titulů vede klepnutí rovnou do poslední kapitoly, stejně jako v karuselu
    // na Knihovně - jinak by "pokračovat ve čtení" končilo na detailu a uživatel by musel
    // kapitolu hledat sám. Bez známé poslední kapitoly zbývá detail.
    val openItem: (MangaEntity) -> Unit = { manga ->
        val chapterId = manga.lastReadChapterId
        if (section == LibrarySection.CONTINUE_READING && chapterId != null) {
            onOpenChapter(chapterId)
        } else {
            onOpenManga(manga.id)
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize().background(screenGradient)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(NightBlue, NightBlue.copy(alpha = 0f))))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(TablerIcons.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = TextSecondary)
            }
            Text(
                text = stringResource(section.titleRes),
                style = TextStyle(brush = titleGradient, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Text(
                text = "${items.size}",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.library_section_empty),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp + navBottom),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { manga ->
                    SearchResultCard(manga = manga, onClick = { openItem(manga) })
                }
            }
        }
    }
}
