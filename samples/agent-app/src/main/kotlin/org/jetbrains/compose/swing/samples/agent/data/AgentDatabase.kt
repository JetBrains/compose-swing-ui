package org.jetbrains.compose.swing.samples.agent.data

import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

/** Everything the app keeps between runs. Its suspend functions run on [Dispatchers.IO]. */
@Database(
    entities = [
        SessionEntity::class,
        TurnEntity::class,
        ItemEntity::class,
        AllowedToolEntity::class,
        SettingsRecord::class,
        WorkspaceRecord::class,
    ],
    version = 1,
)
internal abstract class AgentDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun transcriptDao(): TranscriptDao

    abstract fun settingsDao(): SettingsDao

    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        /** Opens the database in [file], creating it and its folder when they are missing. */
        fun open(
            file: Path,
            queryContext: CoroutineContext = Dispatchers.IO,
        ): AgentDatabase {
            Files.createDirectories(file.toAbsolutePath().parent)
            return Room
                .databaseBuilder<AgentDatabase>(file.toAbsolutePath().toString())
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(queryContext)
                .build()
        }
    }
}
