package com.aibot

suspend fun main() {
//    val bot = TelegramBot(
//        BuildConfig.botApiKey,
//        "com.aibot.controller"
//    ) { BotConfiguration(inputListener = BotInputListenerMapImpl()).logging { botLogLevel = Level.ALL } }
//    bot.handleUpdates()
    val bot = ConversationModel()
    bot.run()
}
