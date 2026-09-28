package com.aura.music.data.backup

import com.aura.music.data.db.SavedTrackWithTags
import com.aura.music.data.db.SongWithTags
import com.aura.music.data.db.TagEntity
import com.aura.music.domain.repository.PlaylistExportEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON backup of the whole library.
 *
 * Every song and bookmark entry carries its source URL, which is all that
 * is needed to bring it back later — local file paths and thumbnails are
 * intentionally NOT stored (they are device-specific).
 *
 * - v1: songs (+ their tags) only.
 * - v2: adds standalone `tags`, saved streaming bookmarks (`savedTracks`,
 *   with their tags) and `playlists` (members referenced by URL, resolved
 *   against the current library on import).
 *
 * Example:
 * ```
 * {
 *   "app": "aura", "version": 2, "exportedAt": 1758100000000,
 *   "songs": [ { "title": "...", "sourceUrl": "https://...", "tags": [...] } ],
 *   "savedTracks": [ { "title": "...", "url": "https://...", "tags": [...] } ],
 *   "tags": [ { "name": "sad", "colorHex": "#2563EB" } ],
 *   "playlists": [ { "name": "Gym", "songUrls": ["..."], "savedUrls": ["..."] } ]
 * }
 * ```
 */
const val BACKUP_VERSION = 2

data class BackupTag(
    val name: String,
    val colorHex: String?
)

data class BackupSongEntry(
    val title: String,
    val artist: String?,
    val sourceUrl: String,
    val sourcePlatform: String,
    val durationMs: Long,
    val bitrateKbps: Int?,
    val audioFormat: String,
    val tags: List<BackupTag>
)

/** One saved streaming bookmark in a backup file. */
data class BackupSavedEntry(
    val title: String,
    val artist: String?,
    val url: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val tags: List<BackupTag>
)

data class ParsedBackup(
    val entries: List<BackupSongEntry>,
    val invalid: List<String>,
    val savedEntries: List<BackupSavedEntry> = emptyList(),
    val tagDefinitions: List<BackupTag> = emptyList(),
    val playlists: List<PlaylistExportEntry> = emptyList()
)

object LibraryBackup {

    fun exportJson(
        songs: List<SongWithTags>,
        saved: List<SavedTrackWithTags> = emptyList(),
        tagDefinitions: List<TagEntity> = emptyList(),
        playlists: List<PlaylistExportEntry> = emptyList(),
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
        root.put("app", "aura")
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", exportedAt)

        val songsJson = JSONArray()
        songs.forEach { item ->
            songsJson.put(songObject(item))
        }
        root.put("songs", songsJson)

        val savedJson = JSONArray()
        saved.forEach { item ->
            val t = item.track
            val obj = JSONObject()
            obj.put("title", t.title)
            if (t.artist.isNullOrBlank()) obj.put("artist", JSONObject.NULL)
            else obj.put("artist", t.artist)
            // Re-stream key — canonical watch URL.
            obj.put("url", t.url)
            obj.put("durationMs", t.durationMs)
            if (t.thumbnailUrl.isNullOrBlank()) obj.put("thumbnailUrl", JSONObject.NULL)
            else obj.put("thumbnailUrl", t.thumbnailUrl)
            obj.put("tags", tagsJson(item.tags))
            savedJson.put(obj)
        }
        root.put("savedTracks", savedJson)

        val tagsJson = JSONArray()
        tagDefinitions.forEach { tag ->
            val obj = JSONObject()
            obj.put("name", tag.name)
            if (tag.colorHex.isNullOrBlank()) obj.put("colorHex", JSONObject.NULL)
            else obj.put("colorHex", tag.colorHex)
            tagsJson.put(obj)
        }
        root.put("tags", tagsJson)

        val playlistsJson = JSONArray()
        playlists.forEach { playlist ->
            val obj = JSONObject()
            obj.put("name", playlist.name)
            obj.put("songUrls", JSONArray(playlist.songUrls))
            obj.put("savedUrls", JSONArray(playlist.savedUrls))
            playlistsJson.put(obj)
        }
        root.put("playlists", playlistsJson)

        return root.toString(2)
    }

    private fun songObject(item: SongWithTags): JSONObject {
        val s = item.song
        val obj = JSONObject()
        obj.put("title", s.title)
        if (s.artist.isNullOrBlank()) {
            obj.put("artist", JSONObject.NULL)
        } else {
            obj.put("artist", s.artist)
        }
        // Re-download key — canonical watch URL.
        obj.put("sourceUrl", s.sourceUrl)
        obj.put("sourcePlatform", s.sourcePlatform)
        obj.put("durationMs", s.durationMs)
        if (s.bitrateKbps != null) obj.put("bitrateKbps", s.bitrateKbps)
        obj.put("audioFormat", s.audioFormat)
        obj.put("tags", tagsJson(item.tags))
        return obj
    }

    private fun tagsJson(tags: List<com.aura.music.data.db.TagEntity>): JSONArray {
        val tagsJson = JSONArray()
        tags.forEach { tag ->
            val tagObj = JSONObject()
            tagObj.put("name", tag.name)
            if (tag.colorHex.isNullOrBlank()) {
                tagObj.put("colorHex", JSONObject.NULL)
            } else {
                tagObj.put("colorHex", tag.colorHex)
            }
            tagsJson.put(tagObj)
        }
        return tagsJson
    }

