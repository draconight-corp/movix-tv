package com.example.movix.update

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.movix.BuildConfig
import com.example.movix.R
import com.example.movix.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val OWNER = "draconight-corp"
    private const val REPO = "movix-tv"
    private const val APK_FILE_NAME = "movix-tv-update.apk"

    /**
     * Vérifie GitHub, et si une release plus récente existe, propose l'install à l'utilisateur.
     * Silencieux en cas d'erreur (offline, rate-limit GitHub, etc).
     */
    suspend fun checkAndPrompt(activity: Activity) = coroutineScope {
        val current = BuildConfig.VERSION_NAME
        val release = runCatching {
            withContext(Dispatchers.IO) { ApiClient.github.latestRelease(OWNER, REPO) }
        }.onFailure { Log.w(TAG, "Update check failed: ${it.message}") }.getOrNull() ?: return@coroutineScope

        if (release.draft) return@coroutineScope
        val remote = release.tagName.removePrefix("v")
        if (!isNewer(remote, current)) {
            Log.i(TAG, "Up to date ($current vs $remote)")
            return@coroutineScope
        }

        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        if (apk == null) {
            Log.w(TAG, "Release $remote has no APK asset")
            return@coroutineScope
        }

        if (activity.isFinishing || activity.isDestroyed) return@coroutineScope
        showUpdateDialog(activity, remote, release.body.orEmpty(), apk.browserDownloadUrl)
    }

    private fun showUpdateDialog(activity: Activity, version: String, notes: String, apkUrl: String) {
        val notesPreview = notes.take(400).ifBlank { "Améliorations et corrections." }
        AlertDialog.Builder(activity, R.style.MovixDialog)
            .setTitle("Mise à jour disponible : v$version")
            .setMessage(notesPreview)
            .setPositiveButton("Mettre à jour") { _, _ ->
                downloadAndInstall(activity, apkUrl, version)
            }
            .setNegativeButton("Plus tard", null)
            .setCancelable(true)
            .show()
    }

    private fun downloadAndInstall(context: Context, url: String, version: String) {
        // Nettoie un ancien APK éventuel
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val outFile = File(outDir, APK_FILE_NAME)
        if (outFile.exists()) outFile.delete()

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("Movix TV v$version")
            .setDescription("Téléchargement de la mise à jour…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(outFile))
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = dm.enqueue(req)
        Toast.makeText(context, "Téléchargement en cours…", Toast.LENGTH_SHORT).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                ctx.unregisterReceiver(this)

                val query = DownloadManager.Query().setFilterById(downloadId)
                dm.query(query)?.use { c ->
                    if (c.moveToFirst()) {
                        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            launchInstall(ctx, outFile)
                        } else {
                            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            Toast.makeText(ctx, "Échec du téléchargement (code $reason)", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun launchInstall(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible de lancer l'install : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Compare deux versions semver simplifiées (MAJOR.MINOR.PATCH).
     * Renvoie true si remote > current.
     */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = parseVersion(remote)
        val c = parseVersion(current)
        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val a = r.getOrNull(i) ?: 0
            val b = c.getOrNull(i) ?: 0
            if (a != b) return a > b
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> =
        v.split(".", "-", "+").mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
}
