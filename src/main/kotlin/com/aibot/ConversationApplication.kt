package com.aibot

import com.aibot.repositories.MessagesRepositoryImpl
import com.aibot.repositories.UsersRepositoryImpl
import org.ktorm.database.Database

suspend fun main() {
    val database = Database.connect("jdbc:sqlite:..\\..\\sample.db")
    val usersRepository = UsersRepositoryImpl(database)
    val messagesRepository = MessagesRepositoryImpl(database, usersRepository)

    val bot = ConversationModel(
        ChatHistoryManager(usersRepository, messagesRepository)
    )
    bot.run()
}
