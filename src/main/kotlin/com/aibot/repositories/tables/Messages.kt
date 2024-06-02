package com.aibot.repositories.tables

import org.ktorm.schema.*

object Messages : Table<Nothing>("messages") {
    val messageId = int("message_id").primaryKey()
    val ts = long("ts")
    val userId = long("user_id")
    val message = text("message")
    val resource = text("resource")
    val role = text("role")
    val usage = float("usage")
    val deleted = boolean("deleted")
}