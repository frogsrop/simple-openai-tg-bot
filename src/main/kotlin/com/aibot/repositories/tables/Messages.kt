package com.aibot.repositories.tables

import org.ktorm.schema.Table
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.text

object Messages : Table<Nothing>("messages") {
    val messageId = int("message_id").primaryKey()
    val ts = long("ts")
    val userId = long("user_id")
    val message = text("message")
    val role = text("role")
}