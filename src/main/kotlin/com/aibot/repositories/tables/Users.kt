package com.aibot.repositories.tables

import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.varchar

object Users : Table<Nothing>("users") {
    val userId = long("user_id").primaryKey()
    val permission = int("permission")
    val name = varchar("name")
    val model = varchar("model")
}