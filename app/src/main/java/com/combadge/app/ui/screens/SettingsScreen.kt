package com.combadge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.combadge.app.ui.theme.*

/**
 * Settings screen: crew name, aliases, volume, haptic, auto-accept.
 * Accessed via long-press on combadge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialName: String,
    initialAliases: String,
    initialVolume: Float,
    initialHaptic: Boolean,
    initialAutoAccept: Boolean,
    onSave: (name: String, aliases: List<String>, volume: Float, haptic: Boolean, autoAccept: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var aliases by remember { mutableStateOf(initialAliases) }
    var volume by remember { mutableFloatStateOf(initialVolume) }
    var haptic by remember { mutableStateOf(initialHaptic) }
    var autoAccept by remember { mutableStateOf(initialAutoAccept) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    "CREW SETTINGS",
                    color = LcarsAmber,
                    fontSize = 16.sp,
                    letterSpacing = 3.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = LcarsAmber
                    )
                }
            },
            actions = {
                TextButton(onClick = {
                    val aliasesList = aliases
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onSave(name.trim(), aliasesList, volume, haptic, autoAccept)
                }) {
                    Text("SAVE", color = LcarsAmber, letterSpacing = 2.sp, fontSize = 13.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DeepSpaceDark
            )
        )

        HorizontalDivider(color = LcarsAmber.copy(alpha = 0.4f), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            SettingSection("IDENTITY") {
                LcarsTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "My Crew Name",
                    capitalization = KeyboardCapitalization.Words
                )

                Spacer(modifier = Modifier.height(8.dp))

                LcarsTextField(
                    value = aliases,
                    onValueChange = { aliases = it },
                    label = "Aliases (comma-separated)",
                    placeholder = "e.g. Engineering, The Bridge"
                )
            }

            SettingSection("AUDIO") {
                Text("Chirp Volume", color = LcarsText, fontSize = 13.sp, letterSpacing = 1.sp)
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = LcarsAmber,
                        activeTrackColor = LcarsAmber,
                        inactiveTrackColor = LcarsAmberDim.copy(alpha = 0.4f)
                    )
                )
                Text(
                    "${(volume * 100).toInt()}%",
                    color = LcarsTextDim,
                    fontSize = 11.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingSection("BEHAVIOR") {
                LcarsSwitchRow(
                    label = "Haptic Feedback",
                    checked = haptic,
                    onCheckedChange = { haptic = it }
                )
                LcarsSwitchRow(
                    label = "Auto-accept Hails",
                    description = "Combadges open immediately when hailed",
                    checked = autoAccept,
                    onCheckedChange = { autoAccept = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = LcarsLavender,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            HorizontalDivider(
                color = LcarsLavender.copy(alpha = 0.3f),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun LcarsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = LcarsLavender, fontSize = 12.sp) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = LcarsTextDim, fontSize = 12.sp) }
        } else null,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LcarsAmber,
            unfocusedBorderColor = LcarsLavender.copy(alpha = 0.4f),
            focusedTextColor = LcarsText,
            unfocusedTextColor = LcarsText,
            cursorColor = LcarsAmber,
            focusedContainerColor = DeepSpaceDark,
            unfocusedContainerColor = DeepSpaceDark,
            focusedLabelColor = LcarsAmber
        ),
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun LcarsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = LcarsText, fontSize = 13.sp, letterSpacing = 1.sp)
            if (description.isNotEmpty()) {
                Text(description, color = LcarsTextDim, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepSpace,
                checkedTrackColor = LcarsAmber,
                uncheckedThumbColor = LcarsTextDim,
                uncheckedTrackColor = DeepSpaceDark
            )
        )
    }
}
