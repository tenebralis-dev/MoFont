package com.je.fontsmanager.samsung.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.je.fontsmanager.samsung.builder.FontBuilder
import com.je.fontsmanager.samsung.util.ShizukuAPI
import com.je.fontsmanager.samsung.util.CacheCleanupUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import rikka.shizuku.Shizuku
import java.io.FileOutputStream
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.graphics.Typeface as AndroidTypefaceLegacy
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import android.util.TypedValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.graphics.createBitmap
import com.je.fontsmanager.samsung.R

sealed class Screen(val route: String, val titleRes: Int) {
    object Home : Screen("home", R.string.nav_home)
    object Library : Screen("library", R.string.nav_library)
    object Settings : Screen("settings", R.string.title_manage)
    object FontPreview : Screen("font_preview", R.string.dialog_font_preview_title)
}

private fun getRandomSimpleText(context: Context): String {
    val simpleTexts = mutableListOf<String>()
    val resIds = mutableListOf(
        R.string.sample_text_simple_1,
        R.string.sample_text_simple_2,
        R.string.sample_text_simple_3,
        R.string.sample_text_simple_4,
    )
    resIds.forEach { resId ->
        try {
            val text = context.getString(resId)
            if (text.isNotEmpty()) {
                simpleTexts.add(text)
            }
        } catch (_: Exception) {}
    }
    return simpleTexts.randomOrNull() ?: "sample text"
}

// 《春江花月夜》—— 按顺序分配给字体库卡片的固定预览诗句
private val libraryPreviewTexts = listOf(
    "春江潮水连海平，海上明月共潮生。",
    "滟滟随波千万里，何处春江无月明。",
    "江流宛转绕芳甸，月照花林皆似霰。",
    "空里流霜不觉飞，汀上白沙看不见。",
    "江天一色无纤尘，皎皎空中孤月轮。",
    "江畔何人初见月？江月何年初照人？",
    "人生代代无穷已，江月年年望相似。",
    "不知江月待何人，但见长江送流水。",
    "白云一片去悠悠，青枫浦上不胜愁。",
    "谁家今夜扁舟子？何处相思明月楼？",
    "可怜楼上月徘徊，应照离人妆镜台。",
    "玉户帘中卷不去，捣衣砧上拂还来。",
    "此时相望不相闻，愿逐月华流照君。",
    "鸿雁长飞光不度，鱼龙潜跃水成文。",
    "昨夜闲潭梦落花，可怜春半不还家。",
    "江水流春去欲尽，江潭落月复西斜。",
    "斜月沉沉藏海雾，碣石潇湘无限路。",
    "不知乘月几人归，落月摇情满江树。"
)

private fun getLibraryPreviewText(index: Int): String {
    return libraryPreviewTexts[index % libraryPreviewTexts.size]
}

private fun makeSampleTextView(ctx: Context, textSizeSp: Float, textColor: Int, initialText: String) =
    TextView(ctx).apply {
        text = initialText
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setTextColor(textColor)
    }

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val homeViewModel: HomeViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
        bottomBar = {
            if (currentRoute != Screen.FontPreview.route) {
                NavigationBar {
                    fun navTo(screen: Screen) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    }
                    NavigationBarItem(icon = { Icon(Icons.Default.Home, null) }, label = { Text(androidx.compose.ui.res.stringResource(Screen.Home.titleRes)) },
                        selected = currentRoute == Screen.Home.route, onClick = { navTo(Screen.Home) })
                    NavigationBarItem(icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text(androidx.compose.ui.res.stringResource(Screen.Library.titleRes)) },
                        selected = currentRoute == Screen.Library.route, onClick = { navTo(Screen.Library) })
                    NavigationBarItem(icon = { Icon(Icons.Default.Settings, null) }, label = { Text(androidx.compose.ui.res.stringResource(Screen.Settings.titleRes)) },
                        selected = currentRoute == Screen.Settings.route, onClick = { navTo(Screen.Settings) })
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) { HomeScreen(navController, homeViewModel) }
            composable(Screen.Library.route) { LibraryScreen(navController, homeViewModel, libraryViewModel) }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.FontPreview.route) { FontPreviewScreen(navController, homeViewModel) }
        }
    }
}

