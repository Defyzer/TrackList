package ru.daniil.tracklist

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ru.daniil.tracklist.data.ItunesApi

class ApiTest {
    @Test
    fun searchEncodesQueryAndMapsJson() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"results":[{"trackId":7,"trackName":"Song","artistName":"Artist","trackTimeMillis":205000}]}"""
                    )
            )
            val api =
                Retrofit.Builder()
                    .baseUrl(server.url("/"))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ItunesApi::class.java)
            val songs = api.search("Море и ветер").results!!
            val url = server.takeRequest().requestUrl!!
            assertEquals("Море и ветер", url.queryParameter("term"))
            assertEquals("song", url.queryParameter("entity"))
            assertEquals("music", url.queryParameter("media"))
            assertEquals("30", url.queryParameter("limit"))
            assertEquals("itunes:7", songs.single().toSong()!!.id)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun httpErrorIsExposedToCaller() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            val api =
                Retrofit.Builder()
                    .baseUrl(server.url("/"))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ItunesApi::class.java)
            try {
                api.search("song")
                fail("Expected HTTP error")
            } catch (e: retrofit2.HttpException) {
                assertEquals(503, e.code())
            }
        } finally {
            server.shutdown()
        }
    }
}
