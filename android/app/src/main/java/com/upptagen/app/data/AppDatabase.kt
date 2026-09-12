package com.upptagen.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.upptagen.app.data.dao.ActiveTimerDao
import com.upptagen.app.data.dao.TagDao
import com.upptagen.app.data.dao.TimeEntryDao
import com.upptagen.app.data.model.ActiveTimerEntity
import com.upptagen.app.data.model.EntryTagCrossRef
import com.upptagen.app.data.model.TagEntity
import com.upptagen.app.data.model.TimeEntryEntity

@Database(
    entities = [
        TimeEntryEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class,
        ActiveTimerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun tagDao(): TagDao
    abstract fun activeTimerDao(): ActiveTimerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "upptagen.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
