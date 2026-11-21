@file:OptIn(ExperimentalOneConfigUiApi::class)

package dev.deftu.screencapper.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import dev.deftu.screencapper.upload.SecretCipher
import dev.deftu.screencapper.upload.SecretFields
import dev.deftu.screencapper.upload.UploaderProfile
import dev.deftu.screencapper.upload.UploaderSecrets
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

internal const val REENTER_SECRET = "Re-enter this secret"

internal val urlSecretRanges: (String) -> List<IntRange> = { url -> SecretFields.urlSecrets(url).map { it.range } }

@Composable
internal fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    secretRanges: ((String) -> List<IntRange>)? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    val locked = SecretCipher.isSealed(value)
    val hasSecret = !locked && value.isNotEmpty() && (secretRanges == null || secretRanges(value).isNotEmpty())
    TextField(
        if (locked) "" else value,
        onValueChange,
        modifier,
        placeholder = if (locked) REENTER_SECRET else placeholder,
        invalid = locked,
        label = label,
        masked = secretRanges == null && !revealed && !locked,
        visualTransformation = if (secretRanges != null && !revealed) PartialMask(secretRanges) else null,
        trailing = if (hasSecret) {
            { IconButton(if (revealed) "eye-off" else "eye", if (revealed) "Hide secret" else "Show secret", { revealed = !revealed }, size = ControlSize.Compact) }
        } else {
            null
        },
    )
}

@Composable
internal fun SecretKeyValueList(
    pairs: List<Pair<String, String>>,
    keyPlaceholder: String,
    valuePlaceholder: String,
    addLabel: String,
    onChange: (List<Pair<String, String>>) -> Unit,
) {
    val spacing = OneConfigTheme.current.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        pairs.forEachIndexed { index, (key, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                TextField(key, { onChange(pairs.toMutableList().apply { set(index, it to value) }) }, Modifier.weight(1f), placeholder = keyPlaceholder, label = keyPlaceholder)
                val onValue: (String) -> Unit = { onChange(pairs.toMutableList().apply { set(index, key to it) }) }
                if (SecretFields.isSecretName(key) || SecretCipher.isSealed(value)) {
                    SecretTextField(value, onValue, Modifier.weight(1.6f), placeholder = valuePlaceholder, label = valuePlaceholder)
                } else {
                    TextField(value, onValue, Modifier.weight(1.6f), placeholder = valuePlaceholder, label = valuePlaceholder)
                }
                IconButton("trash", "Remove", { onChange(pairs.toMutableList().apply { removeAt(index) }) }, size = ControlSize.Compact, danger = true)
            }
        }
        TextButton(addLabel, "plus", { onChange(pairs + ("" to "")) })
    }
}

@Composable
internal fun LockedSecretsWarning(profile: UploaderProfile) {
    val locked = UploaderSecrets.lockedFields(profile)
    if (locked.isEmpty()) return
    Hint("Re-enter ${locked.joinToString(", ")}. Saved secrets can't be read here, so uploads fail until you do.", "alert-circle", OneConfigTheme.current.error)
}

@Composable
internal fun SecretsNote() {
    Hint("Keys, tokens and passwords are encrypted in your config.", "lock")
}

private class PartialMask(private val ranges: (String) -> List<IntRange>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val chars = text.text.toCharArray()
        for (range in ranges(text.text)) {
            for (index in range) if (index in chars.indices) chars[index] = '•'
        }
        return TransformedText(AnnotatedString(String(chars)), OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean = other is PartialMask && other.ranges === ranges

    override fun hashCode(): Int = ranges.hashCode()
}
