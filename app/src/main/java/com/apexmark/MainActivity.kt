package com.apexmark

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apexmark.engine.ConvertResult
import com.apexmark.engine.MarkdownConverter
import com.apexmark.engine.StyleStyler
import com.apexmark.service.FloatingPortalService
import com.apexmark.service.FloatingPortalServiceLocator
import com.apexmark.ui.theme.*

class MainActivity : ComponentActivity() {

    /** 优先复用 Service 中的 converter；冷启动 Service 尚未到位时本地懒构造一次。 */
    private val converter: MarkdownConverter by lazy {
        FloatingPortalServiceLocator.instance?.converter ?: MarkdownConverter(StyleStyler())
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            FloatingPortalService.startWithBubble(this)
            Toast.makeText(this, getString(R.string.bubble_started), Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        if (!sharedText.isNullOrBlank()) {
            val (plain, html) = converter.convertText(sharedText)
            converter.writeToClipboard(this, plain, html)
            Toast.makeText(this, getString(R.string.converted_success), Toast.LENGTH_SHORT).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val isFirstLaunch = !getPreferences(MODE_PRIVATE).getBoolean("onboarded", false)

        setContent {
            ApexMarkTheme {
                var showOnboarding by remember { mutableStateOf(isFirstLaunch) }

                if (showOnboarding) {
                    OnboardingDialog(
                        hasOverlayPermission = Settings.canDrawOverlays(this),
                        onGrantOverlay = { requestOverlayPermission() },
                        onDismiss = {
                            getPreferences(MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
                            showOnboarding = false
                        }
                    )
                }

                MainScreen(
                    converter = converter,
                    onToggleBubble = ::toggleBubble
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        overlayPermissionLauncher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun toggleBubble() {
        if (FloatingPortalServiceLocator.bubbleVisibleFlow.value) {
            FloatingPortalService.toggleBubble(this)
            return
        }
        if (!Settings.canDrawOverlays(this)) requestOverlayPermission()
        else {
            FloatingPortalService.startWithBubble(this)
            Toast.makeText(this, getString(R.string.bubble_started), Toast.LENGTH_SHORT).show()
        }
    }

}


@Composable
private fun OnboardingDialog(
    hasOverlayPermission: Boolean,
    onGrantOverlay: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Apex600, Apex400))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Security, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        },
        title = {
            Text(stringResource(R.string.onboarding_title), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.onboarding_desc),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                PermissionItem(Icons.Filled.Layers, stringResource(R.string.permission_overlay_title),
                    stringResource(R.string.permission_overlay_desc), hasOverlayPermission, onGrantOverlay)
                PermissionItem(Icons.Filled.ContentPaste, stringResource(R.string.permission_clipboard_title),
                    stringResource(R.string.permission_clipboard_desc), true, {})

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Apex500.copy(alpha = 0.08f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.PrivacyTip, null, tint = Apex500, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.privacy_notice),
                            style = MaterialTheme.typography.bodySmall, color = Apex700, lineHeight = 18.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.get_started), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun PermissionItem(icon: ImageVector, title: String, desc: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (granted) Success.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (granted) Icons.Filled.CheckCircle else icon, null,
                tint = if (granted) Success else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (granted) {
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.permission_granted), fontSize = 11.sp, color = Success, fontWeight = FontWeight.Medium)
                }
            }
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
            if (!granted) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onGrant, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(30.dp)) {
                    Text(stringResource(R.string.go_enable), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


@Composable
private fun MainScreen(
    converter: MarkdownConverter,
    onToggleBubble: () -> Unit
) {
    val context = LocalContext.current
    var lastResult by remember { mutableStateOf<String?>(null) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    val bubbleVisible by FloatingPortalServiceLocator.bubbleVisibleFlow.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { showThemeSheet = true }) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = stringResource(R.string.theme),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showAboutSheet = true }) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = stringResource(R.string.about),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Apex400.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(2.dp, Apex500.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_apexmark_logo),
                    contentDescription = "ApexMark",
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "ApexMark",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.app_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    when (val result = converter.convertClipboard(context)) {
                        is ConvertResult.Success -> {
                            lastResult = context.getString(R.string.converted_chars, result.charCount)
                            Toast.makeText(context, context.getString(R.string.converted_success), Toast.LENGTH_SHORT).show()
                        }
                        is ConvertResult.Empty -> lastResult = context.getString(R.string.clipboard_empty)
                        is ConvertResult.NotMarkdown -> lastResult = context.getString(R.string.not_markdown)
                        is ConvertResult.NotHtml -> lastResult = context.getString(R.string.not_html)
                        is ConvertResult.TooLarge -> lastResult = context.getString(R.string.content_too_large, result.sizeMb)
                        is ConvertResult.Error -> lastResult = result.message
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.ContentPaste, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.convert_clipboard_content), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    when (val result = converter.convertHtmlClipboardToMarkdown(context)) {
                        is ConvertResult.Success -> {
                            lastResult = context.getString(R.string.converted_to_markdown_with_count, result.charCount)
                            Toast.makeText(context,
                                context.getString(R.string.converted_to_markdown_with_count, result.charCount),
                                Toast.LENGTH_SHORT).show()
                        }
                        is ConvertResult.Empty -> lastResult = context.getString(R.string.clipboard_empty)
                        is ConvertResult.NotMarkdown -> lastResult = context.getString(R.string.not_markdown)
                        is ConvertResult.NotHtml -> lastResult = context.getString(R.string.not_html)
                        is ConvertResult.TooLarge -> lastResult = context.getString(R.string.content_too_large, result.sizeMb)
                        is ConvertResult.Error -> lastResult = result.message
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.SwapHoriz, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.convert_clipboard_reverse), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            AnimatedVisibility(visible = lastResult != null) {
                lastResult?.let { msg ->
                    val isOk = msg.startsWith("✓")
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOk) SuccessLight else ErrorLight
                        )
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isOk) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                null, Modifier.size(18.dp),
                                tint = if (isOk) Success else Error
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(msg, fontWeight = FontWeight.Medium,
                                color = if (isOk) Success else Error,
                                fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(R.string.quick_tools), modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(10.dp))

            ToolCard(
                icon = if (bubbleVisible) Icons.Filled.HighlightOff else Icons.Filled.Circle,
                title = stringResource(if (bubbleVisible) R.string.stop_bubble else R.string.start_bubble),
                description = stringResource(if (bubbleVisible) R.string.stop_bubble_desc else R.string.start_bubble_desc),
                onClick = onToggleBubble
            )

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.usage_guide), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    UsageStep("1", stringResource(R.string.usage_step_1))
                    UsageStep("2", stringResource(R.string.usage_step_2))
                    UsageStep("3", stringResource(R.string.usage_step_3))
                    UsageStep("4", stringResource(R.string.usage_step_4))
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    if (showThemeSheet) {
        ThemePickerSheet(onDismiss = { showThemeSheet = false })
    }
    if (showAboutSheet) {
        AboutSheet(onDismiss = { showAboutSheet = false })
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
    }
    val repoUrl = stringResource(R.string.about_repo_url)
    val emailUri = stringResource(R.string.about_email)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Apex400.copy(alpha = 0.18f), MaterialTheme.colorScheme.surface)))
                        .border(1.5.dp, Apex500.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_apexmark_logo),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("ApexMark", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.about_version, versionName),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                stringResource(R.string.about_description),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(20.dp))

            AboutRow(Icons.Filled.Code, stringResource(R.string.about_repository), repoUrl) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl)))
            }
            AboutRow(Icons.Filled.Email, stringResource(R.string.about_contact), emailUri) {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$emailUri")))
            }
            AboutRow(Icons.Filled.Gavel, stringResource(R.string.about_license), stringResource(R.string.about_license_value)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/agpl-3.0.html")))
            }
            AboutRow(Icons.Filled.PrivacyTip, stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_value), null)
            AboutRow(Icons.Filled.Person, stringResource(R.string.about_author), stringResource(R.string.about_author_value), null)

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Apex500.copy(alpha = 0.08f))
            ) {
                Text(
                    text = stringResource(R.string.about_credits),
                    modifier = Modifier.padding(14.dp),
                    fontSize = 12.sp,
                    color = Apex700,
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AboutRow(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)?) {
    val mod = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(vertical = 10.dp)
    Row(modifier = mod, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
        }
        if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePickerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentMode by ThemePreference.themeMode.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.appearance_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.choose_theme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThemeOption(
                    label = stringResource(R.string.theme_system),
                    icon = Icons.Filled.BrightnessAuto,
                    selected = currentMode == ThemeMode.System,
                    lightPreviewColors = listOf(Neutral0, Neutral100, Apex500),
                    darkPreviewColors = listOf(DarkSurface1, DarkSurface2, Apex400),
                    showBoth = true,
                    modifier = Modifier.weight(1f)
                ) { ThemePreference.setThemeMode(context, ThemeMode.System) }

                ThemeOption(
                    label = stringResource(R.string.theme_light),
                    icon = Icons.Filled.LightMode,
                    selected = currentMode == ThemeMode.Light,
                    lightPreviewColors = listOf(Neutral0, Neutral100, Apex500),
                    modifier = Modifier.weight(1f)
                ) { ThemePreference.setThemeMode(context, ThemeMode.Light) }

                ThemeOption(
                    label = stringResource(R.string.theme_dark),
                    icon = Icons.Filled.DarkMode,
                    selected = currentMode == ThemeMode.Dark,
                    darkPreviewColors = listOf(DarkSurface1, DarkSurface2, Apex400),
                    modifier = Modifier.weight(1f)
                ) { ThemePreference.setThemeMode(context, ThemeMode.Dark) }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    lightPreviewColors: List<Color> = emptyList(),
    darkPreviewColors: List<Color> = emptyList(),
    showBoth: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                  else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                if (showBoth || lightPreviewColors.isNotEmpty()) {
                    val colors = lightPreviewColors.ifEmpty { darkPreviewColors }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(colors.getOrElse(0) { Neutral0 })
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.getOrElse(2) { Apex500 })
                        )
                    }
                }
                if (showBoth || (lightPreviewColors.isEmpty() && darkPreviewColors.isNotEmpty())) {
                    val colors = darkPreviewColors.ifEmpty { lightPreviewColors }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(colors.getOrElse(0) { DarkSurface1 })
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.getOrElse(2) { Apex400 })
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Icon(
                icon, null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (selected) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}


@Composable
private fun ToolCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun UsageStep(number: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
