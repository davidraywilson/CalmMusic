package com.calmapps.calmmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun PermissionsOnboardingScreen(
    hasOverlayPermission: Boolean,
    hasBatteryOptimizationExemption: Boolean,
    onRequestOverlayPermissionClick: () -> Unit,
    onRequestBatteryOptimizationClick: () -> Unit,
    onContinueClick: () -> Unit,
    onSkipClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextMMD(
                text = "Welcome to CalmMusic",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextMMD(
                text = "Before getting started, CalmMusic needs a few permissions to be most useful. " +
                        "The \"Dynamic Island\" inspired overlay allows you to access what's playing from " +
                        "any screen.\n" +
                        "\n" +
                        "Most phones now have smart battery optimizations which can prevent " +
                        "CalmMusic from continuing to run in the background when not actively playing " +
                        "a song. This will cause the overlay to disappear.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedButtonMMD(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestOverlayPermissionClick,
                enabled = !hasOverlayPermission,
                contentPadding = PaddingValues(12.dp),
            ) {
                TextMMD(
                    text = if (hasOverlayPermission) {
                        "Overlay already allowed"
                    } else {
                        "Allow overlay"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            OutlinedButtonMMD(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestBatteryOptimizationClick,
                enabled = !hasBatteryOptimizationExemption,
                contentPadding = PaddingValues(12.dp),
            ) {
                TextMMD(
                    text = if (hasBatteryOptimizationExemption) {
                        "Background optimization already allowed"
                    } else {
                        "Allow CalmMusic to run in background"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            if (hasOverlayPermission && hasBatteryOptimizationExemption) {
                Spacer(modifier = Modifier.height(8.dp))

                ButtonMMD(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onContinueClick,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    TextMMD(
                        text = "Continue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextMMD(
                text = "Skip for now",
                fontSize = 18.sp,
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 4.dp)
                    .clickable(onClick = onSkipClick),
            )
        }
    }
}
