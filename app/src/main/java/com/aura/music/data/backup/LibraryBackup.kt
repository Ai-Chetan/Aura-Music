package com.aura.music.data.backup

import com.aura.music.data.db.SongWithTags
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned JSON backup of the library.
 *
 * Every song entry carries its [BackupSongEntry.sourceUrl], which is all that
 * is needed to re-download it from YouTube later — local file paths and
 * thumbnails are intentionally NOT stored (they are device-specific).
 *
 * Example:
 * ```
 * {
 *   "app": "aura", "version": 1, "exportedAt": 1758100000000,
 *   "songs": [
 *     { "title": "...", "artist": "...",
 *       "sourceUrl": "https://www.youtube.com/watch?v=...",
 *       "sourcePlatform": "youtube", "durationMs": 214000,
 *       "bitrateKbps": 160, "audioFormat": "opus",
  *       "tags": [{"name": "sad", "colorHex": "#2563EB"}] }
 *   ]
 * }
 * ```
 */
const val BACKUP_VERSION = 1

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

object LibraryBackup {

    fun exportJson(
        songs: List<SongWithTags>,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
        root.put("app", "aura")
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", exportedAt)

        val songsJson = JSONArray()
        songs.forEach { item ->
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

            val tagsJson = JSONArray()
            item.tags.forEach { tag ->
                val tagObj = JSONObject()
                tagObj.put("name", tag.name)
                if (tag.colorHex.isNullOrBlank()) {
                    tagObj.put("colorHex", JSONObject.NULL)
                } else {
                    tagObj.put("colorHex", tag.colorHex)
                }
                tagsJson.put(tagObj)
            }
            obj.put("tags", tagsJson)
            songsJson.put(obj)
        }
        root.put("songs", songsJson)
        return root.toString(2)
    }

    /**
     * Parses an import file. Lenient on shape, strict on identity: entries
     * without a [BackupSongEntry.sourceUrl] are collected into
     * [ParsedBackup.invalid] with a reason instead of failing the whole file.
     */
    fun parseImport(raw: String): Result<ParsedBackup> {
        return try {
            val root = JSONObject(raw)
            if (root.optString("app", "aura") != "aura") {
                return Result.failure(Exception("Not an Aura library file."))
            }
            val version = root.optInt("version", BACKUP_VERSION)
            if (version != BACKUP_VERSION) {
                return Result.failure(
                    Exception("Unsupported backup version $version (this app reads v$BACKUP_VERSION).")
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
                val tags = mutableListOf<BackupTag>()
                val tagsJson = obj.optJSONArray("tags")
                if (tagsJson != null) {
                    for (j in 0 until tagsJson.length()) {
                        val tagObj = tagsJson.optJSONObject(j) ?: continue
                        val name = tagObj.optString("name", "").trim()
                        if (name.isEmpty()) continue
                        val color = tagObj.optString("colorHex", null)
                            ?.takeIf { it.isNotBlank() && it != "null" }
                        tags.add(BackupTag(name = name, colorHex = color))
                    }
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
                        tags = tags
                    )
                )
            }
            Result.success(ParsedBackup(entries = entries, invalid = invalid))
        } catch (e: Exception) {
            Result.failure(Exception("Couldn't read that file as JSON: ${e.message}"))
        }
    }
}

data class ParsedBackup(
    val entries: List<BackupSongEntry>,
    val invalid: List<String>
)
