package com.aura.music.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeUrlsTest {

    @Test
    fun watchUrl_isSingleVideo() {
        val url = "https://www.youtube.com/watch?v=xRb8hxwN5zc"
        assertTrue(YoutubeUrls.isYouTubeUrl(url))
        assertFalse(YoutubeUrls.isPlaylistUrl(url))
        assertFalse(YoutubeUrls.isVideoWithPlaylist(url))
        assertEquals("xRb8hxwN5zc", YoutubeUrls.extractVideoId(url))
        assertEquals(url, YoutubeUrls.canonicalUrl(url))
    }

    @Test
    fun watchUrlWithList_offersPlaylistOption() {
        val url = "https://www.youtube.com/watch?v=xRb8hxwN5zc&list=PLAk1PHcom15Uabc123XYZ"
        assertTrue(YoutubeUrls.isYouTubeUrl(url))
        assertFalse(YoutubeUrls.isPlaylistUrl(url))
        assertTrue(YoutubeUrls.isVideoWithPlaylist(url))
        assertEquals(
            "https://www.youtube.com/playlist?list=PLAk1PHcom15Uabc123XYZ",
            YoutubeUrls.canonicalPlaylistUrl(url)
        )
        // Duplicate detection still keys on the single video.
        assertEquals(
            "https://www.youtube.com/watch?v=xRb8hxwN5zc",
            YoutubeUrls.canonicalUrl(url)
        )
    }

    @Test
    fun purePlaylistUrl_isBatchImport() {
        val url = "https://www.youtube.com/playlist?list=PLAk1PHcom15Uabc123XYZ"
        assertTrue(YoutubeUrls.isYouTubeUrl(url))
        assertTrue(YoutubeUrls.isPlaylistUrl(url))
        assertFalse(YoutubeUrls.isVideoWithPlaylist(url))
        assertEquals(url, YoutubeUrls.canonicalPlaylistUrl(url))
    }

    @Test
    fun shortAndShareUrls_parseVideoId() {
        assertEquals(
            "xRb8hxwN5zc",
            YoutubeUrls.extractVideoId("https://youtu.be/xRb8hxwN5zc")
        )
        assertEquals(
            "xRb8hxwN5zc",
            YoutubeUrls.extractVideoId("https://www.youtube.com/shorts/xRb8hxwN5zc")
        )
        assertFalse(
            YoutubeUrls.isVideoWithPlaylist("https://youtu.be/xRb8hxwN5zc")
        )
    }

    @Test
    fun nonVideoUrls_areRejected() {
        assertFalse(
            YoutubeUrls.isYouTubeUrl("https://www.youtube.com/@somuchannel")
        )
        assertFalse(YoutubeUrls.isYouTubeUrl("https://example.com/song.mp3"))
        assertFalse(YoutubeUrls.isYouTubeUrl(""))
        assertNull(YoutubeUrls.extractVideoId("https://www.youtube.com/@somuchannel"))
    }
}
