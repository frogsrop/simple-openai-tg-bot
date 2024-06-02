package com.aibot

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.core.Usage
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
import com.aibot.Utils.Companion.evaluatePrice
import com.aibot.Utils.Companion.getCommands
import com.aibot.aibot.BuildConfig
import com.aibot.domain.models.*
import com.aibot.domain.models.MessageData.Companion.toChatMessage
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
import com.github.kotlintelegrambot.logging.LogLevel.*
import com.github.kotlintelegrambot.types.TelegramBotResult
import kotlinx.coroutines.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashMap
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import com.github.kotlintelegrambot.entities.User as TgUser

class ConversationModel(
    private val chatHistoryAdapter: ChatHistoryAdapter
) {
    private val mainContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val botContext = CoroutineScope(SupervisorJob() + mainContext)

    private val openAI = OpenAI(
        config = OpenAIConfig(
            BuildConfig.openAiApiKey, LoggingConfig(LogLevel.None, logger = Logger.Empty), timeout = Timeout(10.minutes)
        )
    )

    private val adminUsers = listOf(
        User.buildAdmin(99064756L, "Yanis"),
        User.buildAdmin(2107387576L, "Arthur"),
        User.buildAdmin(114765204L, "NotAntony")
    )

    private val df = DecimalFormat("#.#######", DecimalFormatSymbols(Locale.US))
    private val inlineQuestion = HashMap<Long, Pair<String, String>>()
    fun escapeMarkdown(text: String): String {
        val charactersToEscape = listOf(
            '_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!'
        )

        val stringBuilder = StringBuilder()
        var insideCodeBlock = false

        val lines = text.lines()

        for (line in lines) {
            if (line.startsWith("```")) {
                insideCodeBlock = !insideCodeBlock
                stringBuilder.append(line).append("\n")
                continue
            }

            if (insideCodeBlock) {
                stringBuilder.append(line).append("\n")
            } else {
                for (char in line) {
                    if (char in charactersToEscape) {
                        stringBuilder.append('\\')
                    }
                    stringBuilder.append(char)
                }
                stringBuilder.append("\n")
            }
        }

        return stringBuilder.toString()
    }

    suspend fun Bot.sendMessageWithRetry(
        chatId: Long,
        message: String,
        maxRetries: Int = 5,
        parseMode: ParseMode? = ParseMode.HTML
    ) {
        var parseMode = parseMode
        var attempts = 0
        var success = false
        val originalMessage = message.substring(0)
        var message = message
        var description = ""
        println("sending: \"$message\"")
        while (attempts < maxRetries && !success) {
            try {
                withContext(mainContext) {
                    val response = sendMessage(ChatId.fromId(chatId), message, parseMode = parseMode)
                    success = response.isSuccess
                    response.fold(
                        { res ->
                            println("sent with: ${parseMode?.name}")
                        },
                        { er ->
                            val errorDescription: String? = when (er) {
                                is TelegramBotResult.Error.HttpError -> "${er.httpCode} ${er.description}"
                                is TelegramBotResult.Error.TelegramApi -> "${er.errorCode} ${er.description}"
                                is TelegramBotResult.Error.InvalidResponse -> "${er.httpCode} ${er.httpStatusMessage}"
                                is TelegramBotResult.Error.Unknown -> er.exception.message
                            }
                            println("error: $errorDescription")
                        }
                    )
                }
            } catch (e: Exception) {
                println("Error sending message: ${e.message}")
                e.printStackTrace()
            }
            attempts++
            if (!success) {
                delay(1000L * attempts) // Exponential backoff
            }
            when (attempts) {
                (maxRetries - 1) -> {
                    parseMode = null
                    message = originalMessage
                }

                (maxRetries - 2) -> {
                    message = escapeMarkdown(originalMessage)
                    parseMode = ParseMode.MARKDOWN
                }

                (maxRetries - 3) -> {
                    message = escapeMarkdown(originalMessage)
                    parseMode = ParseMode.MARKDOWN_V2
                }

                (maxRetries - 4) -> {
                    message = originalMessage
                    parseMode = ParseMode.MARKDOWN
                }
            }
        }

        if (!success) {
            println("Failed to send message after $maxRetries attempts:\n${description}")
        }
    }

    suspend fun Bot.logInTg(text: String) {
        BuildConfig.historyChatId?.let {
            sendMessageWithRetry(it, text)
        }
    }

    private suspend fun handleUpdate(
        coroutineContext: CoroutineContext = Dispatchers.Main,
        event: Pair<Update, Bot>,
        handler: SuspendBotAction
    ) {
        botContext.launch(coroutineContext) {
            try {
                val update = event.first
                val bot = event.second
                update.message?.let { message ->
                    message.from?.let { from ->
                        val user = chatHistoryAdapter.getUser(from.id)
                        if (user == null) {
                            bot.logInTg("/start name:\"${from.firstName} ${from.lastName}\" username:\"${(from.username?.let { " username=$it" } ?: "")}\" id=\"${from.id}L\"")
                            bot.sendMessageWithRetry(from.id, "Contact Yanis")
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
            } catch (e: Exception) {
                println("Error handling update: ${e.message}")
                e.printStackTrace()
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
                    bot,
                    listOf(MessageData(user.userId, data?.first?.trim() ?: "", "", Role.USER, 99999f)),
                    user,
                    true
                )
            val result = listOf(
                InlineQueryResult.Article(
                    id = "0",
                    title = "Result without question",
                    inputMessageContent = InputMessageContent.Text(prediction.second ?: "_empty_"),
                    description = "Response for ${data?.first}: ${prediction.second ?: "_empty_"}",
                ),
                InlineQueryResult.Article(
                    id = "1",
                    title = "Result with question",
                    inputMessageContent = InputMessageContent.Text(
                        ("Question: ${data?.first}\nResponse: " + (prediction.second ?: "_empty_"))
                    ),
                    description = "Response for ${data?.first}: ${prediction.second ?: "_empty_"}",
                )
            )
            if (inlineQuestion[it.key]?.second == data?.second && data?.second != null) {
                println("Good response: ${data.second}")
                bot.answerInlineQuery(data.second, result)
            }
        }
        delay(10 * 1000)
        inlineResponder(bot)
    }

    fun run() {
        botContext.launch {
            while (true) {
                val users = chatHistoryAdapter.listUsers()
                for (userInfo in users) {
                    val messages = chatHistoryAdapter.getAllMessages(userInfo.user.userId)
                    if (messages.isNotEmpty()) {
                        val last = messages.last()
                        val dtH = (System.currentTimeMillis() - last.ts) / 1000f / 60f / 60f
                        if (dtH >= 1) {
                            chatHistoryAdapter.deleteMessages(userInfo.user.userId)
                        }
                    }
                }
                delay(5 * 60 * 1000)
//                delay(1000)
            }

        }
        adminUsers.forEach {
            if (!chatHistoryAdapter.hasUser(it.userId)) {
                chatHistoryAdapter.addUser(it)
            }
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
            logLevel = Error
            timeout = 60
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
                command("help") {
                    if (message.chat.type == "private") {
                        handleUpdate(Dispatchers.Default, update to bot, help)
                    }
                }
                command("add_user") {
                    if (message.chat.type == "private") {
                        handleUpdate(Dispatchers.Default, update to bot, addUser)
                    }
                }
                command("delete_user") {
                    if (message.chat.type == "private") {
                        handleUpdate(Dispatchers.Default, update to bot, deleteUser)
                    }
                }
                command("usage") {
                    if (message.chat.type == "private") {
                        handleUpdate(Dispatchers.Default, update to bot, getUsage)
                    }
                }
                command("list_users") {
                    if (message.chat.type == "private") {
                        handleUpdate(Dispatchers.Default, update to bot, listUsers)
                    }
                }
                for (entry in Model.entries) {
                    callbackQuery(
                        callbackData = "MODEL_${entry.ordinal}"
                    ) {
                        val id = callbackQuery.from.id
                        val user = chatHistoryAdapter.getUser(id)
                        if (user == null) {
                            bot.sendMessageWithRetry(id, "Contact Yanis")
                            return@callbackQuery
                        }
                        val model = Model.entries[callbackQuery.data.substring("MODEL_".length).toInt()]!!
                        chatHistoryAdapter.addUser(user.copy(model = model))

                        bot.sendMessageWithRetry(
                            user.userId,
                            "New model:\n${model.id}: ${model.description}"
                        )
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

        val commands = getCommands(UserPermission.DEFAULT)
        println("setMyCommands result: ${bot.setMyCommands(commands)}")
//        botContext.launch(Dispatchers.IO) { inlineResponder(bot) }
        bot.startPolling()
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
            if (user.permission < UserPermission.PREMIUM) return@withContext
            text?.let {
                val args = it.split(" ")

                if (args.size - 1 < 2) {
                    bot.sendMessageWithRetry(
                        user.userId,
                        "Not all arguments passed"
                    )
                    return@withContext
                }
                val id = try {
                    args[1].toLong()
                } catch (e: Throwable) {
                    bot.sendMessageWithRetry(
                        user.userId,
                        "Bad argument: id == ${args[1]}"
                    )
                    return@withContext
                }
                val idStr = id.toString()
                val idx = it.indexOf(idStr)
                bot.sendMessageWithRetry(id, it.substring(idx + idStr.length))
            }
        }
    }

    private suspend fun requestChatPrediction(
        bot: Bot,
        messages: List<MessageData>,
        user: User,
        short: Boolean = false
    ): Pair<Usage?, String?> {
        val toSendMessages = messages.filter {
            val isPremium = user.permission >= UserPermission.PREMIUM
            val hasResource = it.resource.isNotEmpty()
            val imgModel = user.model.imgModel

            hasResource && isPremium && imgModel || !hasResource
        }.map { it.toChatMessage(bot, user) }.takeLast(5).toMutableList()
        toSendMessages.add(0, assistantInitialization)

        val completionRequest = ChatCompletionRequest(
            model = ModelId(user.model.id),
            messages = toSendMessages,
            temperature = 0.7
        )

        return try {
            val completion = openAI.chatCompletion(completionRequest)
            val content = completion.choices.firstNotNullOf { it.message }.content
            println("generated response: ${content?.substring(0, 10)}...")
            completion.usage to content
        } catch (e: Throwable) {
            println("failed with response")
            null to null
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
            if (user.permission < UserPermission.PREMIUM) return@withContext
            val models = Model.entries.map {
                "${it.id}: ${it.description}"
            }.joinToString("\n\n")
            bot.sendMessageWithRetry(user.userId, models)
        }
    }

    private val help: SuspendBotAction = { bot: Bot,
                                           from: TgUser,
                                           user: User,
                                           text: String?,
                                           reply: Message?,
                                           photo: String?,
                                           sticker: String?
        ->
        withContext(Dispatchers.Default) {
            bot.sendMessageWithRetry(
                user.userId,
                getCommands(user.permission).map {
                    "/${it.command} - ${it.description}"
                }.joinToString("\n")
            )
        }
    }
    private val getUsage: SuspendBotAction = { bot: Bot,
                                               from: TgUser,
                                               user: User,
                                               text: String?,
                                               reply: Message?,
                                               photo: String?,
                                               sticker: String?
        ->
        withContext(Dispatchers.Default) {
            bot.sendMessageWithRetry(
                user.userId,
                "Overall usage: ${df.format(chatHistoryAdapter.getUsage(user.userId))}$"
            )
        }
    }

    private fun getTextForModel(model: Model): String {
        return model.id + if (model.imgModel) " with imgs " else " "
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
            if (user.permission < UserPermission.PREMIUM) return@withContext
            val content = text!!.substring("/change_model".length).trim()
            val ids = Model.entries.map { it.id }
            if (content.isEmpty() || content !in ids) {
                val gpt_3_5 = InlineKeyboardButton.CallbackData(
                    text = "${getTextForModel(Model.GPT_3_5_TURBO)}price 1x",
                    callbackData = "MODEL_${Model.GPT_3_5_TURBO.ordinal}"
                )
                val gpt_4o = InlineKeyboardButton.CallbackData(
                    text = "${getTextForModel(Model.GPT_4O)}price 10x",
                    callbackData = "MODEL_${Model.GPT_4O.ordinal}"
                )
                val gpt_4 = InlineKeyboardButton.CallbackData(
                    text = "${getTextForModel(Model.GPT_4)}price 60x",
                    callbackData = "MODEL_${Model.GPT_4.ordinal}"
                )
                val gpt_4_turbo =
                    InlineKeyboardButton.CallbackData(
                        text = "${getTextForModel(Model.GPT_4_TURBO)}price 20x",
                        callbackData = "MODEL_${Model.GPT_4_TURBO.ordinal}"
                    )
                val markup = InlineKeyboardMarkup.create(
                    listOf(
                        listOf(gpt_3_5, gpt_4o),
                        listOf(gpt_4, gpt_4_turbo)
                    )
                )

                bot.sendMessage(
                    ChatId.fromId(user.userId),
                    text = "Select model (prefer 3.5 or 4o)",
                    replyMarkup = markup
                )

                return@withContext
            }
            val model = Model.from(content)!!
            chatHistoryAdapter.addUser(user.copy(model = model))
            bot.sendMessageWithRetry(user.userId, "New model set:\n ${model.id}: ${model.description}")
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
                    bot,
                    listOf(
                        MessageData(
                            from.id,
                            "Hello, who are you?",
                            "",
                            Role.USER,
                            0f
                        )
                    ),
                    user
                )

                chatHistoryAdapter.addMessage(
                    MessageData(user.userId, helloResponse.second ?: "", "", Role.from(ChatRole.Assistant), 0f)
                )
                bot.sendMessageWithRetry(
                    user.userId,
                    "${helloResponse.second} \nrunning on: ${user.model.id}"
                )
            }
        }

    private val addUser: SuspendBotAction = { bot: Bot,
                                              from: TgUser,
                                              user: User,
                                              text: String?,
                                              reply: Message?,
                                              photo: String?,
                                              sticker: String?
        ->
        withContext(Dispatchers.Default) {
            if (user.permission < UserPermission.ADMIN) return@withContext

            text?.let {
                val args = it.split(" ")

                if (args.size - 1 < 2) {
                    bot.sendMessageWithRetry(
                        user.userId,
                        "Not all arguments passed"
                    )
                    return@withContext
                }
                val id = try {
                    args[1].toLong()
                } catch (e: Throwable) {
                    bot.sendMessageWithRetry(
                        user.userId,
                        "Bad argument: id == ${args[1]}"
                    )
                    return@withContext
                }

                val permissionOrdinal = try {
                    args[2].toInt()
                } catch (e: Throwable) {
                    bot.sendMessageWithRetry(
                        user.userId,
                        "Bad argument: id == ${args[2]}"
                    )
                    return@withContext
                }
                val permission = UserPermission.from(permissionOrdinal)
                chatHistoryAdapter.addUser(User(id, "", permission, Model.GPT_3_5_TURBO))
                bot.sendMessageWithRetry(
                    user.userId,
                    "User added: $id $permission"
                )
            }
        }
    }

    private fun formatToJson(id: Long, name: String, usage: Float, model: String, permission: String): String {
        return """
        "$id": {
            "name": "$name",
            "permission": "$permission"
            "usage": "${"%.3f".format(usage)}$",
            "model": "$model"
        }
    """.trimIndent()
    }

    private val listUsers: SuspendBotAction = { bot: Bot,
                                                from: TgUser,
                                                user: User,
                                                text: String?,
                                                reply: Message?,
                                                photo: String?,
                                                sticker: String?
        ->
        withContext(Dispatchers.Default) {
            if (user.permission < UserPermission.ADMIN) return@withContext
            val users = chatHistoryAdapter.listUsers()
                .map {
                    formatToJson(
                        it.user.userId,
                        it.user.name,
                        it.usage,
                        it.user.model.name,
                        it.user.permission.name
                    )
                }
                .joinToString("\n")
            bot.sendMessageWithRetry(
                user.userId,
                "Users:\n$users"
            )
        }
    }
    private val deleteUser: SuspendBotAction = { bot: Bot,
                                                 from: TgUser,
                                                 user: User,
                                                 text: String?,
                                                 reply: Message?,
                                                 photo: String?,
                                                 sticker: String?
        ->
        withContext(Dispatchers.Default) {
            if (user.permission < UserPermission.ADMIN) return@withContext
            text?.let {
                val args = it.split(" ")
                if (args.size - 1 < 1) {
                    bot.sendMessageWithRetry(
                        user.userId,
                        "Not all arguments passed"
                    )
                    return@withContext
                }
                val id = try {
                    args[1].toLong()
                } catch (e: Throwable) {
                    bot.sendMessageWithRetry(
                        user.userId,
                        "Bad argument: id == ${args[1]}"
                    )
                    return@withContext
                }

                val deletingUser = chatHistoryAdapter.getUser(id)
                deletingUser?.let {
                    val success = chatHistoryAdapter.deleteUser(deletingUser.userId)
                    if (success) {
                        bot.sendMessageWithRetry(
                            user.userId,
                            "User deleted: ${deletingUser.name} ${deletingUser.userId} ${deletingUser.permission}"
                        )
                    } else {
                        bot.sendMessageWithRetry(
                            user.userId,
                            "Could not delete user"
                        )
                    }

                } ?: bot.sendMessageWithRetry(
                    user.userId,
                    "User not found"
                )

            }
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
                    val currentMessage = MessageData(user.userId, it, "", Role.USER, 0f)
                    chatHistoryAdapter.addMessage(currentMessage)
                }

                photo?.let {
                    val msg = MessageData(user.userId, "", it, Role.USER, 0f)
                    chatHistoryAdapter.addMessage(msg)
                }

                sticker?.let {
                    val config = MessageData(
                        user.userId,
                        "refer to next image as reaction, don't try to react to it, imagine that user reacted with that picture to previous message",
                        "",
                        Role.SYSTEM,
                        0f
                    )
                    chatHistoryAdapter.addMessage(config)
                    val msg = MessageData(user.userId, "", it, Role.USER, 0f)
                    chatHistoryAdapter.addMessage(msg)
                }

                println(
                    """${user.name}\n
                       |$userMessage
                    """.trimMargin()
                )

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

//                    + "\n" + userMessage
                if (text == null && (photo != null || sticker != null) && !user.model.imgModel) {


                    bot.sendMessageWithRetry(
                        user.userId,
                        "System: Images not supported in this model"
                    )

                    return@withContext
                }
                val history = chatHistoryAdapter.getMessages(user.userId)
                val answer = requestChatPrediction(bot, history, user)
                val usage =
                    answer.first.let { evaluatePrice(it?.promptTokens ?: 0, it?.completionTokens ?: 0, user.model) }
                val messageData = answer.second?.let { content ->
                    MessageData(user.userId, content, "", Role.ASSISTANT, usage)
                } ?: MessageData(
                    user.userId,
                    if (answer.first == null) "Server response timeout..." else "No response from server...",
                    "",
                    Role.SYSTEM,
                    0f
                )

                chatHistoryAdapter.addMessage(messageData)
                println("logging response: ${messageData.message.substring(0, 10)}...")

                bot.logInTg("${user.name} ${from.username?.let { " username=$it" } ?: ""} model=${user.model} usage=${
                    df.format(chatHistoryAdapter.getUsage(user.userId))
                }$ model=${user.model} id=${user.userId}L ")
                println("sending response: ${messageData.message}...")
                bot.sendMessageWithRetry(user.userId, messageData.message)
            }
        }
}
typealias SuspendBotAction = suspend (Bot, TgUser, User, String?, Message?, String?, String?) -> Unit