    /**
     * Parses an import file. Lenient on shape, strict on identity: entries
     * without a usable URL are collected into [ParsedBackup.invalid] with a
     * reason instead of failing the whole file. v1 files (songs only) and
     * v2 files both parse.
     */
    fun parseImport(raw: String): Result<ParsedBackup> {
        return try {
            val root = JSONObject(raw)
            if (root.optString("app", "aura") != "aura") {
                return Result.failure(Exception("Not an Aura library file."))
            }
            val version = root.optInt("version", BACKUP_VERSION)
            if (version !in 1..BACKUP_VERSION) {
                return Result.failure(
                    Exception("This backup needs a newer Aura to open. Update the app and try again.")
                )
            }
            val songsJson = root.optJSONArray("songs")
                ?: return Result.failure(Exception("Backup has no \"songs\" list."))

            val entries = mutableListOf<BackupSongEntry>()
            val invalid = mutableListOf<String>()
            for (i in 0 until songsJson.length()) {
                val obj = songsJson.optJSONObject(i) ?: continue
                val sourceUrl = obj.optString("sourceUrl", "").trim()
                if (sourceUrl.isEmpty()) {
                    val label = obj.optString("title", "entry #${i + 1}")
                    invalid.add("\"$label\" has no link — skipped.")
                    continue
                }
                entries.add(
                    BackupSongEntry(
                        title = obj.optString("title", "Unknown Title")
                            .takeIf { it.isNotBlank() } ?: "Unknown Title",
                        artist = obj.optString("artist", null)
                            ?.takeIf { it.isNotBlank() && it != "null" },
                        sourceUrl = sourceUrl,
                        sourcePlatform = obj.optString("sourcePlatform", "youtube")
                            .takeIf { it.isNotBlank() } ?: "youtube",
                        durationMs = obj.optLong("durationMs", 0L),
                        bitrateKbps = obj.optInt("bitrateKbps", -1).takeIf { it > 0 },
                        audioFormat = obj.optString("audioFormat", "")
                            .takeIf { it.isNotBlank() } ?: "unknown",
                        tags = parseTags(obj.optJSONArray("tags"))
                    )
                )
            }

            // v2 sections (absent in v1 files — parse leniently).
            val savedEntries = mutableListOf<BackupSavedEntry>()
            root.optJSONArray("savedTracks")?.let { savedJson ->
                for (i in 0 until savedJson.length()) {
                    val obj = savedJson.optJSONObject(i) ?: continue
                    val url = obj.optString("url", "").trim()
                    if (url.isEmpty()) {
                        val label = obj.optString("title", "bookmark #${i + 1}")
                        invalid.add("Saved \"$label\" has no link — skipped.")
                        continue
                    }
                    savedEntries.add(
                        BackupSavedEntry(
                            title = obj.optString("title", "Unknown Title")
                                .takeIf { it.isNotBlank() } ?: "Unknown Title",
                            artist = obj.optString("artist", null)
                                ?.takeIf { it.isNotBlank() && it != "null" },
                            url = url,
                            durationMs = obj.optLong("durationMs", 0L),
                            thumbnailUrl = obj.optString("thumbnailUrl", null)
                                ?.takeIf { it.isNotBlank() && it != "null" },
                            tags = parseTags(obj.optJSONArray("tags"))
                        )
                    )
                }
            }

            val tagDefinitions = mutableListOf<BackupTag>()
            root.optJSONArray("tags")?.let { tagsJson ->
                for (i in 0 until tagsJson.length()) {
                    val obj = tagsJson.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val color = obj.optString("colorHex", null)
                        ?.takeIf { it.isNotBlank() && it != "null" }
                    tagDefinitions.add(BackupTag(name = name, colorHex = color))
                }
            }

            val playlists = mutableListOf<PlaylistExportEntry>()
            root.optJSONArray("playlists")?.let { playlistsJson ->
                for (i in 0 until playlistsJson.length()) {
                    val obj = playlistsJson.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    playlists.add(
                        PlaylistExportEntry(
                            name = name,
                            songUrls = obj.optStringArray("songUrls"),
                            savedUrls = obj.optStringArray("savedUrls")
                        )
                    )
                }
            }

            Result.success(
                ParsedBackup(
                    entries = entries,
                    invalid = invalid,
                    savedEntries = savedEntries,
                    tagDefinitions = tagDefinitions,
                    playlists = playlists
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Couldn't read that file — it doesn't look like an Aura backup."))
        }
    }

    private fun parseTags(array: JSONArray?): List<BackupTag> {
        if (array == null) return emptyList()
        val tags = mutableListOf<BackupTag>()
        for (j in 0 until array.length()) {
            val tagObj = array.optJSONObject(j) ?: continue
            val name = tagObj.optString("name", "").trim()
            if (name.isEmpty()) continue
            val color = tagObj.optString("colorHex", null)
                ?.takeIf { it.isNotBlank() && it != "null" }
            tags.add(BackupTag(name = name, colorHex = color))
        }
        return tags
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        val urls = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) urls.add(value)
        }
        return urls
    }
}
