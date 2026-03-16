package com.combadge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.combadge.app.ui.theme.*

/**
 * First-launch screen where the user enters their crew name.
 */
@Composable
fun RegistrationScreen(
    onRegister: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val canProceed = name.trim().length >= 2

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // Header
            Text(
                text = "STARFLEET COMBADGE",
                fontSize = 22.sp,
                color = LcarsAmber,
                letterSpacing = 5.sp,
                textAlign = TextAlign.Center
            )

            LcarsBar(color = LcarsAmber, height = 3.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Prompt
            Text(
                text = "CREW IDENTIFICATION",
                fontSize = 14.sp,
                color = LcarsLavender,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Enter your crew name.\nThis is how other combadges will locate you.",
                fontSize = 13.sp,
                color = LcarsTextDim,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            // Input
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = {
                    Text(
                        "e.g. Geordi, Number One, Counselor",
                        color = LcarsTextDim,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LcarsAmber,
                    unfocusedBorderColor = LcarsLavender.copy(alpha = 0.5f),
                    focusedTextColor = LcarsText,
                    unfocusedTextColor = LcarsText,
                    cursorColor = LcarsAmber,
                    focusedContainerColor = DeepSpaceDark,
                    unfocusedContainerColor = DeepSpaceDark
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (canProceed) onRegister(name.trim())
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            // Engage button
            Button(
                onClick = { onRegister(name.trim()) },
                enabled = canProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LcarsAmber,
                    contentColor = DeepSpace,
                    disabledContainerColor = LcarsAmberDim,
                    disabledContentColor = DeepSpace.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "ENGAGE",
                    fontSize = 16.sp,
                    letterSpacing = 4.sp,
                    color = if (canProceed) DeepSpace else DeepSpace.copy(alpha = 0.5f)
                )
            }

            LcarsBar(color = LcarsPeriwinkle, height = 2.dp)
        }
    }
}
