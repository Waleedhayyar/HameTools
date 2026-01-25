package com.ham.tools.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.ham.tools.data.local.AppDatabase
import com.ham.tools.data.local.QslTemplateDao
import com.ham.tools.data.local.QsoLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for application-level dependencies
 * 
 * Provides singleton instances of database and DAOs
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * Migration from version 1 to 2
     * Adds new columns for extended QSO information
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE qso_logs ADD COLUMN opName TEXT")
            db.execSQL("ALTER TABLE qso_logs ADD COLUMN qth TEXT")
            db.execSQL("ALTER TABLE qso_logs ADD COLUMN gridLocator TEXT")
            db.execSQL("ALTER TABLE qso_logs ADD COLUMN qslInfo TEXT")
            db.execSQL("ALTER TABLE qso_logs ADD COLUMN txPower TEXT")
            db.execSQL("ALTER TABLE qso_logs ADD COLUMN rig TEXT")
            db.execSQL("ALTER TABLE qso_logs ADD COLUMN myGridLocator TEXT")
            db.execSQL("ALTER TABLE qso_logs ADD COLUMN propagation TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE qso_logs ADD COLUMN remarks TEXT")
        }
    }
    
    /**
     * Migration from version 2 to 3
     * Adds QSL templates table
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS qsl_templates (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    backgroundUri TEXT,
                    backgroundColor INTEGER NOT NULL DEFAULT -15066830,
                    canvasWidth INTEGER NOT NULL DEFAULT 1200,
                    canvasHeight INTEGER NOT NULL DEFAULT 800,
                    textElementsJson TEXT NOT NULL DEFAULT '[]',
                    isDefault INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    
    /**
     * Provides the Room database instance
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }
    
    /**
     * Provides the QsoLogDao instance
     */
    @Provides
    @Singleton
    fun provideQsoLogDao(database: AppDatabase): QsoLogDao {
        return database.qsoLogDao()
    }
    
    /**
     * Provides the QslTemplateDao instance
     */
    @Provides
    @Singleton
    fun provideQslTemplateDao(database: AppDatabase): QslTemplateDao {
        return database.qslTemplateDao()
    }
    
    /**
     * Provides FusedLocationProviderClient for GPS location
     */
    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }
}
