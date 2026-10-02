package com.autoskip.helper.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RuleEntity::class, LogEntity::class, CondRuleEntity::class, WidgetRuleEntity::class, DeletedServerRule::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun logDao(): LogDao
    abstract fun condRuleDao(): CondRuleDao
    abstract fun widgetRuleDao(): WidgetRuleDao
    abstract fun deletedServerRuleDao(): DeletedServerRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v3 → v4：新增控件规则表 */
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

        /** v4 → v5：规则表加 serverId/source；新增墓碑表 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // rules 加列
                db.execSQL("ALTER TABLE rules ADD COLUMN serverId INTEGER")
                db.execSQL("ALTER TABLE rules ADD COLUMN source TEXT NOT NULL DEFAULT 'user'")
                // cond_rules 加列
                db.execSQL("ALTER TABLE cond_rules ADD COLUMN serverId INTEGER")
                db.execSQL("ALTER TABLE cond_rules ADD COLUMN source TEXT NOT NULL DEFAULT 'user'")
                // widget_rules 加列
                db.execSQL("ALTER TABLE widget_rules ADD COLUMN serverId INTEGER")
                db.execSQL("ALTER TABLE widget_rules ADD COLUMN source TEXT NOT NULL DEFAULT 'user'")
                // 墓碑表
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS deleted_server_rules (" +
                        "serverId INTEGER PRIMARY KEY NOT NULL, " +
                        "deletedAt INTEGER NOT NULL)"
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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    // ★ 仅对 v1/v2 老用户破坏性迁移（无法追溯其表结构）；
                    //   v3/v4 用户走正常迁移，数据不丢
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
