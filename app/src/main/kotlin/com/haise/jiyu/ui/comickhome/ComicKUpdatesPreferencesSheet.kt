package com.haise.jiyu.ui.comickhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haise.jiyu.R
import com.haise.jiyu.ui.theme.CardBorder
import com.haise.jiyu.ui.theme.TextSecondary
import com.haise.jiyu.ui.theme.Violet
import compose.icons.TablerIcons
import compose.icons.tablericons.Check

/**
 * "Preferences" pro Aktualizace, stejné 3 sekce, které má ComicK vlastní Preferences
 * stránka (Type, Demographic, Mature Content) - "Display comics in my list" a
 * "countdown timers" appka nemá (vázané na ComicK účet/premium, žádná obdoba u nás).
 * Type/Demographic filtrují, co se v Aktualizacích vůbec zobrazí; Mature Content jsou
 * opt-in přepínače navíc k běžnému (safe) obsahu, ne filtr co ho skrývá.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicKUpdatesPreferencesSheet(
    initialCountries: Set<String>,
    initialDemographics: Set<String>,
    initialMatureFlags: Set<String>,
    showAdultContent: Boolean,
    onDismiss: () -> Unit,
    onApply: (countries: Set<String>, demographics: Set<String>, matureFlags: Set<String>) -> Unit,
) {
    var countries by remember { mutableStateOf(initialCountries) }
    var demographics by remember { mutableStateOf(initialDemographics) }
    var matureFlags by remember { mutableStateOf(initialMatureFlags) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111B35),
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {
            Text(
                text = stringResource(R.string.comick_prefs_title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                item { PrefsSectionLabel(stringResource(R.string.comick_prefs_type)) }
                item {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        listOf(
                            "jp" to (stringResource(R.string.browse_source_type_manga) to stringResource(R.string.comick_prefs_type_manga_desc)),
                            "kr" to (stringResource(R.string.browse_source_type_manhwa) to stringResource(R.string.comick_prefs_type_manhwa_desc)),
                            "cn" to (stringResource(R.string.browse_source_type_manhua) to stringResource(R.string.comick_prefs_type_manhua_desc)),
                            "others" to (stringResource(R.string.comick_browse_type_others) to stringResource(R.string.comick_prefs_type_others_desc)),
                        ).forEach { (value, labelAndDesc) ->
                            val (label, description) = labelAndDesc
                            PrefsCheckboxOption(label, description, value in countries) {
                                countries = if (value in countries) countries - value else countries + value
                            }
                        }
                    }
                }
                item { PrefsSectionLabel(stringResource(R.string.comick_prefs_demographic)) }
                item {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        listOf(
                            "1" to ("Shounen" to stringResource(R.string.comick_prefs_demo_shounen_desc)),
                            "2" to ("Josei" to stringResource(R.string.comick_prefs_demo_josei_desc)),
                            "3" to ("Seinen" to stringResource(R.string.comick_prefs_demo_seinen_desc)),
                            "4" to ("Shoujo" to stringResource(R.string.comick_prefs_demo_shoujo_desc)),
                            "0" to (stringResource(R.string.comick_prefs_no_demographic) to stringResource(R.string.comick_prefs_no_demographic_desc)),
                        ).forEach { (value, labelAndDesc) ->
                            val (label, description) = labelAndDesc
                            PrefsCheckboxOption(label, description, value in demographics) {
                                demographics = if (value in demographics) demographics - value else demographics + value
                            }
                        }
                    }
                }
                if (showAdultContent) {
                    item { PrefsSectionLabel(stringResource(R.string.comick_prefs_mature)) }
                    item {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            listOf(
                                "suggestive" to (stringResource(R.string.comick_prefs_mature_suggestive) to stringResource(R.string.comick_prefs_mature_suggestive_desc)),
                                "violence" to (stringResource(R.string.comick_prefs_mature_violence) to stringResource(R.string.comick_prefs_mature_violence_desc)),
                                "adult" to (stringResource(R.string.comick_prefs_mature_adult) to stringResource(R.string.comick_prefs_mature_adult_desc)),
                            ).forEach { (value, labelAndDesc) ->
                                val (label, description) = labelAndDesc
                                PrefsCheckboxOption(label, description, value in matureFlags) {
                                    matureFlags = if (value in matureFlags) matureFlags - value else matureFlags + value
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
            Button(
                onClick = { onApply(countries, demographics, matureFlags) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.comick_browse_apply))
            }
        }
    }
}

@Composable
private fun PrefsSectionLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

/** Checkbox + název + popis pod ním, stejný styl jako ComicK vlastní Preferences
 * stránka (viz screenshoty) - dřív tahle sekce používala pilulkové chipy bez
 * popisu (Type/Demographic) nebo zmáčknutý checkbox (Mature Content: `.size(20.dp)`
 * následované `.padding(end = 10.dp)` zmenšilo viditelnou kreslicí plochu o polovinu,
 * takže z čtverečku vznikl úzký proužek - uživatelský report se screenshotem). */
@Composable
private fun PrefsCheckboxOption(label: String, description: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) Violet else Color.Transparent)
                .border(1.dp, if (checked) Violet else CardBorder, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(TablerIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(text = description, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
