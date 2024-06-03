package com.aibot

import com.aibot.repositories.KtormMessagesRepository
import com.aibot.repositories.KtormUsersRepository
import org.ktorm.database.Database

fun main() {
    val database = Database.connect("jdbc:sqlite:sample.db")
    val usersRepository = KtormUsersRepository(database)
    val messagesRepository = KtormMessagesRepository(database)

    val bot = ConversationModel(
        ChatHistoryAdapter(usersRepository, messagesRepository)
    )

    bot.run()
}
