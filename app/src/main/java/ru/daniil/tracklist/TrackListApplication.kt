package ru.daniil.tracklist

import android.app.Application
import androidx.room.Room
import androidx.work.*
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.daniil.tracklist.data.*

class AppContainer(app: Application) {
    private val db = Room.databaseBuilder(app, MusicDatabase::class.java, "tracklist.db").build()
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    private val api =
        Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ItunesApi::class.java)
    val repository = MusicRepository(db, api)
    val settings = SettingsStore(app)
    val backup = BackupManager(db)
}

class TrackListApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "library-backup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
    }
}
