package com.je.fontsmanager.samsung.ui

import android.content.Context
import android.graphics.Typeface as AndroidTypefaceLegacy
import android.net.Uri
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

data class LocalFontItem(
    val uri: Uri,
    val fileName: String,
    val lastModified: Long,
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

    companion object {
        private const val PREFS_NAME = "library_prefs"
        private const val KEY_FOLDER_URI = "folder_uri"
    }

    fun restoreFolderUri(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_FOLDER_URI, null)
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            // Verify the persisted permission is still valid
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (hasPermission) {
                folderUri = uri
            } else {
                // Clear invalid stored URI
                prefs.edit().remove(KEY_FOLDER_URI).apply()
            }
        }
    }

    fun setFolderUri(context: Context, uri: Uri) {
        // Persist the permission
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        // Save to prefs
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .apply()

        folderUri = uri
    }

    fun toggleSortOrder() {
        sortOrder = if (sortOrder == SortOrder.NAME) SortOrder.DATE else SortOrder.NAME
        fontList = sortFonts(fontList)
    }

    fun scanFonts(context: Context) {
        val uri = folderUri ?: return
        viewModelScope.launch {
            isScanning = true
            val items = withContext(Dispatchers.IO) {
                val result = mutableListOf<LocalFontItem>()
                val root = DocumentFile.fromTreeUri(context, uri) ?: return@withContext result
                scanDirectory(root, result)
                result
            }
            fontList = sortFonts(items)
            isScanning = false
        }
    }

    private fun scanDirectory(dir: DocumentFile, result: MutableList<LocalFontItem>) {
        dir.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDirectory(file, result)
            } else if (file.name?.endsWith(".ttf", ignoreCase = true) == true) {
                result.add(
                    LocalFontItem(
                        uri = file.uri,
                        fileName = file.name ?: "unknown.ttf",
                        lastModified = file.lastModified()
                    )
                )
            }
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
                    // Copy to cache and create Typeface
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

    /**
     * Load font into HomeViewModel for detailed preview and navigate to FontPreviewScreen.
     * Returns a cached File for the font, or null on failure.
     */
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

    private fun sortFonts(items: List<LocalFontItem>): List<LocalFontItem> {
        return when (sortOrder) {
            SortOrder.NAME -> items.sortedBy { it.fileName.lowercase() }
            SortOrder.DATE -> items.sortedByDescending { it.lastModified }
        }
    }
}
