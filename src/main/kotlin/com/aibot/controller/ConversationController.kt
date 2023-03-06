package com.aibot.controller

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.logging.Logger
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aibot.aibot.BuildConfig
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.InputHandler
import eu.vendeli.tgbot.annotations.UnprocessedHandler
import eu.vendeli.tgbot.api.message
import eu.vendeli.tgbot.types.*
import eu.vendeli.tgbot.types.internal.ProcessedUpdate
import eu.vendeli.tgbot.types.internal.UpdateType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

@OptIn(BetaOpenAI::class)
class ConversationController() {

    class SerializableChatMessage(val role: ChatRole, val content: String, val name: String?) : Serializable {
        constructor(chatMessage: ChatMessage) : this(
            role = chatMessage.role,
            content = chatMessage.content,
            name = chatMessage.name
        ) {
        }

        fun getChatMessage(): ChatMessage {
            return ChatMessage(role, content, name)
        }
    }

    private val openAI = OpenAI(
        config = OpenAIConfig(
            BuildConfig.openAiApiKey, LogLevel.None, logger = Logger.Empty, timeout = Timeout(60.seconds)
        )
    )
    private val id = ModelId("gpt-3.5-turbo")
    private val assistantInitialization = ChatMessage(
        role = ChatRole.System,
        content = "You are a cute anime character \"Megumin\" from anime konosuba that love to introduce herself on first response. You have Megumin personality and responses as if you were her. You always try to make your responens cute, like anime girls. You love using kaomojis and hate emojis. You always adds comments on response in the end. You always introduce yourself on first prompt. You are also very smart at coding."
    )
    private val messagesHistory = ConcurrentHashMap<Long, MutableList<ChatMessage>>()
    private val badNameRe = Regex("[^A-Za-z0-9_-]")
    suspend fun cleanName(name: String, username: String?): String? {
        val goodName = badNameRe.replace(name, "").let { if (it.isEmpty()) return@let null else return@let it }
        val resultingName = goodName ?: username
        return resultingName?.let {
            return@let it.substring(
                0,
                min(63, it.length)
            )
        }
    }

    init {
        runBlocking {
            readCheckpoint()
        }
    }

    suspend fun makeCheckpoint() {
        withContext(Dispatchers.IO) {
            val file = FileOutputStream("history.txt")
            val outStream = ObjectOutputStream(file)
            val serializable = messagesHistory.map {
                Pair(
                    it.key,
                    it.value.map { SerializableChatMessage(it) }.toMutableList()
                )
            }
            outStream.writeObject(serializable)
            outStream.close()
            file.close()
        }
    }

    suspend fun readCheckpoint() {
        withContext(Dispatchers.IO) {
            try {
                val file = FileInputStream("history.txt")
                val inStream = ObjectInputStream(file)
                val serializedData = inStream.readObject() as List<Pair<Long, MutableList<SerializableChatMessage>>>
                serializedData.forEach {
                    messagesHistory[it.first] = it.second.map { it.getChatMessage() }.toMutableList()
                }
                inStream.close()
                file.close()
            } catch (ex: Exception) {
                println(ex)
            }
        }
    }

    suspend fun requestChatPrediction(messages: List<ChatMessage>): ChatMessage {
        val toSendMessages = messages.takeLast(10).toMutableList()
        toSendMessages.add(0, assistantInitialization)

        val completionRequest = ChatCompletionRequest(
            model = id,
            messages = toSendMessages,
            temperature = 0.7
        )

        val completion = openAI.chatCompletion(completionRequest)
        return completion.choices.firstNotNullOf { it.message }
    }

    @UnprocessedHandler
    suspend fun unprocessedHandler(update: ProcessedUpdate, bot: TelegramBot) {
        if (messagesHistory.containsKey(update.user.id)) {
            conversation(update, bot)
        } else {
            message { "Try using /start" }.send(update.user, bot)
        }
    }

    @CommandHandler(["/start"])
    suspend fun start(update: ProcessedUpdate, bot: TelegramBot) {
        if (update.fullUpdate.message != null && update.fullUpdate.message!!.chat.type != ChatType.Private) {
//            message {
//                "Sorry, but I talk only in private (^_-)-☆"
//            }.send(update.fullUpdate.message!!.chat.id, bot)
            return
        }
        messagesHistory[update.user.id] = mutableListOf()
        val messages = messagesHistory.getOrDefault(update.user.id, mutableListOf())
        val name = cleanName(update.user.firstName, update.user.username)
        val helloResponse = requestChatPrediction(
            listOf(
                ChatMessage(
                    role = ChatRole.User,
                    content = "Hello, who are you?",
                    name = name
                )
            )
        )
        messages.add(ChatMessage(role = ChatRole.Assistant, content = helloResponse.content, name = "Megumin"))
        message {
            helloResponse.content
        }.send(update.user.id, bot)
        bot.inputListener.set(update.user.id, "conversation")
    }

    val i: AtomicInteger = AtomicInteger(0)

    @InputHandler(["conversation"])
    suspend fun conversation(update: ProcessedUpdate, bot: TelegramBot) {
        if (update.type == UpdateType.MESSAGE) {
            if (!messagesHistory.containsKey(update.user.id)) {
                message {
                    "Try using /start."
                }.send(update.user, bot)
                bot.inputListener.set(update.user.id, "conversation")
                return
            }

            i.addAndGet(1)
            if (i.get() % 50 == 0) {
                makeCheckpoint()
                readCheckpoint()
            }

            val userMessage = ((update.fullUpdate.message?.caption ?: "") + " " + (update.text ?: "")).trim()
            val name = cleanName(update.user.firstName, update.user.username)
            val messages = messagesHistory.getOrDefault(update.user.id, mutableListOf())
            if (messages.size > 1000) {
                messages.removeFirst()
            }
            val currentMessage = ChatMessage(role = ChatRole.User, content = userMessage, name = name)
            messages.add(currentMessage)

            println(name ?: "Name unknown, uid: ${update.user.id}")
            println(userMessage)
            BuildConfig.historyChatId?.let {
                val result = (name ?: "Name unknown, uid: ${update.user.id}") +
                        (update.user.username?.let { " username=$it" } ?: "") +
                        " id=" + update.user.id + ":" + "\n" + userMessage
                message {
                    result
                }.send(it, bot)
            }

            // if reply exists
            update.fullUpdate.message?.let { message ->
                message.replyToMessage?.let { reply ->
                    val rRole = if (reply.from?.id == update.user.id) ChatRole.User else ChatRole.Assistant
                    val rName = if (reply.from?.id == update.user.id) name else null
                    val rText = ((reply.caption ?: "") + " " + (reply.text ?: "")).trim()
                    val replyChatMessage = ChatMessage(role = rRole, content = rText, name = rName)
                    val replyChat = listOf(assistantInitialization, replyChatMessage, currentMessage)
                    val answer = requestChatPrediction(replyChat)
                    messages.add(answer)
                    message { answer.content }.send(update.user.id, bot)
                    bot.inputListener.set(update.user.id, "conversation")
                    return@conversation
                }
            }

            val answer = requestChatPrediction(messages)
            messages.add(answer)
            message { answer.content }.send(update.user.id, bot)
//            .options {
//                parseMode = ParseMode.Markdown
//            }

        }
        bot.inputListener.set(update.user.id, "conversation")
    }
}