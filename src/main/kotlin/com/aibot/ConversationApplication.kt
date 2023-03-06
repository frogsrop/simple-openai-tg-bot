package com.aibot

import com.aibot.aibot.BuildConfig
import eu.vendeli.tgbot.TelegramBot
suspend fun main() {
    val bot = TelegramBot(BuildConfig.botApiKey, "com.aibot.controller") {
        context {        }
    }
    bot.handleUpdates()
}
