package com.haise.jiyu.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.transform.Transformation
import com.haise.jiyu.R
import com.haise.jiyu.util.ScrambledImageUrl
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle

// ── Stránka s možností opětovného načtení při selhání ────────────────────────

@Composable
fun RetryableAsyncImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier.fillMaxSize(),
    cropBorders: Boolean = false,
    // Skutečná (intrinsic) velikost bitmapy v px - viz [imageDisplayRect]. Bez tohohle by
    // overlay neznal rozdíl mezi rozměrem tohohle Boxu (často fillMaxSize přes celou
    // obrazovku) a skutečně vykresleným obrázkem (letterbox mezery u contentScale jiného
    // než FillBounds), a pozice bublin by driftovaly tím víc, čím dál od okraje stránky.
    onImageSize: ((Size) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var retryTrigger by remember(url) { mutableStateOf(0) }
    var isError by remember(url) { mutableStateOf(false) }

    Box(modifier = modifier) {
        val request = remember(url, retryTrigger, cropBorders) {
            val scramble = ScrambledImageUrl.parse(url)
            val transforms = buildList<Transformation> {
                if (cropBorders) add(CropBordersTransformation())
                scramble?.let { add(TileDescrambleTransformation(it.grid, it.seed)) }
            }
            ImageRequest.Builder(context)
                .data(url)
                .apply { if (transforms.isNotEmpty()) transformations(transforms) }
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = imageModifier,
            onState = { state ->
                isError = state is AsyncImagePainter.State.Error
                if (state is AsyncImagePainter.State.Success) {
                    val painterSize = state.painter.intrinsicSize
                    if (painterSize.isSpecified && painterSize.width > 0f && painterSize.height > 0f) {
                        onImageSize?.invoke(painterSize)
                    }
                }
            },
        )
        if (isError) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(TablerIcons.AlertCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.reader_page_load_failed), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { isError = false; retryTrigger++ }) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
        }
    }
}
