package com.haise.jiyu.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haise.jiyu.R
import androidx.compose.ui.res.stringResource
import com.haise.jiyu.ui.theme.NightBlue
import com.haise.jiyu.ui.theme.TextPrimary
import com.haise.jiyu.ui.theme.TextSecondary
import com.haise.jiyu.ui.theme.Violet

/** Klíč pro položku, co odkryje volné textové pole na popis - sdíleno mezi voláními [ReportDialog]. */
const val REPORT_PROBLEM_OTHER_KEY = "other"

/**
 * Obecný report dialog (výběr problému + volitelný text) - appka nemá vlastní backend na
 * reporty, takže volající obvykle na [onSend] naváže [buildReportEmailIntent] a otevře
 * uživatelův e-mailový klient. Použito jak pro report konkrétního zdroje (BrowseScreen.kt),
 * tak pro obecný report appky (AboutSettingsScreen.kt) - liší se jen [title]/[problems]/
 * obsah e-mailu, který si sestaví volající.
 */
@Composable
fun ReportDialog(
    title: String,
    problems: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSend: (problemKey: String, details: String) -> Unit,
) {
    var selectedProblem by remember { mutableStateOf(problems.firstOrNull()?.first ?: REPORT_PROBLEM_OTHER_KEY) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column {
                problems.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { selectedProblem = key },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedProblem == key,
                            onClick = { selectedProblem = key },
                            colors = RadioButtonDefaults.colors(selectedColor = Violet, unselectedColor = TextSecondary),
                        )
                        Text(label, color = TextPrimary, fontSize = 14.sp)
                    }
                }
                if (selectedProblem == REPORT_PROBLEM_OTHER_KEY) {
                    TextField(
                        value = details,
                        onValueChange = { details = it },
                        placeholder = { Text(stringResource(R.string.browse_report_details_placeholder), color = TextSecondary, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color.White.copy(alpha = 0.06f), unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(selectedProblem, details) },
                enabled = selectedProblem != REPORT_PROBLEM_OTHER_KEY || details.isNotBlank(),
            ) { Text(stringResource(R.string.common_send), color = Violet) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = TextSecondary) }
        },
        containerColor = NightBlue,
    )
}

/**
 * Appka nemá vlastní backend na sběr reportů - report se pošle jako e-mail přes uživatelův
 * vlastní e-mailový klient (ACTION_SENDTO), ne automaticky na pozadí bez jeho vědomí. Stejný
 * vzor jako sdílení stránky v čtečce.
 */
fun buildReportEmailIntent(subject: String, body: String): Intent =
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf("biketrialradim@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
