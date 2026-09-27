package com.kelele.manliu

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "albums")
data class ComicAlbum(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    val progressPage: Int = 0,
    val progressOffset: Int = 0,
    @ColumnInfo(defaultValue = "0") val progressPageId: Long = 0,
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = ComicAlbum::class,
        parentColumns = ["id"],
        childColumns = ["albumId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("albumId"), Index(value = ["importItemId"], unique = true)],
)
data class ComicPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val albumId: Long,
    val position: Int,
    val fileName: String,
    val originalName: String,
    val width: Int,
    val height: Int,
    // Kept to read databases created before chapter controls were removed.
    val chapterTitle: String? = null,
    val importItemId: Long? = null,
)

data class AlbumOverview(
    val id: Long,
    val title: String,
    val progressPage: Int,
    val pageCount: Int,
    val coverName: String?,
)

@Entity(
    tableName = "import_jobs",
    foreignKeys = [ForeignKey(
        entity = ComicAlbum::class, parentColumns = ["id"], childColumns = ["albumId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("albumId")],
)
data class ImportJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val albumId: Long,
    val folderUri: String,
    val status: String = "QUEUED",
    val total: Int,
    val processed: Int = 0,
    val imported: Int = 0,
    val failed: Int = 0,
    val message: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "import_items",
    foreignKeys = [ForeignKey(
        entity = ImportJob::class, parentColumns = ["id"], childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("taskId"), Index(value = ["taskId", "sequence"], unique = true),
        Index(value = ["taskId", "status", "sequence"])],
)
data class ImportItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val sequence: Int,
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val status: String = "PENDING",
    val error: String? = null,
)

@Dao
interface ComicDao {
    @Query("SELECT * FROM albums ORDER BY sortOrder ASC, createdAt DESC, id DESC")
    fun observeAlbums(): Flow<List<ComicAlbum>>

    @Query("""
        SELECT a.id, a.title, a.progressPage,
            (SELECT COUNT(*) FROM pages p WHERE p.albumId = a.id) AS pageCount,
            (SELECT p.fileName FROM pages p WHERE p.albumId = a.id
             ORDER BY p.position, p.id LIMIT 1) AS coverName
        FROM albums a ORDER BY a.sortOrder ASC, a.createdAt DESC, a.id DESC
    """)
    fun observeOverviews(): Flow<List<AlbumOverview>>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    fun observeAlbum(id: Long): Flow<ComicAlbum?>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun findAlbum(id: Long): ComicAlbum?

    @Query("SELECT * FROM albums ORDER BY sortOrder ASC, createdAt DESC, id DESC")
    suspend fun getAlbums(): List<ComicAlbum>

    @Insert
    suspend fun addAlbum(album: ComicAlbum): Long

    @Query("UPDATE albums SET sortOrder = sortOrder + :count")
    suspend fun makeRoomForAlbums(count: Int)

    @Query("UPDATE albums SET sortOrder = :position WHERE id = :id")
    suspend fun changeAlbumOrder(id: Long, position: Int)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun removeAlbum(id: Long)

    @Query("UPDATE albums SET progressPage = :page, progressOffset = :offset, progressPageId = :pageId WHERE id = :id")
    suspend fun saveProgress(id: Long, page: Int, offset: Int, pageId: Long)

    @Query("SELECT * FROM pages WHERE albumId = :albumId ORDER BY position ASC, id ASC")
    fun observePages(albumId: Long): Flow<List<ComicPage>>

    @Query("SELECT * FROM pages WHERE albumId = :albumId ORDER BY position ASC, id ASC")
    suspend fun getPages(albumId: Long): List<ComicPage>

    @Query("SELECT * FROM pages WHERE importItemId = :itemId LIMIT 1")
    suspend fun pageForImportItem(itemId: Long): ComicPage?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM pages WHERE albumId = :albumId")
    suspend fun nextPosition(albumId: Long): Int

    @Insert
    suspend fun addPage(page: ComicPage): Long

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun removePage(id: Long)

    @Query("DELETE FROM pages WHERE albumId = :albumId AND id IN (:ids)")
    suspend fun removePages(albumId: Long, ids: List<Long>)

    @Query("UPDATE pages SET position = position - 1 WHERE albumId = :albumId AND position > :position")
    suspend fun closeGap(albumId: Long, position: Int)

    @Query("UPDATE pages SET position = :position WHERE id = :id")
    suspend fun changePosition(id: Long, position: Int)

    @Query("UPDATE pages SET position = position + 1 WHERE albumId = :albumId AND position >= :start AND position < :end")
    suspend fun shiftPagesDown(albumId: Long, start: Int, end: Int)

    @Query("UPDATE pages SET position = position - 1 WHERE albumId = :albumId AND position > :start AND position <= :end")
    suspend fun shiftPagesUp(albumId: Long, start: Int, end: Int)

    @Insert
    suspend fun addImportJob(job: ImportJob): Long

    @Insert
    suspend fun addImportItems(items: List<ImportItem>)

    @Query("SELECT * FROM import_jobs WHERE albumId = :albumId ORDER BY id DESC LIMIT 1")
    fun observeLatestImport(albumId: Long): Flow<ImportJob?>

    @Query("SELECT * FROM import_jobs WHERE id = :id LIMIT 1")
    suspend fun findImportJob(id: Long): ImportJob?

    @Query("SELECT * FROM import_jobs WHERE albumId = :albumId AND status IN ('QUEUED', 'RUNNING', 'PAUSED') AND processed < total ORDER BY id DESC LIMIT 1")
    suspend fun unfinishedImport(albumId: Long): ImportJob?

    @Query("SELECT * FROM import_jobs WHERE status IN ('QUEUED', 'RUNNING') ORDER BY id LIMIT 1")
    suspend fun nextQueuedImport(): ImportJob?

    @Query("SELECT * FROM import_items WHERE taskId = :taskId AND status = 'PENDING' ORDER BY sequence LIMIT 1")
    suspend fun nextPendingItem(taskId: Long): ImportItem?

    @Query("SELECT * FROM import_items WHERE taskId = :taskId AND status = 'FAILED' ORDER BY sequence")
    fun observeFailedItems(taskId: Long): Flow<List<ImportItem>>

    @Query("UPDATE import_items SET status = :status, error = :error WHERE id = :itemId")
    suspend fun markImportItem(itemId: Long, status: String, error: String?)

    @Query("UPDATE import_jobs SET processed = processed + 1, imported = imported + :imported, failed = failed + :failed, updatedAt = :now WHERE id = :taskId")
    suspend fun recordImportResult(taskId: Long, imported: Int, failed: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE import_jobs SET status = :status, updatedAt = :now WHERE id = :taskId")
    suspend fun setImportStatus(taskId: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE import_jobs SET status = :status, message = :message, updatedAt = :now WHERE id = :taskId")
    suspend fun setImportState(taskId: Long, status: String, message: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE import_jobs SET status = 'PAUSED', message = :message WHERE status = 'RUNNING'")
    suspend fun pauseRunningImports(message: String)

    @Query("UPDATE import_items SET status = 'PENDING', error = NULL WHERE taskId = :taskId AND status = 'FAILED'")
    suspend fun resetFailedItems(taskId: Long)

    @Query("UPDATE import_jobs SET processed = imported, failed = 0, status = 'QUEUED', updatedAt = :now WHERE id = :taskId")
    suspend fun resetFailedCounter(taskId: Long, now: Long = System.currentTimeMillis())
}

@Database(
    entities = [ComicAlbum::class, ComicPage::class, ImportJob::class, ImportItem::class],
    version = 3,
    exportSchema = false,
)
abstract class ComicDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE albums ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            UPDATE albums SET sortOrder = (
                SELECT COUNT(*) FROM albums AS prior
                WHERE prior.createdAt > albums.createdAt
                   OR (prior.createdAt = albums.createdAt AND prior.id > albums.id)
            )
        """.trimIndent())
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE albums ADD COLUMN progressPageId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE pages ADD COLUMN chapterTitle TEXT")
        db.execSQL("ALTER TABLE pages ADD COLUMN importItemId INTEGER")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pages_importItemId ON pages (importItemId)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS import_jobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, albumId INTEGER NOT NULL,
                folderUri TEXT NOT NULL, status TEXT NOT NULL, total INTEGER NOT NULL,
                processed INTEGER NOT NULL, imported INTEGER NOT NULL, failed INTEGER NOT NULL,
                message TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                FOREIGN KEY(albumId) REFERENCES albums(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_import_jobs_albumId ON import_jobs (albumId)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS import_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL,
                sequence INTEGER NOT NULL, uri TEXT NOT NULL, name TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL, status TEXT NOT NULL, error TEXT,
                FOREIGN KEY(taskId) REFERENCES import_jobs(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_import_items_taskId ON import_items (taskId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_import_items_taskId_sequence ON import_items (taskId, sequence)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_import_items_taskId_status_sequence ON import_items (taskId, status, sequence)")
    }
}
