package dev.pschmitt.netboxandchill.ui.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.R

@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    var baseUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // ic_launcher is an <adaptive-icon> (background + foreground layers) - painterResource() only
    // supports VectorDrawables and raster assets, not that wrapper format, and throws at runtime.
    // Rendering it through a Drawable -> Bitmap first works for any drawable type.
    val appIconBitmap =
        remember { ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()?.asImageBitmap() }

    LaunchedEffect(uiState) {
        if (uiState is OnboardingUiState.Success) onDone()
    }

    Scaffold { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .padding(24.dp)
                    .fillMaxSize()
                    // Edge-to-edge (enableEdgeToEdge() in MainActivity) opts out of the legacy
                    // windowSoftInputMode=adjustResize behavior, so without this the keyboard
                    // overlaps the fields below the fold instead of the content shifting up.
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            verticalArrangement = Arrangement.Center,
        ) {
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = null,
                    modifier =
                        Modifier.size(64.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(14.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Connect to NetBox",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter your NetBox instance URL and an API token. Generate a token under your " +
                    "NetBox profile → API Tokens.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("NetBox URL") },
                placeholder = { Text("https://netbox.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val tokensUrl = baseUrl.trim().trimEnd('/') + "/user/api-tokens/"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tokensUrl)))
                        },
                        enabled = baseUrl.isNotBlank(),
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open API tokens page")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("API token") },
                // NetBox 4.x v2 tokens are "nbt_<key>.<secret>" - shown as a placeholder (not the
                // label) since it's an example format, not something to type verbatim.
                placeholder = { Text("nbt_xxxxxxxxxxxx.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") },
                singleLine = true,
                visualTransformation =
                    if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (tokenVisible) "Hide token" else "Show token",
                            )
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService<ClipboardManager>()
                                token = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)
                                        ?.text
                                        ?.toString()
                                        ?.trim() ?: token
                            }
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            val errorState = uiState as? OnboardingUiState.Error
            if (errorState != null) {
                Text(
                    errorState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { viewModel.connect(baseUrl, token) },
                enabled = uiState !is OnboardingUiState.Validating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState is OnboardingUiState.Validating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect")
                }
            }
        }
    }
}
