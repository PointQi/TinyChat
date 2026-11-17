package io.tinychat.data.db

import androidx.room.migration.Migration

/**
 * 迁移登记处。**每次改表结构必须**：① `TinyChatDatabase.VERSION` +1；
 * ② 在这里加一条 `Migration(n, n+1)`；③ 提交新生成的 schema JSON（M1-1 验收项）。
 *
 * v1 是初始版本，没有迁移。故意不在任何地方用 `fallbackToDestructiveMigration()`：
 * 聊天记录是用户资产，宁可启动时报错也不能悄悄清库。
 */
object TinyChatMigrations {
    val ALL: Array<Migration> = emptyArray()
}
