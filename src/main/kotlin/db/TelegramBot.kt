package org.example.db

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow


@Suppress("UNREACHABLE_CODE")
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
        val telegramUser = update.message.from

        // Загружаем пользователя из базы или создаем "заготовку"
        val user = userRepository.findById(chatId).orElseGet {
            User(id = chatId, userName = telegramUser.userName)
        }

        // Если пользователь пишет /start, сбрасываем состояние
        if (messageText == "/start") {
            user.state = "START"
        }

        when (user.state) {
            "START" -> {
                sendMsg(chatId, "Привет! Давай создадим твою анкету. Как тебя зовут?")
                user.state = "WAITING_FOR_NAME"
            }

            "WAITING_FOR_NAME" -> {
                user.name = messageText
                sendMsg(chatId, "Приятно познакомиться, ${user.name}! Сколько тебе лет?")
                user.state = "WAITING_FOR_AGE"
            }

            "WAITING_FOR_AGE" -> {
                val age = messageText.toIntOrNull()
                if (age != null && age in 18..110) {
                    user.age = age
                    sendGenderKeyboard(chatId, "Выбери свой пол:")
                    user.state = "WAITING_FOR_GENDER"
                } else {
                    sendMsg(chatId, "Пожалуйста, введи число от 18 до 110.")
                }
            }

            "WAITING_FOR_GENDER" -> {
                when (messageText) {
                    "Мужской" -> user.gender = "MALE"
                    "Женский" -> user.gender = "FEMALE"
                    else -> {
                        sendMsg(chatId, "Нажми на одну из кнопок на клавиатуре.")
                        return
                    }
                }
                user.state = "REGISTERED"
                // Убираем клавиатуру обычным сообщением
                val replyMarkup = ReplyKeyboardRemove(true)
                sendMsg(chatId, "Ура! Регистрация завершена. Теперь я буду искать тебе попутчиков.", replyMarkup)
            }

            "REGISTERED" -> {
                sendMsg(chatId, "Твоя анкета уже заполнена! Скоро я пришлю новые совпадения.")
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
}