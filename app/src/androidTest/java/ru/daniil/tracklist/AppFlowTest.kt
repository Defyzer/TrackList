package ru.daniil.tracklist

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import ru.daniil.tracklist.ui.MainViewModel

class AppFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        ui.waitForIdle()
        // Capture the settled screen after dismissing transient operation notices.
        ui.runOnUiThread {
            ViewModelProvider(ui.activity)[MainViewModel::class.java].clearMessage()
        }
        ui.mainClock.advanceTimeBy(500)
        Thread.sleep(200)
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder =
            File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply {
                mkdirs()
            }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun createEditFavoriteAndPlaylist() {
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("Добавить песню", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        ui.onNodeWithText("Добавить песню", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Название песни").performTextInput("Утро у моря")
        ui.onNodeWithText("Исполнитель").performTextInput("Учебная группа")
        ui.onNodeWithText("Альбом").performTextInput("Демо")
        ui.onNodeWithText("Длительность, мин:сек").performTextClearance()
        ui.onNodeWithText("Длительность, мин:сек").performTextInput("3:25")
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent 4")
            .close()
        screenshot("02_editor")
        ui.onNodeWithText("Сохранить песню").performScrollTo().performClick()
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("Утро у моря").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("01_library")
        ui.onNodeWithText("Утро у моря").performClick()
        ui.onNodeWithText("В избранное").performClick()
        screenshot("03_song")
        ui.onNodeWithText("Редактировать сведения").performClick()
        ui.onNodeWithText("Название песни").performTextClearance()
        ui.onNodeWithText("Название песни").performTextInput("Утро у моря — версия 2")
        ui.onNodeWithText("Сохранить песню").performScrollTo().performClick()
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("Утро у моря — версия 2").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithContentDescription("Назад").performClick()
        ui.onNodeWithText("Избранное").performClick()
        ui.onNodeWithText("Утро у моря — версия 2").assertExists()
        ui.onNodeWithText("Подборки").performClick()
        ui.mainClock.advanceTimeBy(1000)
        ui.waitForIdle()
        Thread.sleep(300)
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("Новая подборка", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        ui.onNodeWithText("Новая подборка", useUnmergedTree = true).performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("Название").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Название").performTextInput("В дорогу")
        ui.onNodeWithText("Описание").performTextInput("Музыка для поездок")
        ui.onNodeWithText("Сохранить").performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("В дорогу").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("В дорогу").performClick()
        ui.onNodeWithText("Добавить из фонотеки").performClick()
        ui.onNodeWithText("+ Утро у моря — версия 2").performClick()
        ui.onNodeWithText("Готово").performClick()
        ui.onNodeWithText("Утро у моря — версия 2").assertExists()
        screenshot("04_playlist")
        ui.onNodeWithContentDescription("Назад").performClick()
        ui.onNodeWithText("Каталог").performClick()
        screenshot("05_catalog")
        ui.onNodeWithText("Настройки").performClick()
        screenshot("06_settings")
        ui.onNodeWithText("Тёмная тема").performClick()
        ui.onNodeWithText("Фонотека").performClick()
        screenshot("07_dark_library")
    }
}
