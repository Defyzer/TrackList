package ru.daniil.tracklist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.daniil.tracklist.ui.MainViewModel
import ru.daniil.tracklist.ui.TrackListApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TrackListApplication).container
        setContent {
            val model: MainViewModel =
                viewModel(
                    factory =
                        object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                MainViewModel(
                                    container.repository,
                                    container.settings,
                                    container.backup,
                                )
                                    as T
                        }
                )
            TrackListApp(model)
        }
    }
}
