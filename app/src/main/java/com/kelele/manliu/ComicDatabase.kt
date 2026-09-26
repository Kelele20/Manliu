package com.kelele.manliu

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "albums")
data class ComicAlbum(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val progressPage: Int = 0,
    val progressOffset: Int = 0,
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = ComicAlbum::class,
        parentColumns = ["id"],
        childColumns = ["albumId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("albumId")],
)
data class ComicPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val albumId: Long,
    val position: Int,
    val fileName: String,
    val originalName: String,
    val width: Int,
    val height: Int,
)

@Dao
interface ComicDao {
    @Query("SELECT * FROM albums ORDER BY createdAt DESC, id DESC")
    fun observeAlbums(): Flow<List<ComicAlbum>>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    fun observeAlbum(id: Long): Flow<ComicAlbum?>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun findAlbum(id: Long): ComicAlbum?

    @Insert
    suspend fun addAlbum(album: ComicAlbum): Long

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun removeAlbum(id: Long)

    @Query("UPDATE albums SET progressPage = :page, progressOffset = :offset WHERE id = :id")
    suspend fun saveProgress(id: Long, page: Int, offset: Int)

    @Query("SELECT * FROM pages WHERE albumId = :albumId ORDER BY position ASC, id ASC")
    fun observePages(albumId: Long): Flow<List<ComicPage>>

    @Query("SELECT * FROM pages WHERE albumId = :albumId ORDER BY position ASC, id ASC")
    suspend fun getPages(albumId: Long): List<ComicPage>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM pages WHERE albumId = :albumId")
    suspend fun nextPosition(albumId: Long): Int

    @Insert
    suspend fun addPage(page: ComicPage): Long

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun removePage(id: Long)

    @Query("UPDATE pages SET position = position - 1 WHERE albumId = :albumId AND position > :position")
    suspend fun closeGap(albumId: Long, position: Int)

    @Query("UPDATE pages SET position = :position WHERE id = :id")
    suspend fun changePosition(id: Long, position: Int)
}

@Database(entities = [ComicAlbum::class, ComicPage::class], version = 1, exportSchema = false)
abstract class ComicDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
}
