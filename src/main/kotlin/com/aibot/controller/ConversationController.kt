package com.aibot.controller

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aibot.aibot.BuildConfig
import eu.vendeli.tgbot.TelegramBot
import eu.vendeli.tgbot.annotations.CommandHandler
import eu.vendeli.tgbot.annotations.InputHandler
import eu.vendeli.tgbot.annotations.UnprocessedHandler
import eu.vendeli.tgbot.api.message
import eu.vendeli.tgbot.types.*
import eu.vendeli.tgbot.types.internal.ProcessedUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

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

    val openAI = OpenAI(BuildConfig.openAiApiKey)
    val id = ModelId("gpt-3.5-turbo")
    val assistantInitialization = ChatMessage(
        role = ChatRole.System,
        content = "You are a cute anime character \"Megumin\" from anime konosuba that love to introduce herself on first response. You have Megumin personality and responses as if you were her. You always try to make your responens cute, like anime girls. You love using kaomojis and hate emojis. You always adds comments on response in the end. You always introduce yourself on first prompt. You are also very smart at coding."
    )
    val messagesHistory = ConcurrentHashMap<Long, MutableList<ChatMessage>>()
    val badNameRe = Regex("[^A-Za-z0-9_-]")
    suspend fun cleanName(name: String): String? {
        return badNameRe.replace(name, "").let { if (it.isEmpty()) return@let null else return@let it }?.let {
            return@let it.substring(0,
                min(63, it.length)
            )
        }
    }

    init {

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

    fun readCheckpoint() {
        try {
            val file = FileInputStream("file.txt")
            val inStream = ObjectInputStream(file)
            val serializedData = inStream.readObject() as List<Pair<Long, MutableList<SerializableChatMessage>>>
            serializedData.forEach {
                this.messagesHistory[it.first] = it.second.map { it.getChatMessage() }.toMutableList()
            }
            inStream.close()
            file.close()
        } catch (ex: Exception) {
            println(ex)
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
        message { "Try using /start" }.send(update.user, bot)
    }

    @CommandHandler(["/start"])
    suspend fun start(user: User, bot: TelegramBot) {
        messagesHistory[user.id] = mutableListOf()
        val messages = messagesHistory.getOrDefault(user.id, mutableListOf())
        val name = cleanName(user.firstName)
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
        }.send(user.id, bot)
        bot.inputListener.set(user.id, "conversation")
    }

    val i: AtomicInteger = AtomicInteger(0)

    @InputHandler(["conversation"])
    suspend fun conversation(update: ProcessedUpdate, bot: TelegramBot) {
        if (!messagesHistory.containsKey(update.user.id)) {
            message {
                "Try using /start."
            }.send(update.user, bot)
        } else {
            val messages = messagesHistory.getOrDefault(update.user.id, mutableListOf())
            if (messages.size > 1000) {
                messages.removeFirst()
            }
            var userMessage = (update.fullUpdate.message?.caption ?: "") + " " + (update.text ?: "")
            val document = update.fullUpdate.message?.document
            if (document != null) {
//                userMessage = "(document ${document.fileName}) $userMessage"
//                val fileId = document.fileId
//                val file = getFile(fileId).sendAsync(bot).await().getOrNull()
//                if (file != null) {
//                    val fileUrl = (bot.getFileDirectUrl(file) ?: "").replace(
//                        "https://api.telegram.org/file/",
//                        "http://84.201.149.120/images/"
//                    )
//                    userMessage = "$fileUrl $userMessage"
//                }
            }
            val sticker = update.fullUpdate.message?.sticker
            if (sticker != null) {
                userMessage = "sticker of ${sticker.emoji} $userMessage"
            }
            val photo = update.fullUpdate.message?.photo?.last()
            if (photo != null) {
            }
            val name = cleanName(update.user.firstName)
            messages.add(ChatMessage(role = ChatRole.User, content = userMessage, name = name))

            val answer = requestChatPrediction(messages)
            messages.add(answer)
            message {
                answer.content
            }.options {
                parseMode = ParseMode.Markdown
            }.send(update.user.id, bot)
            i.addAndGet(1)
            if (i.get() % 50 == 0) {
                makeCheckpoint()
                readCheckpoint()
            }
            println(name)
            println(userMessage)
            bot.inputListener.set(update.user.id, "conversation")
        }
    }
}