@Composable
fun HomeScreen(navController: androidx.navigation.NavController, sharedState: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedFontFile = sharedState.selectedFontFile
    val selectedFontName = sharedState.selectedFontName
    val selectedBoldFontFile = sharedState.selectedBoldFontFile
    val selectedBoldFontName = sharedState.selectedBoldFontName
    val displayName = sharedState.displayName
    val previewTypeface = sharedState.previewTypeface
    val boldPreviewTypeface = sharedState.boldPreviewTypeface

    LaunchedEffect(sharedState.lastErrorMessage) {
        sharedState.lastErrorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            sharedState.clearError()
        }
    }
    
    var isProcessing by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var pendingInstallPackage by remember { mutableStateOf<String?>(null) }
    var awaitingInstallResult by remember { mutableStateOf(false) }
    var pendingApkFile by remember { mutableStateOf<File?>(null) }
    var showFontDropdown by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                    val excludeFiles = listOfNotNull(sharedState.selectedFontFile, sharedState.selectedBoldFontFile)
                CacheCleanupUtils.cleanup(context.cacheDir, excludeFiles)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val installAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && context.packageManager.canRequestPackageInstalls()) {
            installAction.value?.invoke()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pkgName = pendingInstallPackage
        if (pkgName == null) {
            isProcessing = false
            awaitingInstallResult = false
            return@rememberLauncherForActivityResult
        }
        if (result.resultCode == Activity.RESULT_CANCELED) {
            isProcessing = false
            awaitingInstallResult = false
            Toast.makeText(context, context.getString(R.string.toast_install_cancelled), Toast.LENGTH_SHORT).show()
            pendingInstallPackage = null
        }
    }

    DisposableEffect(awaitingInstallResult, pendingInstallPackage) {
        val pkg = pendingInstallPackage
        if (awaitingInstallResult && pkg != null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_PACKAGE_ADDED) {
                        val added = intent.data?.schemeSpecificPart
                        if (added == pkg) {
                            try { if (pendingInstallPackage == pkg) { pendingApkFile?.let { try { it.delete() } catch (_: Exception) {} }; pendingApkFile = null } } catch (_: Exception) {}
                            isProcessing = false
                            awaitingInstallResult = false
                            Toast.makeText(context, context.getString(R.string.toast_install_succeeded), Toast.LENGTH_SHORT).show()
                            pendingInstallPackage = null
                            try {
                                context.unregisterReceiver(this)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
            try { context.registerReceiver(receiver, filter) } catch (_: Exception) {}
            onDispose {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
        } else {
            onDispose {}
        }
    }

    val ttfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val fileName = FontInstallerUtils.getFileName(context, it)
            if (!fileName.endsWith(".ttf", ignoreCase = true)) {
                Toast.makeText(context, context.getString(R.string.toast_only_ttf_supported), Toast.LENGTH_SHORT).show()
                return@let
            }
            try { selectedFontFile?.delete() } catch (_: Exception) {}
            val baseName = fileName.removeSuffix(".ttf").replace(Regex("[^a-zA-Z0-9]"), "")
            val cachedFile = File(context.cacheDir, "${baseName}.ttf")

            val success = try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(cachedFile).use { output -> input.copyTo(output) }
                }
                true
            } catch (e: Exception) {
                Log.e("FontInstaller", "Failed to cache font", e)
                false
            }
            if (success) {
                try { pendingApkFile?.let { try { it.delete() } catch (_: Exception) {} }; pendingApkFile = null } catch (_: Exception) {}
                sharedState.setSelectedFontFile(cachedFile, fileName)
                sharedState.updateDisplayName(fileName.removeSuffix(".ttf"))
                sharedState.clearError()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_failed_to_save), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val boldTtfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val fileName = FontInstallerUtils.getFileName(context, it)
            if (!fileName.endsWith(".ttf", ignoreCase = true)) {
                Toast.makeText(context, context.getString(R.string.toast_only_ttf_supported), Toast.LENGTH_SHORT).show()
                return@let
            }
            sharedState.clearSelectedBold()
            val baseName = fileName.removeSuffix(".ttf").replace(Regex("[^a-zA-Z0-9]"), "")
            val cachedFile = File(context.cacheDir, "${baseName}_bold.ttf")

            val success = try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(cachedFile).use { output -> input.copyTo(output) }
                }
                true
            } catch (e: Exception) {
                Log.e("FontInstaller", "Failed to cache bold font", e)
                false
            }

            if (success) {
                try { pendingApkFile?.let { try { it.delete() } catch (_: Exception) {} }; pendingApkFile = null } catch (_: Exception) {}
                sharedState.setSelectedBoldFontFile(cachedFile, fileName)
                sharedState.clearError()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_failed_to_save_bold), Toast.LENGTH_SHORT).show()
            }
        }
    }

    installAction.value = {
        val pkgName = "com.monotype.android.font.${displayName.replace(Regex("[^a-zA-Z0-9]"), "")}"
        pendingInstallPackage = pkgName; awaitingInstallResult = true
        scope.launch {
            isProcessing = true
            FontInstallerUtils.buildAndInstallFont(context, selectedFontFile!!, displayName, installLauncher,
                onAlreadyInstalled = {
                    isProcessing = false; awaitingInstallResult = false; pendingInstallPackage = null
                    Toast.makeText(context, context.getString(R.string.toast_already_installed), Toast.LENGTH_LONG).show()
                },
                onComplete = { success ->
                    isProcessing = false; awaitingInstallResult = false
                    Toast.makeText(context, context.getString(if (FontInstallerUtils.isAppInstalled(context, pkgName) && success) R.string.toast_install_succeeded else R.string.toast_install_failed), Toast.LENGTH_SHORT).show()
                    pendingInstallPackage = null
                },
                boldTtfFile = selectedBoldFontFile,
                onRegisterPending = { _, file -> pendingApkFile = file }
            )
        }
    }

    fun performInstall() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                permissionLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_cannot_open_settings), Toast.LENGTH_SHORT).show()
            }
            return
        }
        installAction.value?.invoke()
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_title)) },
            text = {
                Column {
                    Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_message))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(displayName, { if (it.length <= 50) sharedState.updateDisplayName(it) },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_hint)) },
                        singleLine = true, isError = displayName.length > 50,
                        supportingText = { Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_counter, displayName.length)) })
                    if (displayName.length > 50) Text(androidx.compose.ui.res.stringResource(R.string.dialog_customize_name_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton({ showNameDialog = false; performInstall() }, enabled = displayName.isNotBlank() && displayName.length <= 50) { Text(androidx.compose.ui.res.stringResource(R.string.button_install)) } },
            dismissButton = { TextButton({ showNameDialog = false }) { Text(androidx.compose.ui.res.stringResource(R.string.button_cancel)) } }
        )
    }

    val appIconBitmap: Bitmap? = remember {
        val d = context.packageManager.getApplicationIcon(context.applicationInfo)
        when (d) {
            is BitmapDrawable -> d.bitmap
            else -> {
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 128
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 128
                createBitmap(w, h).also { bmp ->
                    android.graphics.Canvas(bmp).apply { d.setBounds(0, 0, w, h); d.draw(this) }
                }
            }
        }
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            appIconBitmap?.let {
                Icon(BitmapPainter(it.asImageBitmap()), null, Modifier.size(48.dp), tint = Color.Unspecified)
            }
            Spacer(Modifier.height(16.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.title_font_installer), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(32.dp))

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (selectedFontName != null) {
                        Text(androidx.compose.ui.res.stringResource(R.string.label_selected_font), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                navController.navigate(Screen.FontPreview.route)
                                Log.d("FontInstaller", "Navigating to font preview, typeface is ${if (previewTypeface != null) "loaded" else "null"}")
                            }.padding(vertical = 8.dp)
                        ) {
                            Text(selectedFontName!!, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(8.dp))
                            if (previewTypeface != null) {
                                val previewText = getRandomSimpleText(LocalContext.current)
                                AndroidView(
                                    factory = { ctx -> makeSampleTextView(ctx, 14f, onSurfaceColor, previewText).also { Log.d("FontInstaller", "Creating preview TextView with typeface") } },
                                    update = { tv -> tv.typeface = previewTypeface; tv.text = previewText; tv.setTextColor(onSurfaceColor) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else Text(getRandomSimpleText(LocalContext.current), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.label_display_name, displayName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (selectedBoldFontFile != null) {
                            Spacer(Modifier.height(12.dp))
                            Divider()
                            Spacer(Modifier.height(12.dp))
                            Text(androidx.compose.ui.res.stringResource(R.string.label_bold_variant), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(selectedBoldFontName!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else Text(androidx.compose.ui.res.stringResource(R.string.label_no_font_selected), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    { showFontDropdown = true },
                    Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.button_select_font))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }

                DropdownMenu(showFontDropdown, { showFontDropdown = false }) {
                    DropdownMenuItem(
                        { Text(androidx.compose.ui.res.stringResource(R.string.menu_regular_font)) },
                        {
                            showFontDropdown = false
                            ttfPicker.launch(arrayOf("font/ttf", "font/*", "application/octet-stream"))
                        },
                        leadingIcon = { Icon(Icons.Default.Add, null) }
                    )
                    DropdownMenuItem(
                        { Text(androidx.compose.ui.res.stringResource(R.string.menu_bold_variant)) },
                        {
                            showFontDropdown = false
                            boldTtfPicker.launch(arrayOf("font/ttf", "font/*", "application/octet-stream"))
                        },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        enabled = selectedFontFile != null
                    )
                    if (selectedBoldFontFile != null) {
                        DropdownMenuItem(
                            { Text(androidx.compose.ui.res.stringResource(R.string.menu_remove_bold)) },
                            {
                                showFontDropdown = false
                                sharedState.clearSelectedBold()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (selectedFontFile != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ showNameDialog = true }, Modifier.weight(1f), enabled = !isProcessing) {
                        Icon(Icons.Default.Edit, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.button_display_name))
                    }
                    FilledTonalButton(
                        { if (displayName.isBlank()) showNameDialog = true else performInstall() },
                        Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(androidx.compose.ui.res.stringResource(R.string.button_building))
                        } else {
                            Icon(Icons.Default.Build, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(androidx.compose.ui.res.stringResource(R.string.button_build))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton({
                try { context.startActivity(Intent("com.samsung.settings.FontStyleActivity")) }
                catch (e: Exception) { Toast.makeText(context, context.getString(R.string.toast_cannot_open_settings), Toast.LENGTH_SHORT).show() }
            }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.button_open_font_settings))
            }
        }

        if (showInstructionsDialog)
            Dialog({ showInstructionsDialog = false }) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(androidx.compose.ui.res.stringResource(R.string.dialog_instructions_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(8.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.dialog_instructions_content), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(16.dp))
                        Button({ showInstructionsDialog = false }) { Text(androidx.compose.ui.res.stringResource(R.string.button_close)) }
                    }
                }
            }

        IconButton(
            onClick = { showInstructionsDialog = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.content_description_instructions),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: androidx.navigation.NavController,
    homeViewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadUrl by remember { mutableStateOf("") }

    // Restore folder URI on first composition
    LaunchedEffect(Unit) {
        libraryViewModel.restoreFolderUri(context)
    }

    // Auto-scan when folder URI is set
    val folderUri = libraryViewModel.folderUri
    LaunchedEffect(folderUri) {
        if (folderUri != null) {
            libraryViewModel.scanFonts(context)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            libraryViewModel.setFolderUri(context, it)
        }
    }

    // Download dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { if (!libraryViewModel.isDownloading) { showDownloadDialog = false; downloadUrl = "" } },
            title = { Text(stringResource(R.string.download_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = downloadUrl,
                        onValueChange = { downloadUrl = it; libraryViewModel.clearDownloadError() },
                        label = { Text(stringResource(R.string.download_url_hint)) },
                        singleLine = true,
                        enabled = !libraryViewModel.isDownloading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (libraryViewModel.isDownloading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.download_progress), style = MaterialTheme.typography.bodySmall)
                    }
                    libraryViewModel.downloadError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        val errorText = if (error == "ALREADY_EXISTS") stringResource(R.string.download_error_exists)
                                        else stringResource(R.string.download_error, error)
                        Text(errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        libraryViewModel.downloadFont(context, downloadUrl.trim()) { success ->
                            if (success) {
                                showDownloadDialog = false
                                downloadUrl = ""
                                Toast.makeText(context, context.getString(R.string.download_success), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = downloadUrl.isNotBlank() && !libraryViewModel.isDownloading
                ) { Text(stringResource(R.string.download_start)) }
            },
            dismissButton = {
                if (!libraryViewModel.isDownloading) {
                    TextButton(onClick = { showDownloadDialog = false; downloadUrl = ""; libraryViewModel.clearDownloadError() }) {
                        Text(stringResource(R.string.button_cancel))
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    if (folderUri != null) {
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.SortByAlpha, contentDescription = stringResource(R.string.library_sort_name))
                            }
                            DropdownMenu(showSortMenu, { showSortMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_sort_name_asc)) },
                                    onClick = { showSortMenu = false; libraryViewModel.setSortOrder(SortOrder.NAME, true) },
                                    leadingIcon = { if (libraryViewModel.sortOrder == SortOrder.NAME && libraryViewModel.isAscending) Icon(Icons.Default.Check, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_sort_name_desc)) },
                                    onClick = { showSortMenu = false; libraryViewModel.setSortOrder(SortOrder.NAME, false) },
                                    leadingIcon = { if (libraryViewModel.sortOrder == SortOrder.NAME && !libraryViewModel.isAscending) Icon(Icons.Default.Check, null) },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_sort_date_asc)) },
                                    onClick = { showSortMenu = false; libraryViewModel.setSortOrder(SortOrder.DATE, true) },
                                    leadingIcon = { if (libraryViewModel.sortOrder == SortOrder.DATE && libraryViewModel.isAscending) Icon(Icons.Default.Check, null) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.library_sort_date_desc)) },
                                    onClick = { showSortMenu = false; libraryViewModel.setSortOrder(SortOrder.DATE, false) },
                                    leadingIcon = { if (libraryViewModel.sortOrder == SortOrder.DATE && !libraryViewModel.isAscending) Icon(Icons.Default.Check, null) },
                                )
                            }
                        }
                        IconButton(onClick = { showDownloadDialog = true }) {
                            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.download_title))
                        }
                        IconButton(onClick = { libraryViewModel.scanFonts(context) }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.library_refresh))
                        }
                    }
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.library_select_folder))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (folderUri == null) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.library_no_folder), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Default.CreateNewFolder, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.library_select_folder))
                        }
                    }
                }
            }
        } else if (libraryViewModel.isScanning) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.library_scanning), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (libraryViewModel.fontList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val localFonts = libraryViewModel.fontList.filter { it.source == FontSource.LOCAL }
            val downloadedFonts = libraryViewModel.fontList.filter { it.source == FontSource.DOWNLOADED }
            var localExpanded by remember { mutableStateOf(true) }
            var downloadedExpanded by remember { mutableStateOf(true) }

            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.library_folder_info, libraryViewModel.fontList.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // --- Local fonts section ---
                    if (localFonts.isNotEmpty()) {
                        item(key = "header_local") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { localExpanded = !localExpanded }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.library_section_local, localFonts.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (localExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (localExpanded) {
                            itemsIndexed(localFonts, key = { _, item -> "local_${item.uri}" }) { localIdx, item ->
                                val globalIdx = libraryViewModel.fontList.indexOf(item)
                                FontCard(context, scope, item, globalIdx, localIdx, onSurfaceColor, libraryViewModel, homeViewModel, navController)
                            }
                        }
                    }
                    // --- Downloaded fonts section ---
                    if (downloadedFonts.isNotEmpty()) {
                        item(key = "header_downloaded") {
                            if (localFonts.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { downloadedExpanded = !downloadedExpanded }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.library_section_downloaded, downloadedFonts.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (downloadedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (downloadedExpanded) {
                            itemsIndexed(downloadedFonts, key = { _, item -> "dl_${item.uri}" }) { dlIdx, item ->
                                val globalIdx = libraryViewModel.fontList.indexOf(item)
                                val previewIdx = localFonts.size + dlIdx
                                FontCard(context, scope, item, globalIdx, previewIdx, onSurfaceColor, libraryViewModel, homeViewModel, navController)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontCard(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    item: LocalFontItem,
    globalIndex: Int,
    previewIndex: Int,
    onSurfaceColor: Int,
    libraryViewModel: LibraryViewModel,
    homeViewModel: HomeViewModel,
    navController: androidx.navigation.NavController
) {
    LaunchedEffect(item.uri) { libraryViewModel.loadTypeface(context, globalIndex) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable {
            scope.launch {
                val cachedFile = libraryViewModel.prepareFontForPreview(context, item)
                if (cachedFile != null) {
                    homeViewModel.setSelectedFontFile(cachedFile, item.fileName)
                    homeViewModel.updateDisplayName(item.fileName.removeSuffix(".ttf"))
                    navController.navigate(Screen.FontPreview.route)
                } else {
                    Toast.makeText(context, context.getString(R.string.error_font_preview), Toast.LENGTH_SHORT).show()
                }
            }
        }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(item.fileName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (item.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (item.typeface != null) {
                val previewText = getLibraryPreviewText(previewIndex)
                AndroidView(
                    factory = { ctx -> makeSampleTextView(ctx, 18f, onSurfaceColor, previewText) },
                    update = { tv -> tv.typeface = item.typeface; tv.text = previewText; tv.setTextColor(onSurfaceColor) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(getLibraryPreviewText(previewIndex), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPreviewScreen(navController: androidx.navigation.NavController, sharedState: HomeViewModel) {
    val previewTypeface = sharedState.previewTypeface
    val fontName = sharedState.selectedFontName
    val boldTypeface = sharedState.boldPreviewTypeface
    val boldFontName = sharedState.selectedBoldFontName

    var selectedStyle by remember { mutableStateOf(PreviewStyle.Regular) }
    var customText by remember { mutableStateOf("") }
    var textSize by remember { mutableStateOf(24f) }

    val context = LocalContext.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val defaultSampleText = "疏影横斜水清浅，暗香浮动月黄昏。"
    val simpleSampleText = "疏影横斜水清浅，暗香浮动月黄昏。"

    // Helper to derive the proper typeface for the selected style
    fun deriveTypeface(
        base: AndroidTypefaceLegacy?,
        bold: AndroidTypefaceLegacy?
    ): AndroidTypefaceLegacy? {
        if (base == null) return null
        // Prefer provided bold typeface for bold styles
        val useTf = if (selectedStyle.prefersBoldTf && bold != null) bold else base
        // On API 28+, Typeface.create(Typeface, weight, italic) exists; we call it via reflection-safe signature
        return try {
            AndroidTypefaceLegacy.create(useTf, selectedStyle.weight, selectedStyle.italic)
        } catch (_: Throwable) {
            // Fallback to older style mapping
            val styleConst = when {
                selectedStyle.weight >= 700 && selectedStyle.italic -> android.graphics.Typeface.BOLD_ITALIC
                selectedStyle.weight >= 700 -> android.graphics.Typeface.BOLD
                selectedStyle.italic -> android.graphics.Typeface.ITALIC
                else -> android.graphics.Typeface.NORMAL
            }
            AndroidTypefaceLegacy.create(useTf, styleConst)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fontName ?: androidx.compose.ui.res.stringResource(R.string.dialog_font_preview_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(R.string.button_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (previewTypeface == null) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.error_font_preview),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.preview_text_size, textSize.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = textSize,
                            onValueChange = { textSize = it },
                            valueRange = 12f..72f,
                            steps = 11
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.preview_custom_text_hint)) },
                    placeholder = { Text(simpleSampleText) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(Modifier.height(16.dp))

                // style selector (segmented buttons) - two rows
                Text(stringResource(R.string.label_style), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                val regularRow = listOf(PreviewStyle.Regular, PreviewStyle.Medium, PreviewStyle.Bold) // regular styles
                val italicRow = listOf(PreviewStyle.Italic, PreviewStyle.MediumItalic, PreviewStyle.BoldItalic) // italicized styles

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    regularRow.forEachIndexed { index, style ->
                        SegmentedButton(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            shape = SegmentedButtonDefaults.itemShape(index, regularRow.size),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text(
                                stringResource(style.labelRes),
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    italicRow.forEachIndexed { index, style ->
                        SegmentedButton(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            shape = SegmentedButtonDefaults.itemShape(index, italicRow.size),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text(
                                stringResource(style.labelRes),
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val currentTypeface = deriveTypeface(previewTypeface, boldTypeface)
                Spacer(Modifier.height(16.dp))

                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        val header = stringResource(selectedStyle.labelRes)
                        Text(
                            header,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if ((selectedStyle == PreviewStyle.Bold || selectedStyle == PreviewStyle.BoldItalic) && boldFontName != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                boldFontName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val displayText = customText.ifBlank { defaultSampleText }
                        AndroidView(
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    text = displayText
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                                    setTextColor(onSurfaceColor)
                                    typeface = currentTypeface
                                }
                            },
                            update = { tv ->
                                tv.text = displayText
                                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                                tv.setTextColor(onSurfaceColor)
                                tv.typeface = currentTypeface
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.preview_alphabet),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        val alphabetText = stringResource(R.string.sample_alphabet)
                        AndroidView(
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    text = alphabetText
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                                    setTextColor(onSurfaceColor)
                                    typeface = currentTypeface
                                }
                            },
                            update = { tv ->
                                tv.text = alphabetText
                                tv.setTextColor(onSurfaceColor)
                                tv.typeface = currentTypeface
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.preview_numbers_symbols),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        val numbersText = "0123456789\n!@#\$%^&*()_+-=[]{}|;':\",./<>?"
                        AndroidView(
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    text = numbersText
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                                    setTextColor(onSurfaceColor)
                                    typeface = currentTypeface
                                }
                            },
                            update = { tv ->
                                tv.text = numbersText
                                tv.setTextColor(onSurfaceColor)
                                tv.typeface = currentTypeface
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installedFonts by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var shizukuAuthorized by remember { mutableStateOf(false) }
    var shizukuRunning by remember { mutableStateOf(false) }

    val filteredFonts = installedFonts.filterNot {
        it.endsWith(".foundation") || it.endsWith(".samsungone") || it.endsWith(".roboto")
    }

    fun refreshFonts() {
        scope.launch {
            isRefreshing = true
            installedFonts = FontInstallerUtils.getInstalledCustomFonts(context)
            isRefreshing = false
        }
    }
    
    fun checkShizukuStatus() {
        val running = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        shizukuRunning = running
        shizukuAuthorized = if (running) ShizukuAPI.hasPermission() else false
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkShizukuStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val uninstallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { 
        scope.launch {
            delay(500)
            refreshFonts()
        }
    }
    LaunchedEffect(Unit) { refreshFonts() }
    val fontTypefaces = remember { mutableStateMapOf<String, AndroidTypefaceLegacy?>() }
    val extractedPreviewFiles = remember { mutableStateMapOf<String, File>() }
    LaunchedEffect(installedFonts) {
        val uninstalledPackages = extractedPreviewFiles.keys - installedFonts.toSet()
        uninstalledPackages.forEach { pkg ->
            extractedPreviewFiles[pkg]?.let { file ->
                try { file.delete() } catch (_: Exception) {}
            }
            extractedPreviewFiles.remove(pkg)
            fontTypefaces.remove(pkg)
        }
        installedFonts.forEach { pkg ->
            if (!fontTypefaces.containsKey(pkg)) {
                try {
                    val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                    val apkFile = File(appInfo.sourceDir)
                    val ttfFile = withContext(Dispatchers.IO) { FontInstallerUtils.extractFontPreview(apkFile, context.cacheDir) }
                    if (ttfFile != null) {
                        extractedPreviewFiles[pkg] = ttfFile
                        val tf = try { withContext(Dispatchers.IO) { AndroidTypefaceLegacy.createFromFile(ttfFile) } } catch (_: Exception) { null }
                        fontTypefaces[pkg] = tf
                    } else {
                        fontTypefaces[pkg] = null
                    }
                } catch (_: Exception) {
                    fontTypefaces[pkg] = null
                }
            }
        }
    }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(androidx.compose.ui.res.stringResource(R.string.title_manage), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(bottom = 16.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.rescue_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.rescue_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            try { context.startActivity(Intent("com.samsung.settings.FontStyleActivity")) }
                            catch (_: Exception) { Toast.makeText(context, context.getString(R.string.toast_cannot_open_settings), Toast.LENGTH_SHORT).show() }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.rescue_button))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(androidx.compose.ui.res.stringResource(R.string.section_about), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    val versionName = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        } catch (_: Exception) {
                            "Unknown"
                        }
                    }
                    Text(androidx.compose.ui.res.stringResource(R.string.app_name) + " " + versionName, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(androidx.compose.ui.res.stringResource(R.string.section_installed_fonts), style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.size(48.dp), Alignment.Center) {
                            if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = { refreshFonts() }) { Icon(Icons.Default.Refresh, null) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (filteredFonts.isNotEmpty()) {
                        filteredFonts.forEach { pkg ->
                            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(pkg.substringAfterLast(".").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, style = MaterialTheme.typography.bodyLarge)
                                        Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        fontTypefaces[pkg]?.let { tf ->
                                            Spacer(Modifier.height(4.dp))
                                            val previewText = getRandomSimpleText(LocalContext.current)
                                            AndroidView(
                                                factory = { ctx -> makeSampleTextView(ctx, 14f, onSurfaceColor, previewText) },
                                                update = { tv -> tv.typeface = tf; tv.text = previewText; tv.setTextColor(onSurfaceColor) },
                                                modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                    IconButton({
                                        scope.launch {
                                            if (ShizukuAPI.shouldUseShizuku(context)) {
                                                val success = ShizukuAPI.uninstall(pkg)
                                                Toast.makeText(context, context.getString(if (success) R.string.toast_uninstalled else R.string.toast_uninstall_failed), Toast.LENGTH_SHORT).show()
                                                if (success) { delay(500); refreshFonts() }
                                            } else {
                                                uninstallLauncher.launch(Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { data = Uri.parse("package:$pkg") })
                                            }
                                        }
                                    }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    } else Text(androidx.compose.ui.res.stringResource(R.string.no_custom_fonts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(androidx.compose.ui.res.stringResource(R.string.section_shizuku), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    val shizukuInstalled = remember { ShizukuAPI.isInstalled() }
                    LaunchedEffect(shizukuInstalled) { checkShizukuStatus() }
                    val shizukuStatus = when {
                        !shizukuInstalled -> context.getString(R.string.shizuku_not_installed)
                        shizukuRunning && shizukuAuthorized -> context.getString(R.string.shizuku_running_authorized)
                        shizukuRunning -> context.getString(R.string.shizuku_running_unauthorized)
                        else -> context.getString(R.string.shizuku_not_running)
                    }
                    Text(androidx.compose.ui.res.stringResource(R.string.shizuku_status, shizukuStatus), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    if (shizukuInstalled && shizukuRunning && !shizukuAuthorized) {
                        Button(onClick = {
                            ShizukuAPI.requestPermission(
                                onGranted = { shizukuAuthorized = true; Toast.makeText(context, context.getString(R.string.toast_permission_granted), Toast.LENGTH_SHORT).show() },
                                onDenied = { shizukuAuthorized = false; Toast.makeText(context, context.getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show() }
                            )
                        }) { Text(androidx.compose.ui.res.stringResource(R.string.button_request_permission)) }
                    }
                }
            }
        }
    }
}

object FontInstallerUtils {
    private const val TAG = "FontInstaller"
    private const val FONT_PACKAGE_PREFIX = "com.monotype.android.font"

    fun getFileName(context: Context, uri: Uri): String =
        when (uri.scheme) {
            "content" -> context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).coerceAtLeast(0)) ?: "Unknown" else "Unknown"
            } ?: "Unknown"
            else -> File(uri.path ?: "").name
        }

    fun getInstalledCustomFonts(context: Context): List<String> = try {
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName.startsWith(FONT_PACKAGE_PREFIX) }
            .map { it.packageName }
    } catch (e: Exception) { Log.e(TAG, "Failed to get installed fonts", e); emptyList() }

    suspend fun buildAndInstallFont(
        context: Context,
        ttfFile: File,
        displayName: String,
        installLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
        onAlreadyInstalled: () -> Unit,
        onComplete: (Boolean) -> Unit = {},
        boldTtfFile: File? = null,
        onRegisterPending: (String, File) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val outputApk = File(context.cacheDir, "signed_${System.currentTimeMillis()}.apk")
        var packageNameForCleanup: String? = null
        try {
            val fontName = com.je.fontsmanager.samsung.util.PinyinUtils.toSafeAsciiName(displayName)
            if (!ttfFile.exists()) {
                Log.e(TAG, "TTF file does not exist: ${'$'}{ttfFile.absolutePath}")
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }
            if (boldTtfFile != null && !boldTtfFile.exists()) {
                Log.e(TAG, "Bold TTF file does not exist: ${'$'}{boldTtfFile.absolutePath}")
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }
            val config = FontBuilder.FontConfig(
                displayName = displayName,
                fontName = fontName,
                ttfFile = ttfFile,
                boldTtfFile = boldTtfFile
            )
            packageNameForCleanup = config.packageName
            if (isAppInstalled(context, config.packageName)) {
                withContext(Dispatchers.Main) { onAlreadyInstalled() }
                return@withContext
            }
            if (!FontBuilder.buildAndSignFontApk(context, config, outputApk)) {
                Log.e(TAG, "buildAndSignFontApk failed")
                try { CacheCleanupUtils.deleteFiles(outputApk) } catch (_: Exception) {}
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }
            try {
                onRegisterPending(config.packageName, outputApk)
            } catch (_: Exception) {}
            // 始终使用系统安装界面
            withContext(Dispatchers.Main) {
                installApk(context, outputApk, installLauncher)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing font", e)
            try { CacheCleanupUtils.deleteFiles(outputApk) } catch (_: Exception) {}
            withContext(Dispatchers.Main) { onComplete(false) }
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    // under no circumstances should this function be altered or use any other API
    private fun installApk(context: Context, apkFile: File, installLauncher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (!apkFile.exists() || apkFile.length() == 0L) return
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
        installLauncher.launch(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
        })
    }

    fun extractFontPreview(apkFile: File, cacheDir: File): File? {
        return try {
            val zip = java.util.zip.ZipFile(apkFile)
            val entry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".ttf", ignoreCase = true) }
            if (entry != null) {
                val target = File(cacheDir, entry.name.substringAfterLast('/'))
                if (target.exists() && target.length() > 0L) return target
                val tmp = File(cacheDir, "${target.name}.tmp")
                try {
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                } catch (e: Exception) {
                    try { tmp.delete() } catch (_: Exception) {}
                    throw e
                }
                if (target.exists() && target.length() > 0L) target else null
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
