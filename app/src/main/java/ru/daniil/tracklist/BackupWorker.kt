package ru.daniil.tracklist

import android.content.Context
import android.util.AtomicFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val backup = (applicationContext as TrackListApplication).container.backup
            try {
                val atomic = AtomicFile(File(applicationContext.filesDir, "tracklist_backup.json"))
                val bytes = backup.exportJson().toByteArray(Charsets.UTF_8)
                val stream = atomic.startWrite()
                try {
                    stream.write(bytes)
                    atomic.finishWrite(stream)
                } catch (error: IOException) {
                    atomic.failWrite(stream)
                    throw error
                }
                Result.success()
            } catch (_: IOException) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
}
