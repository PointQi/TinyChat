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

    /**
     * v2 → v3：`attachments` 主键从 `id` 改成 `(id, message_id)`。
     *
     * 原因：真样本里同一个附件会被同一会话的两条提问同时引用（124 个附件里 20 个如此）。
     * 单列主键下后写的那条会把前一条替换掉 —— 导出丢 `export_path`、界面少一个缩略图。
     * SQLite 改主键只能重建表（Room 的 schema 校验也按这个顺序比字段）。
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `attachments_new` (
                    `id` TEXT NOT NULL,
                    `message_id` INTEGER NOT NULL,
                    `file_name` TEXT NOT NULL,
                    `file_size` INTEGER NOT NULL,
                    `mime` TEXT,
                    `local_path` TEXT,
                    `remote_id` TEXT,
                    `is_image` INTEGER NOT NULL,
                    `width` INTEGER,
                    `height` INTEGER,
                    `state` TEXT NOT NULL,
                    PRIMARY KEY(`id`, `message_id`),
                    FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `attachments_new`
                SELECT `id`, `message_id`, `file_name`, `file_size`, `mime`, `local_path`,
                       `remote_id`, `is_image`, `width`, `height`, `state`
                FROM `attachments`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `attachments`")
            db.execSQL("ALTER TABLE `attachments_new` RENAME TO `attachments`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `idx_attachments_msg` ON `attachments` (`message_id`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
