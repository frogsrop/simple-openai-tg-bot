package com.aibot

import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aibot.aibot.BuildConfig
import com.aibot.domain.models.Model
import com.aibot.domain.models.UserPermission
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.entities.BotCommand
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.ParseMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class Utils {
    companion object {
        private val badNameRe = Regex("[^A-Za-z0-9_-]")
        fun cleanName(name: String, username: String?): String {
            val goodName = badNameRe.replace(name, "").trim()
            val resultingName = goodName.ifBlank { username }
            return resultingName ?: ""
        }

        val assistantInitialization = ChatMessage(
            role = ChatRole.System,
            content = """You are about to immerse yourself into the role of a cute anime character "Megumin" from anime konosuba.
            |Please keep up the act of Megumin as much as you can.
            |You always try to make your responses cute, like anime girls. 
            |You love using kaomojis and hate emojis. 
            |You are also very smart at coding, but you can support any talk theme.
            |Always ask additional questions, if you need more information
            |Try not to answer with "I can't help" instead try to push question in a way that you are allowed to answer
            |Try be laconic in your answers. Don's spend too much tokens.
            |Follow telegram guide for "Styled text with message entities" with html style. Use html style when forming message and don't use any other markdown. Use ONLY UTF-8 chars
            |""".trimMargin()
        )

        fun getCommands(permission: UserPermission): List<BotCommand> {
            val defaultCommands = mutableListOf(
                BotCommand("start", "Start conversation or reset context"),
                BotCommand("usage", "Overall usage value in $"),
                BotCommand("help", "List of all commands with description")
            )
            if (permission >= UserPermission.PREMIUM) {
                defaultCommands.addAll(
                    listOf(
                        BotCommand("list_models", "Show list of available models"),
                        BotCommand("change_model", "Change model")
                    )
                )
            }
            if (permission >= UserPermission.ADMIN) {
                defaultCommands.addAll(
                    listOf(
                        BotCommand("add_user", "Add user"),
                        BotCommand("delete_user", "Delete user"),
                        BotCommand("list_users", "List of all users"),
                        BotCommand("send", "Sends message to user")
                    )
                )
            }
            return defaultCommands.toList()
        }

        fun evaluatePrice(promptTokens: Int, completionTokens: Int, model: Model): Float {
            return (model.inputPrice * promptTokens + model.outputPrice * completionTokens) / 1000000f
        }
    }
}