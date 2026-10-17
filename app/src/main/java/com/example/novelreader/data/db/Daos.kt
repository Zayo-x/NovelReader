package com.example.novelreader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadTime DESC")
    fun observeAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: Long): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapter_index ASC")
    fun observeChapters(bookId: Long): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapter_index ASC")
    suspend fun getChapters(bookId: Long): List<Chapter>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapter_index = :index")
    suspend fun getChapter(bookId: Long, index: Int): Chapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<Chapter>)

    @Update
    suspend fun update(chapter: Chapter)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}

@Dao
interface ComicPageDao {
    @Query("SELECT * FROM comic_pages WHERE bookId = :bookId ORDER BY chapter_index ASC, page_index ASC")
    fun observePages(bookId: Long): Flow<List<ComicPage>>

    @Query("SELECT * FROM comic_pages WHERE bookId = :bookId ORDER BY chapter_index ASC, page_index ASC")
    suspend fun getPages(bookId: Long): List<ComicPage>

    @Query("SELECT COUNT(*) FROM comic_pages WHERE bookId = :bookId")
    suspend fun countPages(bookId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<ComicPage>)

    @Query("DELETE FROM comic_pages WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapterIndex ASC, position ASC")
    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}
