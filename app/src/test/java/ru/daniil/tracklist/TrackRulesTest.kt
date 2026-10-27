package ru.daniil.tracklist

import org.junit.Assert.*
import org.junit.Test
import ru.daniil.tracklist.data.TrackDto
import ru.daniil.tracklist.domain.TrackRules

class TrackRulesTest {
    @Test
    fun durationFormatsMinutesAndSeconds() {
        assertEquals("0:00", TrackRules.duration(0))
        assertEquals("3:25", TrackRules.duration(205000))
        assertEquals("61:05", TrackRules.duration(3665000))
        assertEquals("0:00", TrackRules.duration(-1))
    }

    @Test
    fun durationParsingRejectsInvalidInput() {
        assertEquals(205000L, TrackRules.parseDuration("3:25"))
        listOf("abc", "3:60", "-1:00", "1:2:3", "6000:00", "1:-2").forEach {
            assertNull(TrackRules.parseDuration(it))
        }
    }

    @Test
    fun requiredFieldsAreValidated() {
        assertNotNull(TrackRules.validate(" ", "Автор", "3:00"))
        assertNotNull(TrackRules.validate("Песня", " ", "3:00"))
        assertNotNull(TrackRules.validate("Песня", "Автор", "3:70"))
        assertNull(TrackRules.validate("Песня", "Автор", "0:00"))
    }

    @Test
    fun searchMatchesCyrillicWithoutCaseAndIgnoresSpaces() {
        assertTrue(TrackRules.matches("Утро у моря", "Группа", "Альбом", " МОРЯ "))
        assertTrue(TrackRules.matches("Песня", "Группа", "Альбом", "ГРУП"))
        assertFalse(TrackRules.matches("Песня", "Группа", "Альбом", "вечер"))
    }

    @Test
    fun dtoSkipsMissingRequiredFieldsAndAcceptsMissingOptionalFields() {
        assertNull(TrackDto(trackId = 1, trackName = "Песня").toSong())
        assertNull(TrackDto(trackId = -1, trackName = "Песня", artistName = "Автор").toSong())
        val song = TrackDto(trackId = 12, trackName = " Песня ", artistName = "Автор").toSong()!!
        assertEquals("itunes:12", song.id)
        assertEquals("Песня", song.title)
        assertEquals(0L, song.durationMs)
    }
}
