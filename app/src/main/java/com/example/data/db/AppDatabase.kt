package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectFolderEntity::class,
        VideoProjectEntity::class,
        SubtitleCueEntity::class,
        VoiceTranslationEntity::class,
        StudyNotebookEntity::class,
        StudyAttachmentEntity::class,
        StudyScheduleItemEntity::class,
        StudyMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voice_translations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceLanguageCode TEXT NOT NULL,
                        sourceLanguageName TEXT NOT NULL,
                        targetLanguageCode TEXT NOT NULL,
                        targetLanguageName TEXT NOT NULL,
                        originalText TEXT NOT NULL,
                        translatedText TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS project_folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        accentKey TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL("ALTER TABLE video_projects ADD COLUMN folderId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_projects_folderId ON video_projects(folderId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_notebooks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        preferredMethod TEXT NOT NULL,
                        dailyMinutes INTEGER NOT NULL,
                        totalDays INTEGER NOT NULL,
                        summaryMarkdown TEXT NOT NULL,
                        keyPointsText TEXT NOT NULL,
                        linkedProjectId INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        notebookId INTEGER NOT NULL,
                        displayName TEXT NOT NULL,
                        attachmentType TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        localFilePath TEXT NOT NULL,
                        extractedText TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(notebookId) REFERENCES study_notebooks(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_attachments_notebookId ON study_attachments(notebookId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_schedule_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        notebookId INTEGER NOT NULL,
                        dayNumber INTEGER NOT NULL,
                        dayLabel TEXT NOT NULL,
                        timeSlot TEXT NOT NULL,
                        taskTitle TEXT NOT NULL,
                        taskDetail TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        FOREIGN KEY(notebookId) REFERENCES study_notebooks(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_schedule_items_notebookId ON study_schedule_items(notebookId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        notebookId INTEGER NOT NULL,
                        isUser INTEGER NOT NULL,
                        messageText TEXT NOT NULL,
                        isOfflineGenerated INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(notebookId) REFERENCES study_notebooks(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_messages_notebookId ON study_messages(notebookId)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bayan_translations.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
