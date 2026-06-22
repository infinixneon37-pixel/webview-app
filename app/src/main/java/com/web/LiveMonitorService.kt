package com.web

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.*
import android.util.Log
import android.widget.Toast
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveMonitorService : Service() {

    private val CHANNEL_ID = "LiveMonitorChannel"
    private var wakeLock: PowerManager.WakeLock? = null
    private var executorService: ExecutorService? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val mergingFolders = Collections.synchronizedSet(HashSet<String>())
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnables = ConcurrentHashMap<String, Runnable>()

    override fun onCreate() {
        super.onCreate()
        executorService = Executors.newFixedThreadPool(10)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TangoMerger::CpuWakeLock")
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Live Merger", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this))
            .setContentTitle("Tango Video Recorder")
            .setContentText("Multi-tab downloader aktif...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
        
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val folder = intent?.getStringExtra("folder") ?: return START_STICKY
        val baseDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "TangoRecord")
        val sessionDir = File(baseDir, folder)

        if ("DOWNLOAD_CHUNK" == intent.action) {
            val url = intent.getStringExtra("url")
            val cookie = intent.getStringExtra("cookie")
            val userAgent = intent.getStringExtra("userAgent")
            val filename = intent.getStringExtra("filename") ?: return START_STICKY

            if (!sessionDir.exists()) sessionDir.mkdirs()

            if (executorService?.isShutdown == false && !mergingFolders.contains(folder)) {
                watchdogRunnables[folder]?.let { watchdogHandler.removeCallbacks(it) }

                val newWatchdog = Runnable {
                    if (!mergingFolders.contains(folder) && sessionDir.exists()) {
                        mergingFolders.add(folder)
                        sendBroadcast(Intent("ACTION_STREAM_STOPPED").putExtra("folder", folder))
                        mainHandler.post { Toast.makeText(this, "⚠️ Sinyal sesimu putus 60 dtk. Merakit Video...", Toast.LENGTH_LONG).show() }
                        Thread { mergeAllFilesAndStop(sessionDir, folder) }.start()
                    }
                    watchdogRunnables.remove(folder)
                }

                watchdogRunnables[folder] = newWatchdog
                watchdogHandler.postDelayed(newWatchdog, 60000)

                executorService?.execute { downloadChunkLocally(url, cookie, userAgent, filename, sessionDir) }
            }
        }

        if ("STOP_SERVICE" == intent.action) {
            watchdogRunnables.remove(folder)?.let { watchdogHandler.removeCallbacks(it) }
            if (!mergingFolders.contains(folder) && sessionDir.exists()) {
                mergingFolders.add(folder)
                Thread { mergeAllFilesAndStop(sessionDir, folder) }.start()
            }
        }
        return START_STICKY
    }

    private fun downloadChunkLocally(urlString: String?, cookie: String?, userAgent: String?, filename: String, sessionDir: File) {
        if (urlString == null) return
        try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            cookie?.let { conn.setRequestProperty("Cookie", it) }
            userAgent?.let { conn.setRequestProperty("User-Agent", it) }
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val videoFile = File(sessionDir, filename)
                conn.inputStream.use { input ->
                    FileOutputStream(videoFile, false).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {}
    }

    private fun mergeAllFilesAndStop(sessionDir: File, folderName: String) {
        try {
            val tsFiles = sessionDir.listFiles()?.filter { it.name.endsWith(".ts") || it.name.endsWith(".m4s") }?.map { it.absolutePath } ?: emptyList()

            if (tsFiles.isEmpty()) {
                mergingFolders.remove(folderName)
                return
            }

            val sortedFiles = tsFiles.sortedBy { path ->
                val name = File(path).name.replace("\\D".toRegex(), "")
                if (name.isEmpty()) 0L else name.toLong()
            }

            val listFile = File(sessionDir, "list_video.txt")
            FileOutputStream(listFile).use { fos ->
                sortedFiles.forEach { f -> fos.write("file '$f'\n".toByteArray()) }
            }

            val waktuSekarang = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val privateOutputFile = File(sessionDir, "hasil_$waktuSekarang.mp4")

            val commandArgs = arrayOf("-f", "concat", "-safe", "0", "-i", listFile.absolutePath, "-c", "copy", "-movflags", "+faststart", privateOutputFile.absolutePath, "-y")

            mainHandler.post { Toast.makeText(this, "🎬 Merakit video untuk: $folderName", Toast.LENGTH_SHORT).show() }

            val session = FFmpegKit.executeWithArguments(commandArgs)

            if (ReturnCode.isSuccess(session.returnCode)) {
                sortedFiles.forEach { File(it).delete() }
                listFile.delete()

                val publicMoviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "TangoRecord")
                if (!publicMoviesDir.exists()) publicMoviesDir.mkdirs()

                val publicOutputFile = File(publicMoviesDir, "${folderName}_${privateOutputFile.name}")

                if (copyFile(privateOutputFile, publicOutputFile)) {
                    privateOutputFile.delete()
                    deleteRecursive(sessionDir)

                    MediaScannerConnection.scanFile(this, arrayOf(publicOutputFile.absolutePath), arrayOf("video/mp4")) { path, _ ->
                        Log.i("MediaScanner", "Video terdaftar: $path")
                    }

                    mainHandler.post { Toast.makeText(this, "✅ Sesi $folderName Sukses Disimpan!", Toast.LENGTH_LONG).show() }
                }
            }
        } catch (e: Exception) {
            Log.e("LiveMonitorService", "Error: ${e.message}")
        } finally {
            mergingFolders.remove(folderName)
        }
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
        fileOrDirectory.delete()
    }

    private fun copyFile(src: File, dst: File): Boolean {
        return try {
            FileInputStream(src).use { input -> FileOutputStream(dst).use { output -> input.copyTo(output) } }
            true
        } catch (e: Exception) { false }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        watchdogHandler.removeCallbacksAndMessages(null)
        executorService?.shutdownNow()
        wakeLock?.let { if (it.isHeld) it.release() }
    }
}
