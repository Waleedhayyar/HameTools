package com.ham.tools.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ham.tools.data.model.QslTemplate
import com.ham.tools.data.model.QsoLog

/**
 * Room database for HamTools app
 * 
 * Contains tables for QSO logs and QSL templates
 */
@Database(
    entities = [QsoLog::class, QslTemplate::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun qsoLogDao(): QsoLogDao
    
    abstract fun qslTemplateDao(): QslTemplateDao
    
    companion object {
        const val DATABASE_NAME = "hamtools.db"
    }
}
