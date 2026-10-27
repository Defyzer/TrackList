package ru.daniil.tracklist.data

import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 30,
        @Query("country") country: String = "US",
    ): SearchResponse
}

data class SearchResponse(val results: List<TrackDto>? = null)

data class TrackDto(
    val trackId: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val trackTimeMillis: Long? = null,
    val trackViewUrl: String? = null,
) {
    fun toSong(): Song? {
        val remoteId = trackId?.takeIf { it > 0 } ?: return null
        val name = trackName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val artist = artistName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Song(
            id = "itunes:$remoteId",
            title = name,
            artist = artist,
            album = collectionName.orEmpty(),
            durationMs = (trackTimeMillis ?: 0).coerceAtLeast(0),
            sourceUrl = trackViewUrl.orEmpty(),
        )
    }
}
