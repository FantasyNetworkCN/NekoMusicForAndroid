package com.neko.music.ui.screens

import android.content.Context
import com.neko.music.R
import com.neko.music.BuildConfig
import com.neko.music.debug.NekoLog as Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.neko.music.ui.theme.isAppDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.config.AppConfig
import com.neko.music.ui.icons.NekoIcon
import com.neko.music.ui.icons.NekoSymbol
import com.neko.music.ui.icons.NekoSymbolIcon
import com.neko.music.data.manager.AppUpdateManager
import com.neko.music.data.manager.UpdateInfo
import com.neko.music.data.cache.AppStorageManager
import com.neko.music.data.cache.AppStorageUsage
import com.neko.music.ui.components.AppUpdateDownloadProgressDialog
import com.neko.music.ui.components.AppUpdateErrorDialog
import com.neko.music.ui.components.AppUpdatePromptDialog
import com.neko.music.ui.components.AppUpdateSuccessDialog
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.NekoAdaptivePageContent
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LiquidGlassSlider
import com.neko.music.ui.components.isLiquidGlassEffectActive
import com.neko.music.util.AppHaptics
import com.neko.music.service.MusicPlayerManager
import com.neko.music.service.StemStreamPhase
import com.neko.music.ui.components.StemAiModelSection
import com.neko.music.ui.components.stemPreparationDescription
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onNavigateToCache: () -> Unit = {},
    onNavigateToPersonalization: () -> Unit = {},
    onNavigateToLyricsStyle: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    val sectionStates = rememberSaveableStateHolder()
    val updateManager = remember { AppUpdateManager(context) }

    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE

    // 更新状态
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateChangelog by remember { mutableStateOf<String?>(null) }
    var updateChangelogLoading by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadTransferred by remember { mutableStateOf(0L) }
    var downloadContentLength by remember { mutableStateOf(0L) }
    var showUpdateSuccessDialog by remember { mutableStateOf(false) }
    var showUpdateErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // 缓存设置 - 使用与 MusicCacheManager 相同的 SharedPreferences
    val cachePrefs = remember { context.getSharedPreferences("music_cache", Context.MODE_PRIVATE) }
    var isCacheEnabled by remember { mutableStateOf(cachePrefs.getBoolean("cache_enabled", true)) }

    // 焦点锁定设置
    val focusLockPrefs = remember { context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE) }
    var isFocusLockEnabled by remember { mutableStateOf(focusLockPrefs.getBoolean("focus_lock_enabled", false)) }
    val stemSeparation = remember(context) { MusicPlayerManager.getInstance(context).stemSeparation }
    val stemState by stemSeparation.state.collectAsState()
    val stemModelState by stemSeparation.modelState.collectAsState()
    val stemStreamPhase by stemSeparation.streamPhase.collectAsState()
    val stemSubtitle = when {
        stemState.enabled -> stemPreparationDescription(stemModelState, stemStreamPhase)
        else -> stringResource(R.string.stem_separation_hint)
    } + if (stemState.roomPlaybackActive) "\n" + stringResource(R.string.stem_local_only) else ""

    // 语言设置
    val languagePrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var currentLanguage by remember { mutableStateOf(languagePrefs.getString("language", "system") ?: "system") }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val themeMode =
        languagePrefs.getString(AppConfig.PrefConfig.KEY_THEME, AppConfig.PrefConfig.DEFAULT_THEME)
            ?: AppConfig.PrefConfig.DEFAULT_THEME
    val themeSubtitle = when (themeMode) {
        "light" -> stringResource(id = R.string.theme_light)
        "dark" -> stringResource(id = R.string.theme_dark)
        else -> stringResource(id = R.string.theme_follow_system)
    }

    // 缓存管理
    val cacheManager = remember { com.neko.music.data.cache.MusicCacheManager.getInstance(context) }
    var cacheSize by remember { mutableStateOf(cacheManager.getCacheSizeFormatted()) }
    var cachedMusicCount by remember { mutableStateOf(cacheManager.getCachedMusicCount()) }
    var storageUsage by remember { mutableStateOf(AppStorageUsage()) }

    LaunchedEffect(Unit) {
        storageUsage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AppStorageManager.calculate(context)
        }
    }

    // 当缓存启用状态改变时更新缓存信息
    LaunchedEffect(isCacheEnabled) {
        cacheSize = cacheManager.getCacheSizeFormatted()
        cachedMusicCount = cacheManager.getCachedMusicCount()
    }

    // 从缓存管理页返回时刷新统计，并在离开页面时移除监听。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, cacheManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cacheSize = cacheManager.getCacheSizeFormatted()
                cachedMusicCount = cacheManager.getCachedMusicCount()
                scope.launch {
                    storageUsage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        AppStorageManager.calculate(context)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 检查更新
    val checkUpdate = {
        scope.launch {
            isCheckingUpdate = true
            try {
                val info = updateManager.checkUpdate()
                if (info != null && info.isUpdateAvailable) {
                    updateInfo = info
                    showUpdateDialog = true
                    updateChangelog = null
                    updateChangelogLoading = true
                    updateChangelog = updateManager.fetchChangelog(info.versionName)
                    updateChangelogLoading = false
                } else {
                    updateStatus = context.getString(
                        if (info != null) R.string.settings_update_current else R.string.settings_update_failed,
                    )
                }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "检查更新失败", e)
                updateStatus = context.getString(R.string.settings_update_failed)
                updateChangelogLoading = false
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    // 下载并安装更新
    val downloadAndInstall = {
        scope.launch {
            isDownloading = true
            downloadTransferred = 0L
            downloadContentLength = 0L

            // 清理所有旧的更新文件
            updateManager.cleanupUpdateFiles()

            try {
                val apkFile = updateManager.downloadApk(
                    updateInfo!!.updateUrl,
                    { downloaded, total ->
                        downloadTransferred = downloaded
                        downloadContentLength = total
                    },
                )

                if (apkFile != null) {
                    isDownloading = false
                    showUpdateDialog = false
                    showUpdateSuccessDialog = true
                    updateManager.installApk(apkFile)
                } else {
                    isDownloading = false
                    showUpdateDialog = false
                    showUpdateErrorDialog = true
                    errorMessage = "下载失败，请稍后重试"
                }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "下载更新失败", e)
                isDownloading = false
                showUpdateDialog = false
                showUpdateErrorDialog = true
                errorMessage = "下载失败：${e.message}"
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val appTitle = stringResource(R.string.settings_section_app)
    val playbackTitle = stringResource(R.string.settings_section_playback)
    val hapticsTitle = stringResource(R.string.haptics_section)
    val aboutTitle = stringResource(R.string.settings_section_about)
    val entries = listOf(
        SettingsSearchEntry(SettingsEntryId.Appearance, SettingsSectionId.App,
            stringResource(R.string.personalization), themeSubtitle, appTitle, "外观 主题 壁纸 appearance theme wallpaper"),
        SettingsSearchEntry(SettingsEntryId.Lyrics, SettingsSectionId.App,
            stringResource(R.string.lyrics_style_entry_title), stringResource(R.string.lyrics_style_entry_subtitle), appTitle,
            "歌词 字号 翻译 音译 lyrics font translation"),
        SettingsSearchEntry(SettingsEntryId.Language, SettingsSectionId.App,
            stringResource(R.string.language), getLanguageDisplayName(context, currentLanguage), appTitle, "语言 language 中文 English"),
        SettingsSearchEntry(SettingsEntryId.AudioFocus, SettingsSectionId.Playback,
            stringResource(R.string.focus_lock), stringResource(R.string.focus_lock_subtitle), playbackTitle, "焦点 播放 focus audio playback"),
        SettingsSearchEntry(SettingsEntryId.StemSeparation, SettingsSectionId.Playback,
            stringResource(R.string.stem_separation), stemSubtitle, playbackTitle, "人声 伴奏 音量 分离 vocals instrumental separation volume"),
        SettingsSearchEntry(SettingsEntryId.CacheEnabled, SettingsSectionId.Playback,
            stringResource(R.string.cache_enabled), stringResource(R.string.cache_enabled_subtitle), playbackTitle, "缓存 离线 cache offline"),
        SettingsSearchEntry(SettingsEntryId.CacheManagement, SettingsSectionId.Playback,
            stringResource(R.string.space_management), stringResource(R.string.storage_summary_songs,
                cachedMusicCount, AppStorageManager.format(storageUsage.total)), playbackTitle,
            "缓存 存储 空间 清理 cache storage clear"),
        SettingsSearchEntry(SettingsEntryId.Haptics, SettingsSectionId.Haptics,
            stringResource(R.string.haptics_enabled), stringResource(R.string.haptics_strength), hapticsTitle, "振动 触感 测试 haptics vibration test"),
        SettingsSearchEntry(SettingsEntryId.Update, SettingsSectionId.About,
            stringResource(R.string.settings_check_updates),
            if (isCheckingUpdate) stringResource(R.string.settings_update_checking)
            else updateStatus.ifEmpty { "${stringResource(R.string.version)} $versionName ($versionCode)" },
            aboutTitle, "版本 更新 version update"),
        SettingsSearchEntry(SettingsEntryId.Privacy, SettingsSectionId.About,
            stringResource(R.string.settings_privacy_title), stringResource(R.string.settings_privacy_subtitle), aboutTitle,
            "隐私 权限 privacy permissions"),
    )
    val visibleEntries = filterSettingsEntries(entries, search)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(
                modifier = Modifier.fillMaxSize(),
            )
            PlaylistPageDarkTintOverlay(enabled = isDarkTheme)
        }

        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentColor = scheme.onSurface,
            topBar = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LargeTopAppBar(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth(),
                        title = {
                            Text(
                                stringResource(id = R.string.settings),
                                color = scheme.onSurface,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.8).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                NekoIcon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(id = R.string.back),
                                    tint = scheme.onSurfaceVariant,
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        )
                    )
                }
            }
        ) { paddingValues ->
            NekoAdaptivePageContent(
                modifier = Modifier.padding(paddingValues),
                maxWidth = 720.dp,
            ) {
                CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 140.dp),
                    ) {
                        SettingsSearchField(search) { search = it }
                        Spacer(Modifier.height(20.dp))
                        SettingsSectionId.entries.forEach { section ->
                            val items = visibleEntries.filter { it.section == section }
                            if (items.isNotEmpty()) {
                                sectionStates.SaveableStateProvider(section.name) {
                                    if (section == SettingsSectionId.Haptics) {
                                        HapticSettingsSection(forceExpanded = search.isNotBlank())
                                    } else {
                                        SettingSection(
                                            title = items.first().sectionTitle,
                                            collapsible = true,
                                            forceExpanded = search.isNotBlank(),
                                        ) {
                                            items.forEachIndexed { index, item ->
                                                val separator = index > 0
                                                when (item.id) {
                                                    SettingsEntryId.Appearance -> SettingItem(
                                                        NekoSymbol.Sparkles, item.title, item.subtitle,
                                                        showSeparator = separator, onClick = onNavigateToPersonalization,
                                                    )
                                                    SettingsEntryId.Lyrics -> SettingItem(
                                                        NekoSymbol.Lyrics, item.title, item.subtitle,
                                                        showSeparator = separator, onClick = onNavigateToLyricsStyle,
                                                    )
                                                    SettingsEntryId.Language -> SettingItem(
                                                        NekoSymbol.Switch, item.title, item.subtitle,
                                                        showSeparator = separator, onClick = { showLanguageDialog = true },
                                                    )
                                                    SettingsEntryId.AudioFocus -> SettingSwitchItem(
                                                        NekoSymbol.Volume, item.title, item.subtitle,
                                                        checked = isFocusLockEnabled, showSeparator = separator,
                                                        onCheckedChange = { enabled ->
                                                            isFocusLockEnabled = enabled
                                                            focusLockPrefs.edit().putBoolean("focus_lock_enabled", enabled).apply()
                                                            com.neko.music.service.MusicPlayerManager.getInstance(context)
                                                                .updateAudioAttributes(enabled)
                                                        },
                                                    )
                                                    SettingsEntryId.CacheEnabled -> SettingSwitchItem(
                                                        NekoSymbol.Download, item.title, item.subtitle,
                                                        checked = isCacheEnabled, showSeparator = separator,
                                                        onCheckedChange = { enabled ->
                                                            isCacheEnabled = enabled
                                                            cachePrefs.edit().putBoolean("cache_enabled", enabled).apply()
                                                        },
                                                    )
                                                    SettingsEntryId.StemSeparation -> {
                                                        SettingSwitchItem(
                                                            NekoSymbol.AutoMix, item.title, item.subtitle,
                                                            checked = stemState.enabled,
                                                            showSeparator = separator,
                                                            onCheckedChange = stemSeparation::setEnabled,
                                                        )
                                                        StemAiModelSection(
                                                            modelState = stemModelState,
                                                            onRetryDownload = stemSeparation::requestAiModel,
                                                        )
                                                        if (stemState.enabled) {
                                                            if (stemStreamPhase == StemStreamPhase.FAILED) {
                                                                TextButton(onClick = stemSeparation::retry) {
                                                                    Text(stringResource(R.string.stem_retry))
                                                                }
                                                            }
                                                            TextButton(onClick = {
                                                                stemSeparation.setVocals(1f)
                                                                stemSeparation.setInstrumental(1f)
                                                            }) { Text(stringResource(R.string.stem_reset_mix)) }
                                                        }
                                                    }
                                                    SettingsEntryId.CacheManagement -> SettingItem(
                                                        NekoSymbol.Storage, item.title, item.subtitle,
                                                        showSeparator = separator, onClick = onNavigateToCache,
                                                    )
                                                    SettingsEntryId.Update -> SettingItem(
                                                        NekoSymbol.ArrowUp, item.title, item.subtitle,
                                                        showSeparator = separator, showLoading = isCheckingUpdate,
                                                        onClick = { if (!isCheckingUpdate) checkUpdate() },
                                                    )
                                                    SettingsEntryId.Privacy -> SettingItem(
                                                        NekoSymbol.Book, item.title, item.subtitle,
                                                        showSeparator = separator, onClick = onNavigateToPrivacyPolicy,
                                                    )
                                                    SettingsEntryId.Haptics -> Unit
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                        if (visibleEntries.isEmpty()) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                NekoSymbolIcon(NekoSymbol.Search, null, Modifier.size(28.dp), scheme.onSurfaceVariant)
                                Text(stringResource(R.string.settings_search_empty), color = scheme.onSurfaceVariant)
                                TextButton(onClick = { search = "" }) { Text(stringResource(R.string.settings_search_clear)) }
                            }
                        }
                    }

                    // 更新对话框
                    if (showUpdateDialog && updateInfo != null) {
                        AppUpdatePromptDialog(
                            versionName = updateInfo!!.versionName,
                            versionCode = updateInfo!!.versionCode,
                            changelog = updateChangelog,
                            changelogLoading = updateChangelogLoading,
                            onConfirm = { downloadAndInstall() },
                            onDismiss = {
                                showUpdateDialog = false
                                updateChangelogLoading = false
                            }
                        )
                    }

                    if (isDownloading) {
                        AppUpdateDownloadProgressDialog(
                            transferredBytes = downloadTransferred,
                            contentLengthBytes = downloadContentLength,
                            onDismiss = { isDownloading = false }
                        )
                    }

                    if (showUpdateSuccessDialog) {
                        AppUpdateSuccessDialog(
                            onDismiss = { showUpdateSuccessDialog = false }
                        )
                    }

                    if (showUpdateErrorDialog) {
                        AppUpdateErrorDialog(
                            message = errorMessage,
                            onDismiss = { showUpdateErrorDialog = false }
                        )
                    }

                    // 语言选择对话框
                    if (showLanguageDialog) {
                        LanguageSelectionDialog(
                            currentLanguage = currentLanguage,
                            onDismiss = { showLanguageDialog = false },
                            onLanguageSelected = { languageCode ->
                                currentLanguage = languageCode
                                languagePrefs.edit().putString("language", languageCode).apply()
                                // 重新创建Activity以应用语言更改，保持在当前页面
                                if (context is android.app.Activity) {
                                    context.recreate()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HapticSettingsSection(forceExpanded: Boolean) {
    val context = LocalContext.current
    val hasVibrator = remember(context) { AppHaptics.hasVibrator(context) }
    val supportsStrength = remember(context) { AppHaptics.supportsStrength(context) }
    var enabled by remember(context) { mutableStateOf(AppHaptics.isEnabled(context)) }
    var strength by remember(context) { mutableFloatStateOf(AppHaptics.strength(context).toFloat()) }
    val strengthState = remember(context) {
        SliderState(
            value = strength,
            trackRange = AppHaptics.MIN_STRENGTH.toFloat()..AppHaptics.MAX_STRENGTH.toFloat(),
        )
    }
    val backdrop = LocalLiquidLayerBackdrop.current
    val useLiquid = backdrop != null && isLiquidGlassEffectActive(backdrop)

    SettingSection(title = stringResource(R.string.haptics_section), collapsible = true, forceExpanded = forceExpanded) {
        if (!hasVibrator) {
            Text(
                text = stringResource(R.string.haptics_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            SettingSwitchItem(
                symbol = NekoSymbol.AutoMix,
                title = stringResource(R.string.haptics_enabled),
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    AppHaptics.setEnabled(context, checked)
                    if (checked) AppHaptics.pulse(context, force = true)
                },
            )
            if (supportsStrength) {
                val strengthLabel = stringResource(R.string.haptics_strength)
                val testLabel = stringResource(R.string.haptics_test)
                val isDark = isAppDarkTheme()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = strengthLabel, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Text(
                            text = stringResource(R.string.haptics_strength_percent, strength.roundToInt()),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val saveStrength = {
                        AppHaptics.setStrength(context, strength.roundToInt())
                        AppHaptics.pulse(context)
                    }
                    if (useLiquid) {
                        val range = (AppHaptics.MAX_STRENGTH - AppHaptics.MIN_STRENGTH).toFloat()
                        LiquidGlassSlider(
                            value = (strength - AppHaptics.MIN_STRENGTH) / range,
                            onValueChange = { fraction ->
                                strength = AppHaptics.MIN_STRENGTH + fraction * range
                                strengthState.value = strength
                            },
                            backdrop = backdrop,
                            enabled = enabled,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .semantics { contentDescription = strengthLabel },
                            onValueChangeFinished = saveStrength,
                        )
                    } else {
                        Slider(
                            state = strengthState,
                            onValueChange = { strength = it },
                            onValueChangeFinished = saveStrength,
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = strengthLabel },
                        )
                    }
                    val panel = LiquidGlassDefaults.shareSheetSection
                    GlassSurface(
                        modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = { AppHaptics.pulse(context, force = true) })
                            .semantics { contentDescription = testLabel },
                        shape = RoundedCornerShape(20.dp),
                        backgroundAlpha = panel.tint.background(isDark),
                        borderAlpha = panel.tint.border(isDark),
                        highlightAlpha = panel.tint.highlight(isDark),
                        borderColor = MaterialTheme.colorScheme.outline,
                        liquidBlur = panel.liquid.blur,
                        liquidLensHeight = panel.liquid.lensHeight,
                        liquidLensAmount = panel.liquid.lensAmount,
                        material3Container = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(testLabel, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val languages = listOf(
        "system" to stringResource(id = R.string.language_follow_system),
        "zh" to stringResource(id = R.string.language_zh),
        "nya" to stringResource(id = R.string.language_nya),
        "en" to stringResource(id = R.string.language_en)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.language),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.primary
            )
        },
        text = {
            Column {
                languages.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLanguageSelected(code)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == code,
                            onClick = {
                                onLanguageSelected(code)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = scheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = name,
                            fontSize = 16.sp,
                            color = scheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.cancel),
                    fontSize = 16.sp,
                    color = scheme.onSurfaceVariant
                )
            }
        },
        containerColor = scheme.surfaceContainerHigh
    )
}

fun getLanguageDisplayName(context: Context, language: String): String {
    return when (language) {
        "system" -> context.getString(R.string.language_follow_system)
        "zh" -> context.getString(R.string.language_zh)
        "nya" -> context.getString(R.string.language_nya)
        "en" -> context.getString(R.string.language_en)
        else -> context.getString(R.string.language_follow_system)
    }
}
