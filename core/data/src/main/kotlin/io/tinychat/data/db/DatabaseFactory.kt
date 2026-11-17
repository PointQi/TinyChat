package io.tinychat.data.db

import android.content.Context
import androidx.room.Room

/**
 * 数据库构建入口。依赖装配走手写 `AppContainer`（决策 21），这里只负责建实例。
 */
object DatabaseFactory {

    fun create(context: Context, name: String = TinyChatDatabase.NAME): TinyChatDatabase =
        Room.databaseBuilder(context, TinyChatDatabase::class.java, name)
            .addMigrations(*TinyChatMigrations.ALL)
            .build()

    /** 测试用：进程内内存库 */
    fun createInMemory(context: Context): TinyChatDatabase =
        Room.inMemoryDatabaseBuilder(context, TinyChatDatabase::class.java)
            .addMigrations(*TinyChatMigrations.ALL)
            .build()
}
