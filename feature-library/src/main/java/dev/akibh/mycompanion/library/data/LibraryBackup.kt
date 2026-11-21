package dev.akibh.mycompanion.library.data

import dev.akibh.mycompanion.library.domain.ReadingGoal
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * The library in a settings backup: books with their progress, shelves, tags, bookmarks, reading
 * history and goal. File access can't be backed up (Android grants it per install), so restored
 * books show "Access lost" until "Locate file" links them again; progress and bookmarks stay.
 */
class LibraryBackup(val json: JSONObject) {
    val bookCount: Int get() = json.optJSONArray("books")?.length() ?: 0

    // JSONObject compares by identity; two backups are the same when they say the same thing.
    override fun equals(other: Any?): Boolean = other is LibraryBackup && other.json.toString() == json.toString()

    override fun hashCode(): Int = json.toString().hashCode()

    override fun toString(): String = "LibraryBackup($bookCount books)"
}

@Singleton
class LibraryBackups @Inject constructor(private val dao: LibraryDao, private val prefs: LibraryPrefs) {

    suspend fun backup(): LibraryBackup {
        val goal = prefs.currentGoal()
        return LibraryBackup(
            JSONObject()
                .put("version", 1)
                .put(
                    "goal",
                    JSONObject().put("minutesPerDay", goal.minutesPerDay).put("booksPerYear", goal.booksPerYear)
                )
                .put("books", JSONArray(dao.allBooks().map { it.toJson() }))
                .put(
                    "labels",
                    JSONArray(
                        dao.allLabels().map {
                            JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind)
                        }
                    )
                )
                .put(
                    "bookLabels",
                    JSONArray(
                        dao.allBookLabels().map {
                            JSONObject().put("bookId", it.bookId).put("labelId", it.labelId)
                        }
                    )
                )
                .put("bookmarks", JSONArray(dao.allBookmarks().map { it.toJson() }))
                .put(
                    "days",
                    JSONArray(
                        dao.allDays().map {
                            JSONObject().put("bookId", it.bookId).put("day", it.day).put("seconds", it.seconds)
                        }
                    )
                ),
        )
    }

    /** Replaces the library with the backup's. */
    suspend fun restore(backup: LibraryBackup) {
        val json = backup.json
        json.optJSONObject("goal")?.let {
            prefs.setGoal(
                ReadingGoal(it.optInt("minutesPerDay", ReadingGoal().minutesPerDay), it.optInt("booksPerYear", 0))
            )
        }
        dao.replaceAll(
            books = json.objects("books").map { it.toBook() },
            labels = json.objects(
                "labels"
            ).map { LabelEntity(it.getLong("id"), it.getString("name"), it.getString("kind")) },
            links = json.objects("bookLabels").map { BookLabelEntity(it.getLong("bookId"), it.getLong("labelId")) },
            bookmarks = json.objects("bookmarks").map {
                BookmarkEntity(
                    id = it.getLong("id"),
                    bookId = it.getLong("bookId"),
                    locator = it.getString("locator"),
                    label = it.optString("label"),
                    progress = it.optDouble("progress", 0.0),
                    createdAt = it.optLong("createdAt"),
                )
            },
            days = json.objects(
                "days"
            ).map { ReadingDayEntity(it.getLong("bookId"), it.getLong("day"), it.getLong("seconds")) },
        )
    }

    private fun JSONObject.objects(name: String): List<JSONObject> =
        optJSONArray(name)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } }.orEmpty()

    private fun BookEntity.toJson() = JSONObject()
        .put("id", id)
        .put("uri", uri)
        .put("format", format)
        .put("title", title)
        .put("author", author)
        .put("fileName", fileName)
        .put("size", size)
        .put("addedAt", addedAt)
        .put("lastOpenedAt", lastOpenedAt ?: JSONObject.NULL)
        .put("locator", locator ?: JSONObject.NULL)
        .put("progress", progress)
        .put("positions", positions)
        .put("status", status)
        .put("finishedAt", finishedAt ?: JSONObject.NULL)
        .put("coverPath", coverPath ?: JSONObject.NULL)

    private fun JSONObject.toBook() = BookEntity(
        id = getLong("id"),
        uri = getString("uri"),
        format = getString("format"),
        title = getString("title"),
        author = optString("author"),
        // Covers live in app storage: kept on the same install, gone after a reinstall.
        coverPath = optStringOrNull("coverPath")?.takeIf { File(it).exists() },
        fileName = optString("fileName"),
        size = optLong("size"),
        addedAt = optLong("addedAt"),
        lastOpenedAt = optLongOrNull("lastOpenedAt"),
        locator = optStringOrNull("locator"),
        progress = optDouble("progress", 0.0),
        positions = optInt("positions"),
        status = optString("status", "TO_READ"),
        finishedAt = optLongOrNull("finishedAt"),
    )

    private fun BookmarkEntity.toJson() = JSONObject()
        .put("id", id)
        .put("bookId", bookId)
        .put("locator", locator)
        .put("label", label)
        .put("progress", progress)
        .put("createdAt", createdAt)

    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)

    private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name)) null else optLong(name)
}
