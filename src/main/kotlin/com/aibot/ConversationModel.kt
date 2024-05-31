package com.aibot

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.exception.OpenAITimeoutException
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.logging.Logger
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aibot.Utils.Companion.assistantInitialization
import com.aibot.Utils.Companion.cleanName
import com.aibot.Utils.Companion.shortAssistantInitialization
import com.aibot.aibot.BuildConfig
import com.aibot.domain.models.*
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.inlineQuery
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.*
import com.github.kotlintelegrambot.entities.inlinequeryresults.InlineQueryResult
import com.github.kotlintelegrambot.entities.inlinequeryresults.InputMessageContent
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.extensions.filters.Filter
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import com.github.kotlintelegrambot.entities.User as TgUser

class ConversationModel(
    private val chatHistoryAdapter: ChatHistoryAdapter
) {
    private val botContext = MainScope()
    private val openAI = OpenAI(
        config = OpenAIConfig(
            BuildConfig.openAiApiKey, LoggingConfig(LogLevel.None, logger = Logger.Empty), timeout = Timeout(10.minutes)
        )
    )

    private val adminUsers = listOf(
        User(99064756L, "Yanis", UserPermission.ADMIN, Model.GPT_3_5_TURBO),
        User(2107387576L, "Arthur", UserPermission.ADMIN, Model.GPT_3_5_TURBO),
        User(114765204L, "NotAntony", UserPermission.ADMIN, Model.GPT_3_5_TURBO)
    )

//    75508016L,
//    835713285L,
//    230655321L,
//    607811624L,
//    201213389L,
//    180096477L,
//    215930580L,
//    1867042039L,
//    251400536L,
//    6743863631L

    private val inlineQuestion = HashMap<Long, Pair<String, String>>()

    private suspend fun handleUpdate(
        coroutineContext: CoroutineContext = Dispatchers.Main,
        event: Pair<Update, Bot>,
        handler: SuspendBotAction
    ) {
        botContext.launch(coroutineContext) {
            val update = event.first
            val bot = event.second
            update.message?.let { message ->
                message.from?.let { from ->
                    val user = chatHistoryAdapter.getUser(from.id)
                    if (user == null) {
                        bot.sendMessage(ChatId.fromId(from.id), "Contact Yanis")
                        return@let
                    }
                    handler(
                        bot,
                        from,
                        user,
                        (message.text.orEmpty() + message.caption.orEmpty()).takeIf { it.isNotBlank() },
                        message.replyToMessage,
                        message.photo?.last()?.fileId,
                        message.sticker?.thumb?.fileId
                    )
                }
            }
        }
    }

    private suspend fun inlineResponder(bot: Bot) {
        inlineQuestion.forEach {
            if (inlineQuestion[it.key]?.first.isNullOrBlank()) {
                return@forEach
            }
            val user = chatHistoryAdapter.getUser(it.key) ?: return@forEach
            val data = inlineQuestion[it.key]
            inlineQuestion[it.key] = "" to (inlineQuestion[it.key]?.second ?: "")
            val prediction =
                requestChatPrediction(
                    listOf(ChatMessage(ChatRole.User, data?.first?.trim())),
                    user,
                    true
                )
            val result = listOf(
                InlineQueryResult.Article(
                    id = "0",
                    title = "Result without question",
                    inputMessageContent = InputMessageContent.Text(prediction.content ?: "_empty_"),
                    description = "Response for ${data?.first}: ${prediction.content ?: "_empty_"}",
                ),
                InlineQueryResult.Article(
                    id = "1",
                    title = "Result with question",
                    inputMessageContent = InputMessageContent.Text(
                        ("Question: ${data?.first}\nResponse: " + (prediction.content ?: "_empty_"))
                    ),
                    description = "Response for ${data?.first}: ${prediction.content ?: "_empty_"}",
                )
            )
            if (inlineQuestion[it.key]?.second == data?.second && data?.second != null) {
                print("Good response: ${data.second}")
                bot.answerInlineQuery(data.second, result)
            }
        }
        delay(10 * 1000)
        inlineResponder(bot)
    }

    fun run() {
        adminUsers.forEach {
            chatHistoryAdapter.addUser(it)
        }

        runBlocking {
            val models = openAI.models()
            models.forEach {
                if (it.id.id.contains("gpt"))
                    println(it)
            }
        }

        val bot = bot {
            token = BuildConfig.botApiKey
            dispatch {
                command("start") {
                    if (message.chat.type == "private") {
                        handleUpdate(Dispatchers.Default, update to bot, start)
                    }
                }
                command("send") {
                    if (message.chat.type == "private" || message.from?.username == "frogsrop") {
                        handleUpdate(Dispatchers.Default, update to bot, send)
                    }
                }
                command("list_models") {
                    if (message.chat.type == "private") {
                        handleUpdate(Dispatchers.Default, update to bot, listModels)
                    }
                }
                command("change_model") {
                    if (message.chat.type == "private") {
                        handleUpdate(Dispatchers.Default, update to bot, changeModel)
                    }
                }
                for (entry in Model.entries) {
                    callbackQuery(
                        callbackData = "MODEL_${entry.ordinal}"
                    ) {
                        val id = callbackQuery.from.id
                        val user = chatHistoryAdapter.getUser(id)
                        if (user == null) {
                            bot.sendMessage(ChatId.fromId(id), "Contact Yanis")
                            return@callbackQuery
                        }
                        val model = Model.entries[callbackQuery.data.substring("MODEL_".length).toInt()]!!
                        chatHistoryAdapter.addUser(user.copy(model = model))
                        bot.sendMessage(ChatId.fromId(user.userId), text = "New model:\n${model.id}: ${model.description}")
                    }
                }
                message(Filter.Private and (Filter.Reply or Filter.Text or Filter.Photo or Filter.Sticker) and !Filter.Command) {
                    handleUpdate(Dispatchers.Default, update to bot, conversation)
                }
                inlineQuery {
                    val queryText = inlineQuery.query

                    if (queryText.isBlank() or queryText.isEmpty()) return@inlineQuery

                    inlineQuestion[inlineQuery.from.id] = Pair(queryText, inlineQuery.id)
                }
            }
        }

        val commands = mutableListOf(
            BotCommand("start", "Start conversation or reset context"),
            BotCommand("list_models", "Show list of available models"),
            BotCommand("change_model", "Change model")
        )
        print("setMyCommands result: ${bot.setMyCommands(commands)}")
        botContext.launch(Dispatchers.IO) { inlineResponder(bot) }
        bot.startPolling()
    }

    private suspend fun requestChatPrediction(
        messages: List<ChatMessage>,
        user: User,
        short: Boolean = false
    ): ChatMessage {
        val toSendMessages = messages.takeLast(10).toMutableList()
        toSendMessages.add(0, if (short) shortAssistantInitialization else assistantInitialization)

        val completionRequest = ChatCompletionRequest(
            model = ModelId(user.model.id),
            messages = toSendMessages,
            temperature = 0.7
        )

        return try {
            val completion = openAI.chatCompletion(completionRequest)
            completion.choices.firstNotNullOf { it.message }
        } catch (e: OpenAITimeoutException) {
            ChatMessage(ChatRole.System, "Request timed out. Try sending it again or paraphrase", "System")
        }

    }

    private val send: SuspendBotAction = { bot: Bot,
                                           from: TgUser,
                                           user: User,
                                           text: String?,
                                           reply: Message?,
                                           photo: String?,
                                           sticker: String?
        ->
        withContext(Dispatchers.Default) {
            val content = text!!.substring("/send".length).trim()
            val idx = content.indexOf(' ')
            val id = content.substring(0, idx).trim()
            val msg = content.substring(idx).trim()
            bot.sendMessage(ChatId.fromId(id.toLong()), msg)
        }
    }

    private val listModels: SuspendBotAction = { bot: Bot,
                                                 from: TgUser,
                                                 user: User,
                                                 text: String?,
                                                 reply: Message?,
                                                 photo: String?,
                                                 sticker: String?
        ->
        withContext(Dispatchers.Default) {
            val models = Model.entries.map {
                "${it.id}: ${it.description}"
            }.joinToString("\n\n")
            bot.sendMessage(ChatId.fromId(user.userId), models)
        }
    }
    private val changeModel: SuspendBotAction = { bot: Bot,
                                                  from: TgUser,
                                                  user: User,
                                                  text: String?,
                                                  reply: Message?,
                                                  photo: String?,
                                                  sticker: String?
        ->
        withContext(Dispatchers.Default) {
            val content = text!!.substring("/change_model".length).trim()
            val ids = Model.entries.map { it.id }
            if (content.isEmpty() || content !in ids) {
                val gpt_3_5 = InlineKeyboardButton.CallbackData(
                    text = Model.GPT_3_5_TURBO.id,
                    callbackData = "MODEL_${Model.GPT_3_5_TURBO.ordinal}"
                )
                val gpt_4o = InlineKeyboardButton.CallbackData(text = Model.GPT_4O.id, callbackData = "MODEL_${Model.GPT_4O.ordinal}")
                val gpt_4 = InlineKeyboardButton.CallbackData(text = Model.GPT_4.id, callbackData ="MODEL_${Model.GPT_4.ordinal}")
                val gpt_4_turbo =
                    InlineKeyboardButton.CallbackData(text = Model.GPT_4_TURBO.id, callbackData = "MODEL_${Model.GPT_4_TURBO.ordinal}")

                val markup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(gpt_3_5, gpt_4o),
                        listOf(gpt_4, gpt_4_turbo)
                    )
                )
                bot.sendMessage(ChatId.fromId(user.userId), text = "Select model", replyMarkup = markup)
                return@withContext
            }
            val model = Model.from(content)!!
            chatHistoryAdapter.addUser(user.copy(model = model))
            bot.sendMessage(ChatId.fromId(user.userId), text = "New model set:\n ${model.id}: ${model.description}")
        }
    }
    private val start: SuspendBotAction =
        { bot: Bot,
          from: TgUser,
          user: User,
          text: String?,
          reply: Message?,
          photo: String?,
          sticker: String? ->
            withContext(Dispatchers.Default) {
                val name = cleanName(from.firstName, from.username)
                val user = user.copy(name = name)
                chatHistoryAdapter.deleteMessages(user.userId)
                chatHistoryAdapter.addUser(user)

                val helloResponse = requestChatPrediction(
                    listOf(
                        ChatMessage(
                            role = ChatRole.User,
                            content = "Hello, who are you?",
                            name = user.name.ifEmpty { null }
                        )
                    ),
                    user
                )

                chatHistoryAdapter.addMessage(
                    MessageData(user.userId, helloResponse.content ?: "", "", Role.from(ChatRole.Assistant))
                )

                bot.sendMessage(
                    ChatId.fromId(user.userId),
                    "${helloResponse.content} \nrunning on: ${user.model.id}"
                )
            }
        }

    private val conversation: SuspendBotAction =
        { bot: Bot,
          from: TgUser,
          user: User,
          text: String?,
          reply: Message?,
          photo: String?,
          sticker: String? ->
            withContext(Dispatchers.Default) {
                val userMessage = text?.trim() ?: ""

                text?.let {
                    val currentMessage = MessageData(user.userId, it, "", Role.USER)
                    chatHistoryAdapter.addMessage(currentMessage)
                }
//                val history = chatHistoryAdapter.getMessages(user.userId)

                photo?.let {
                    val msg = MessageData(user.userId, "", it, Role.USER)
                    chatHistoryAdapter.addMessage(msg)
                }

                sticker?.let {
                    val msg = MessageData(user.userId, "", it, Role.USER)
                    chatHistoryAdapter.addMessage(msg)
                }

                println(
                    """${user.name}\n
                       |$userMessage
                    """.trimMargin()
                )
                BuildConfig.historyChatId?.let {
                    val result = (user.name) +
                            (from.username?.let { " username=$it" } ?: "") +
                            " id=" + user.userId + ":"
//                    + "\n" + userMessage
                    bot.sendMessage(ChatId.fromId(it), result)
                }

                // if reply exists
//                reply?.let { reply ->
//                    val rRole = if (reply.from?.id == from.id) ChatRole.User else ChatRole.Assistant
//                    val rName = if (reply.from?.id == from.id) name else null
//                    val rText = ((reply.caption ?: "") + " " + (reply.text ?: "")).trim()
//                    val replyChatMessage = ChatMessage(role = rRole, content = rText, name = rName)
//                    val replyChat = listOf(assistantInitialization, replyChatMessage, currentMessage)
//                    val answer = requestChatPrediction(replyChat, from.id in adminUsers.map { it.userId })
//                    chatHistoryAdapter.addMessage(from.id, answer)
//                    bot.sendMessage(ChatId.fromId(from.id), answer.content ?: "")
//                    return@withContext
//                }
                val history = chatHistoryAdapter.getMessages(bot, user.userId)

                val answer = requestChatPrediction(history, user)
                answer.content?.let {
                    chatHistoryAdapter.addMessage(MessageData(user.userId, it, "", Role.ASSISTANT))
                } ?: bot.sendMessage(ChatId.fromId(user.userId), "No response...", parseMode = ParseMode.MARKDOWN)
                bot.sendMessage(ChatId.fromId(user.userId), answer.content ?: "", parseMode = ParseMode.MARKDOWN)
            }
        }
}
typealias SuspendBotAction = suspend (Bot, TgUser, User, String?, Message?, String?, String?) -> Unit