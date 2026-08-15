package com.haise.jiyu.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import coil.compose.AsyncImage
import com.haise.jiyu.R
import com.haise.jiyu.data.db.MangaDao
import com.haise.jiyu.data.db.entity.MangaEntity
import com.haise.jiyu.ui.theme.JiyuTheme
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Book
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Konfigurace přidání [CoverWidget] na plochu - uživatelský požadavek "vybrat i cover":
 * na rozdíl od [JiyuWidget] (fixní seznam naposledy čtených) tenhle widget vždy ukazuje
 * JEDEN konkrétní titul, který si uživatel vybere tady. Android nabídku widgetu spustí
 * automaticky, jakmile uživatel widget přetáhne na plochu - viz `android:configure`
 * v cover_widget_info.xml a `APPWIDGET_CONFIGURE` intent-filter v manifestu.
 */
@AndroidEntryPoint
class CoverWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var mangaDao: MangaDao

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bez platneho ID (nekdo obrazovku otevrel jinak nez pres pridani widgetu) neni
        // co konfigurovat - Android ocekava vysledek RESULT_CANCELED, jinak widget nepridá.
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            JiyuTheme {
                ConfigScreen(
                    mangaDao = mangaDao,
                    onBack = { finish() },
                    onPick = { mangaId -> finishWithSelection(mangaId) },
                )
            }
        }
    }

    private fun finishWithSelection(mangaId: String) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { this[CoverWidget.mangaIdKey] = mangaId }
            }
            CoverWidget().update(applicationContext, glanceId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(mangaDao: MangaDao, onBack: () -> Unit, onPick: (String) -> Unit) {
    var library by remember { mutableStateOf<List<MangaEntity>?>(null) }
    LaunchedEffect(Unit) { library = mangaDao.getAllLibrary() }

    Scaffold(
        containerColor = Color(0xFF070B14),
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(TablerIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.widget_cover_config_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) { innerPadding ->
        val items = library
        when {
            items == null -> {}
            items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.widget_cover_config_empty), color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                items(items, key = { it.id }) { manga ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(manga.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(modifier = Modifier.width(48.dp).height(66.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF111B35))) {
                            if (manga.coverUrl.isNullOrBlank()) {
                                Icon(TablerIcons.Book, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.padding(14.dp))
                            } else {
                                AsyncImage(
                                    model = manga.coverUrl,
                                    contentDescription = manga.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Text(
                            text = manga.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
