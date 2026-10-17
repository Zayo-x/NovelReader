package com.example.novelreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Book::class, Chapter::class, ComicPage::class, Bookmark::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun comicPageDao(): ComicPageDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v2 → v3：只新增漫画页面表，保留已有书籍与阅读进度 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `comic_pages` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookId` INTEGER NOT NULL, " +
                        "`chapter_index` INTEGER NOT NULL, " +
                        "`page_index` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`width` INTEGER NOT NULL, " +
                        "`height` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_comic_pages_bookId_chapter_index_page_index` " +
                        "ON `comic_pages` (`bookId`, `chapter_index`, `page_index`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "novel_reader.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // 其他旧版本 schema 不兼容时直接重建，避免升级崩溃
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
