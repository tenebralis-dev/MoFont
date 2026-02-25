package com.je.fontsmanager.samsung.ui

import android.content.Context
import android.graphics.Typeface as AndroidTypefaceLegacy
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.util.Locale

enum class FontSource { LOCAL, DOWNLOADED }

data class LocalFontItem(
    val uri: Uri,
    val fileName: String,
    val lastModified: Long,
    val source: FontSource = FontSource.LOCAL,
    val typeface: AndroidTypefaceLegacy? = null,
    val isLoading: Boolean = false
)

enum class SortOrder { NAME, DATE }

class LibraryViewModel : ViewModel() {

    var fontList by mutableStateOf<List<LocalFontItem>>(emptyList())
        private set
    var isScanning by mutableStateOf(false)
        private set
    var folderUri by mutableStateOf<Uri?>(null)
        private set
    var sortOrder by mutableStateOf(SortOrder.NAME)
        private set
    var isAscending by mutableStateOf(true)
        private set
    var isDownloading by mutableStateOf(false)
        private set
    var downloadError by mutableStateOf<String?>(null)
        private set

    companion object {
        private const val TAG = "LibraryVM"
        private const val PREFS_NAME = "library_prefs"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val DOWNLOADED_DIR_NAME = "downloaded"
    }

    fun restoreFolderUri(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_FOLDER_URI, null)
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (hasPermission) {
                folderUri = uri
            } else {
                prefs.edit().remove(KEY_FOLDER_URI).apply()
            }
        }
    }

    fun setFolderUri(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                     android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Fallback: read-only
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .apply()
        folderUri = uri
    }

    fun setSortOrder(order: SortOrder, ascending: Boolean) {
        sortOrder = order
        isAscending = ascending
        fontList = sortFonts(fontList)
    }

    fun clearDownloadError() { downloadError = null }

    fun scanFonts(context: Context) {
        val uri = folderUri ?: return
        viewModelScope.launch {
            isScanning = true
            val items = withContext(Dispatchers.IO) {
                val result = mutableListOf<LocalFontItem>()
                val root = DocumentFile.fromTreeUri(context, uri) ?: return@withContext result
                root.listFiles().forEach { file ->
                    if (file.isDirectory) {
                        if (file.name == DOWNLOADED_DIR_NAME) {
                            // Scan downloaded/ subfolder separately
                            scanDirectoryFlat(file, result, FontSource.DOWNLOADED)
                        } else {
                            // Recurse into other subdirectories as local fonts
                            scanDirectory(file, result, FontSource.LOCAL)
                        }
                    } else if (file.name?.endsWith(".ttf", ignoreCase = true) == true) {
                        result.add(
                            LocalFontItem(
                                uri = file.uri,
                                fileName = file.name ?: "unknown.ttf",
                                lastModified = file.lastModified(),
                                source = FontSource.LOCAL
                            )
                        )
                    }
                }
                result
            }
            fontList = sortFonts(items)
            isScanning = false
        }
    }

    private fun scanDirectory(dir: DocumentFile, result: MutableList<LocalFontItem>, source: FontSource) {
        dir.listFiles().forEach { file ->
            if (file.isDirectory) {
                if (file.name != DOWNLOADED_DIR_NAME) {
                    scanDirectory(file, result, source)
                }
            } else if (file.name?.endsWith(".ttf", ignoreCase = true) == true) {
                result.add(
                    LocalFontItem(
                        uri = file.uri,
                        fileName = file.name ?: "unknown.ttf",
                        lastModified = file.lastModified(),
                        source = source
                    )
                )
            }
        }
    }

    private fun scanDirectoryFlat(dir: DocumentFile, result: MutableList<LocalFontItem>, source: FontSource) {
        dir.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDirectoryFlat(file, result, source)
            } else if (file.name?.endsWith(".ttf", ignoreCase = true) == true) {
                result.add(
                    LocalFontItem(
                        uri = file.uri,
                        fileName = file.name ?: "unknown.ttf",
                        lastModified = file.lastModified(),
                        source = source
                    )
                )
            }
        }
    }

    /**
     * Download a font from URL into mofont/downloaded/ subfolder via SAF.
     */
    fun downloadFont(context: Context, urlString: String, onComplete: (Boolean) -> Unit) {
        val rootUri = folderUri ?: run { onComplete(false); return }
        viewModelScope.launch {
            isDownloading = true
            downloadError = null
            val success = withContext(Dispatchers.IO) {
                try {
                    val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext false
                    // Get or create downloaded/ directory
                    val downloadDir = root.findFile(DOWNLOADED_DIR_NAME)
                        ?: root.createDirectory(DOWNLOADED_DIR_NAME)
                        ?: return@withContext false

                    // Derive filename from URL
                    val fileName = urlString.substringAfterLast("/")
                        .substringBefore("?")
                        .let { if (it.endsWith(".ttf", ignoreCase = true)) it else "$it.ttf" }

                    // Check if already exists
                    if (downloadDir.findFile(fileName) != null) {
                        downloadError = "ALREADY_EXISTS"
                        return@withContext false
                    }

                    // Download to temp file first
                    val tempFile = File(context.cacheDir, "dl_${System.currentTimeMillis()}.tmp")
                    try {
                        val url = URL(urlString)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 30000
                        conn.instanceFollowRedirects = true
                        conn.connect()

                        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                            downloadError = "HTTP ${conn.responseCode}"
                            return@withContext false
                        }

                        conn.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Download failed", e)
                        downloadError = e.message
                        try { tempFile.delete() } catch (_: Exception) {}
                        return@withContext false
                    }

                    // Write to SAF
                    try {
                        val newFile = downloadDir.createFile("font/ttf", fileName)
                            ?: return@withContext false
                        context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "SAF write failed", e)
                        downloadError = e.message
                        return@withContext false
                    } finally {
                        try { tempFile.delete() } catch (_: Exception) {}
                    }

                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Download error", e)
                    downloadError = e.message
                    false
                }
            }
            isDownloading = false
            if (success) {
                scanFonts(context) // Refresh list
            }
            withContext(Dispatchers.Main) { onComplete(success) }
        }
    }

    fun loadTypeface(context: Context, index: Int) {
        if (index < 0 || index >= fontList.size) return
        val item = fontList[index]
        if (item.typeface != null || item.isLoading) return

        fontList = fontList.toMutableList().also { it[index] = item.copy(isLoading = true) }

        viewModelScope.launch {
            val tf = withContext(Dispatchers.IO) {
                try {
                    val cacheFile = File(context.cacheDir, "lib_${item.fileName}")
                    context.contentResolver.openInputStream(item.uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                    }
                    AndroidTypefaceLegacy.createFromFile(cacheFile)
                } catch (_: Exception) {
                    null
                }
            }
            val currentIndex = fontList.indexOfFirst { it.uri == item.uri }
            if (currentIndex >= 0) {
                fontList = fontList.toMutableList().also {
                    it[currentIndex] = it[currentIndex].copy(typeface = tf, isLoading = false)
                }
            }
        }
    }

    suspend fun prepareFontForPreview(context: Context, item: LocalFontItem): File? {
        return withContext(Dispatchers.IO) {
            try {
                val baseName = item.fileName.removeSuffix(".ttf").replace(Regex("[^a-zA-Z0-9]"), "")
                val cacheFile = File(context.cacheDir, "${baseName}.ttf")
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                }
                if (cacheFile.exists() && cacheFile.length() > 0L) cacheFile else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private val chineseCollator: Collator = Collator.getInstance(Locale.CHINESE)

    private fun sortFonts(items: List<LocalFontItem>): List<LocalFontItem> {
        // Sort within each group but keep LOCAL before DOWNLOADED
        val localFonts = items.filter { it.source == FontSource.LOCAL }
        val downloadedFonts = items.filter { it.source == FontSource.DOWNLOADED }
        return sortGroup(localFonts) + sortGroup(downloadedFonts)
    }

    private fun sortGroup(items: List<LocalFontItem>): List<LocalFontItem> {
        return when (sortOrder) {
            SortOrder.NAME -> if (isAscending) items.sortedWith(compareBy(chineseCollator) { it.fileName }) else items.sortedWith(compareByDescending(chineseCollator) { it.fileName })
            SortOrder.DATE -> if (isAscending) items.sortedBy { it.lastModified } else items.sortedByDescending { it.lastModified }
        }
    }
}
