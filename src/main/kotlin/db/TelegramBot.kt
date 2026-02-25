package org.example.db

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException


@Component
class TelegramBot(
    private val userRepository: UserRepository,
    @Value("\${bot.token}") private val botToken: String,
    @Value("\${bot.username}") private val botName: String
) : TelegramLongPollingBot(botToken) {

    override fun getBotUsername(): String = botName

    override fun onUpdateReceived(update: Update) {
        if (!update.hasMessage() || !update.message.hasText()) return

        val messageText = update.message.text
        val chatId = update.message.chatId
        val user = userRepository.findById(chatId).orElseGet {
            User(id = chatId, userName = update.message.from.userName)
        }

        // --- ОБРАБОТКА ГЛОБАЛЬНЫХ КОМАНД МЕНЮ ---
        when (messageText) {
            "/start", "🔄 Регистрация заново" -> {
                user.state = "START"
                userRepository.save(user)
                startRegistration(chatId, user)
                return
            }
            "/menu" -> {
                user.state = "MAIN_MENU"
                userRepository.save(user)
                sendMainMenu(chatId, "Выберите раздел:")
                return
            }
            "👤 Мой профиль", "⚙️ Настройки", "✈️ Мои планы" -> {
                sendMsg(chatId, "Этот раздел находится в разработке 🛠")
                return
            }
        }

        // --- ОБРАБОТКА ШАГОВ РЕГИСТРАЦИИ (FSM) ---
        when (user.state) {
            "START" -> {
                sendMsg(chatId, "Привет! Давай создадим анкету. Как тебя зовут?")
                user.state = "WAITING_FOR_NAME"
            }
            "WAITING_FOR_NAME" -> {
                user.name = messageText
                sendMsg(chatId, "Сколько тебе лет?")
                user.state = "WAITING_FOR_AGE"
            }
            "WAITING_FOR_AGE" -> {
                val age = messageText.toIntOrNull()
                if (age in 18..110) {
                    user.age = age
                    sendGenderKeyboard(chatId, "Выбери пол:")
                    user.state = "WAITING_FOR_GENDER"
                } else {
                    sendMsg(chatId, "Введи число от 18 до 110.")
                }
            }
            "WAITING_FOR_GENDER" -> {
                if (messageText == "Мужской" || messageText == "Женский") {
                    user.gender = if (messageText == "Мужской") "MALE" else "FEMALE"
                    user.state = "REGISTERED"
                    sendMainMenu(chatId, "Регистрация завершена! Теперь вы можете пользоваться поиском.")
                } else {
                    sendMsg(chatId, "Используйте кнопки.")
                }
            }
        }
        userRepository.save(user)
    }

    // Вспомогательная функция для отправки текста
    private fun sendMsg(chatId: Long, text: String, replyMarkup: ReplyKeyboard? = null) {
        val msg = SendMessage(chatId.toString(), text)
        if (replyMarkup != null) msg.replyMarkup = replyMarkup
        execute(msg)
    }

    // Клавиатура выбора пола
    private fun sendGenderKeyboard(chatId: Long, text: String) {
        val msg = SendMessage(chatId.toString(), text)
        val keyboard = ReplyKeyboardMarkup().apply {
            keyboard = listOf(KeyboardRow().apply { add("Мужской"); add("Женский") })
            resizeKeyboard = true
            oneTimeKeyboard = true
        }
        msg.replyMarkup = keyboard
        execute(msg)
    }

    // Главное меню (постоянное)
    private fun sendMainMenu(chatId: Long, text: String) {
        val msg = SendMessage(chatId.toString(), text)
        val keyboardMarkup = ReplyKeyboardMarkup().apply {
            keyboard = listOf(
                KeyboardRow().apply { add("✈️ Мои планы"); add("👤 Мой профиль") },
                KeyboardRow().apply { add("⚙️ Настройки"); add("🔄 Регистрация заново") }
            )
            resizeKeyboard = true
        }
        msg.replyMarkup = keyboardMarkup
        execute(msg)
    }

    // Вспомогательный метод для старта регистрации
    private fun startRegistration(chatId: Long, user: User) {
        // Очищаем старые данные, если нужно
        user.name = null
        user.age = null
        user.state = "WAITING_FOR_NAME"
        userRepository.save(user)

        // Отправляем сообщение с удалением старой клавиатуры (чтобы не мешала вводу имени)
        val msg = SendMessage(chatId.toString(), "Как тебя зовут?")
        msg.replyMarkup = ReplyKeyboardRemove(true)
        execute(msg)
    }

    @PostConstruct // Выполнится автоматически при запуске приложения
    fun setBotCommands() {
        val listOfCommands = listOf(
            BotCommand("/start", "Повторная регистрация (сброс)"),
            BotCommand("/menu", "Вызвать главное меню")
        )

        try {
            execute(SetMyCommands(listOfCommands, BotCommandScopeDefault(), null))
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
}
