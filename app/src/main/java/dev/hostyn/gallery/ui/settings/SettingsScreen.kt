@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.hostyn.gallery.ui.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.hostyn.gallery.R
import dev.hostyn.gallery.data.AppSettings
import dev.hostyn.gallery.data.ThemeMode
import dev.hostyn.gallery.ui.canManageMedia
import dev.hostyn.gallery.ui.components.ListHeader
import dev.hostyn.gallery.ui.components.SegmentedGroup
import dev.hostyn.gallery.ui.components.SegmentedGroupScope
import dev.hostyn.gallery.ui.components.segmentedColors
import dev.hostyn.gallery.ui.requestManageMedia
import dev.hostyn.gallery.ui.startSafely

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var choosingTheme by remember { mutableStateOf(false) }
    // Changed in the system settings, so read it again when coming back.
    var manageMedia by remember { mutableStateOf(canManageMedia(context)) }
    LifecycleResumeEffect(Unit) {
        manageMedia = canManageMedia(context)
        onPauseOrDispose {}
    }

    fun open(intent: Intent) = startSafely(context, intent)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    Section(stringResource(R.string.library)) {
                        setting(
                            icon = Icons.Rounded.DoneAll,
                            title = stringResource(R.string.manage_media),
                            summary = stringResource(if (manageMedia) R.string.manage_media_on else R.string.manage_media_off),
                            onClick = { requestManageMedia(context) },
                        )
                    }
                }
            }
            item {
                Section(stringResource(R.string.appearance)) {
                    setting(
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.theme),
                        summary = stringResource(themeLabel(settings.theme)),
                        onClick = { choosingTheme = true },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        toggle(
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.dynamic_color),
                            summary = stringResource(R.string.dynamic_color_summary),
                            checked = settings.dynamicColor,
                            onChange = settings::updateDynamicColor,
                        )
                    }
                }
            }
            item {
                Section(stringResource(R.string.about)) {
                    val version = remember {
                        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { null }
                    }
                    setting(icon = Icons.Rounded.Info, title = stringResource(R.string.version), summary = version.orEmpty())
                    setting(
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.source_code),
                        summary = "github.com/hostyn/gallery",
                        onClick = { open(Intent(Intent.ACTION_VIEW, "https://github.com/hostyn/gallery".toUri())) },
                    )
                }
            }
        }
    }

    if (choosingTheme) {
        ChoiceDialog(
            title = stringResource(R.string.theme),
            labels = ThemeMode.entries.map { stringResource(themeLabel(it)) },
            selected = ThemeMode.entries.indexOf(settings.theme),
            onSelect = { settings.updateTheme(ThemeMode.entries[it]) },
            onDismiss = { choosingTheme = false },
        )
    }
}

private fun themeLabel(t: ThemeMode) = when (t) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

@Composable
private fun ChoiceDialog(title: String, labels: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                labels.forEachIndexed { i, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .selectable(selected = i == selected, role = Role.RadioButton) {
                                onSelect(i)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                    ) {
                        RadioButton(selected = i == selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A titled M3 segmented list of settings. */
// Inline, like SegmentedGroup, so a switch's new value redraws its item (see SegmentedGroup).
@Composable
private inline fun Section(title: String, content: @Composable SegmentedGroupScope.() -> Unit) {
    ListHeader(title, Modifier.padding(top = 8.dp))
    SegmentedGroup(content = content)
}

/** A setting: tap to change it, or plain information without [onClick]. */
private fun SegmentedGroupScope.setting(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
) = item { shapes ->
    val leading: @Composable () -> Unit = { Icon(icon, null) }
    val supporting: (@Composable () -> Unit)? = summary?.takeIf { it.isNotEmpty() }?.let { { Text(it) } }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            colors = segmentedColors(),
            leadingContent = leading,
            supportingContent = supporting,
        ) { Text(title) }
    } else {
        SegmentedListItem(
            shapes = shapes,
            colors = segmentedColors(),
            leadingContent = leading,
            supportingContent = supporting,
        ) { Text(title) }
    }
}

/** An on/off setting: the whole item toggles the switch. */
private fun SegmentedGroupScope.toggle(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) = item { shapes ->
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        shapes = shapes,
        colors = segmentedColors(),
        leadingContent = { Icon(icon, null) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) { Text(title) }
}
