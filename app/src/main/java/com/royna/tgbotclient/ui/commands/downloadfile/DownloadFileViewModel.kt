package com.royna.tgbotclient.ui.commands.downloadfile

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.royna.tgbotclient.data.BotRepository
import com.royna.tgbotclient.util.FileUtils.copyFromExt
import com.royna.tgbotclient.util.FileUtils.queryFileName
import com.royna.tgbotclient.util.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class DownloadFileViewModel : ViewModel() {
    // The URI to write the resulting file
    private var _outFileUri = MutableLiveData<Uri>()
    // Source path in the server
    private val _sourceFilePath = MutableLiveData<String>()
    // Event when a download succeeded
    private val _downloadEvent = MutableSharedFlow<String>()

    val downloadEvent: SharedFlow<String>
        get() = _downloadEvent
    val outFileURI : LiveData<Uri>
        get() = _outFileUri
    val sourceFilePath : LiveData<String>
        get() = _sourceFilePath

    fun setFileUrl(uri: Uri) {
        _outFileUri.value = uri
    }
    fun setSourceFile(path: String) {
        _sourceFilePath.value = path
    }
    private fun openFile(activity: FragmentActivity): Uri {
        val contentUri = _outFileUri.value
            ?: throw IllegalStateException("Output URI not selected")
        val sourcePath = _sourceFilePath.value
            ?: throw IllegalStateException("Source path not set")

        val outFile = File(sourcePath)

        // Logic: Create the file in the SAF directory
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(outFile.extension)
            ?: "application/octet-stream"

        val docFile = DocumentFile.fromTreeUri(activity, contentUri)?.run {
            // If file exists, delete it so we can overwrite cleanly
            findFile(outFile.name)?.let {
                Logging.info("Deleting existing destination file")
                it.delete()
            }
            createFile(mimeType, outFile.name)?.uri
        }

        return docFile ?: throw RuntimeException("Could not create file. Check write permissions.")
    }

    private suspend fun downloadFile(activity: FragmentActivity) = runCatching {
        // 1. Prepare Local Destination (SAF)
        val destinationUri = openFile(activity)

        // 2. Prepare Temp Cache File (Buffer)
        val tempFile = File(activity.cacheDir, "temp_download_${System.currentTimeMillis()}.bin")
        if (tempFile.exists()) tempFile.delete()

        // 3. Identify Remote Path
        // CRITICAL: Send the FULL path to the server, not just the filename
        val remotePath = _sourceFilePath.value!!
        Logging.info("Requesting remote file: $remotePath")

        try {
            // 4. Download to Temp File (gRPC)
            BotRepository.getInstance().downloadFile(remotePath, tempFile).getOrThrow()

            // 5. Copy Temp File -> SAF Destination
            Logging.info("Copying to final destination...")
            activity.contentResolver.openOutputStream(destinationUri)?.use { output ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(output)
                }
            }
            Logging.info("Download complete.")

        } catch (e: Exception) {
            Logging.error("Download failed", e)

            // CLEANUP ON FAILURE: Only delete the user's file if the download failed
            DocumentFile.fromSingleUri(activity, destinationUri)?.delete()
            throw e
        } finally {
            // CLEANUP ALWAYS: Delete the internal temp cache file
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
    fun execute(activity: FragmentActivity) {
        activity.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                downloadFile(activity)
            }.onSuccess {
                Logging.info("File downloaded")
                _downloadEvent.emit("File downloaded")
            }.onFailure {
                Logging.error("Failed to download file", it)
                _downloadEvent.emit("Failed to download file: ${it.message}")
            }
        }
    }
}