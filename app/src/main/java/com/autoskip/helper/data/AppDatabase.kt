package com.autoskip.helper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RuleEntity::class, LogEntity::class, CondRuleEntity::class, WidgetRuleEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun logDao(): LogDao
    abstract fun condRuleDao(): CondRuleDao
    abstract fun widgetRuleDao(): WidgetRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v3 → v4：新增控件规则表（保留原有数据） */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS widget_rules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "remark TEXT NOT NULL, " +
                        "widgetId TEXT NOT NULL, " +
                        "matchText TEXT, " +
                        "actionType TEXT NOT NULL, " +
                        "coordX INTEGER NOT NULL, " +
                        "coordY INTEGER NOT NULL, " +
                        "packageName TEXT, " +
                        "enabled INTEGER NOT NULL, " +
                        "hitCount INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "autoskip.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
