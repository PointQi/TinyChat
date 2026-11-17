package io.tinychat.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 迁移登记处。**每次改表结构必须**：① `TinyChatDatabase.VERSION` +1；
 * ② 在这里加一条 `Migration(n, n+1)`；③ 提交新生成的 schema JSON（M1-1 验收项）。
 *
 * 故意不在任何地方用 `fallbackToDestructiveMigration()`：
 * 聊天记录是用户资产，宁可启动时报错也不能悄悄清库。
 */
object TinyChatMigrations {

    /**
     * v1 → v2：消息补两个**仅本地**字段
     * - `reminder_snapshot`：该轮 latest_reminder 快照，用于「同一 reminder 不重复插入」（M1-10 规则 5）
     * - `thinking_signature`：本轮思考的 signature，继续生成要回传（M1-9）
     *
     * 两列都不导出，不影响 v3 往返。
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN reminder_snapshot TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN thinking_signature TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
