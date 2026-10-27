package ru.daniil.tracklist.domain

import java.util.Locale

object TrackRules {
    fun songCount(count: Int): String {
        val word =
            when {
                count % 100 in 11..14 -> "песен"
                count % 10 == 1 -> "песня"
                count % 10 in 2..4 -> "песни"
                else -> "песен"
            }
        return "$count $word"
    }

    fun key(text: String): String = text.trim().lowercase(Locale.ROOT)

    fun duration(milliseconds: Long): String {
        val seconds = milliseconds.coerceAtLeast(0) / 1000
        return "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
    }

    fun parseDuration(text: String): Long? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull() ?: return null
        val seconds = parts[1].toLongOrNull() ?: return null
        if (minutes !in 0..5999 || seconds !in 0..59) return null
        return (minutes * 60 + seconds) * 1000
    }

    fun validate(title: String, artist: String, time: String): String? =
        when {
            title.isBlank() -> "Укажите название песни"
            title.trim().length > 160 -> "Название должно содержать не более 160 символов"
            artist.isBlank() -> "Укажите исполнителя"
            artist.trim().length > 160 -> "Имя исполнителя должно содержать не более 160 символов"
            parseDuration(time) == null -> "Длительность укажите в формате мин:сек, например 3:25"
            else -> null
        }

    fun matches(title: String, artist: String, album: String, query: String): Boolean {
        val term = key(query)
        return term.isEmpty() || listOf(title, artist, album).any { key(it).contains(term) }
    }
}
