package org.example.bot

import org.example.entity.city.CityRepository
import org.example.entity.country.CountryRepository
import org.example.entity.trip.TripRepository
import org.example.entity.user.User
import org.example.entity.user.UserRepository
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class UpdateHandler(
    private val userRepository: UserRepository,
    private val tripRepository: TripRepository,
    private val cityRepository: CityRepository,
    private val countryRepository: CountryRepository,
    private val tripService: TripService,
    private val messageFactory: MessageFactory
) {
    fun handleUpdate(update: Update, bot: TelegramLongPollingBot) {
        val chatId = extractChatId(update) ?: return
        val user = userRepository.findById(chatId).orElseGet {
            val newUser = User(id = chatId, userName = extractUserName(update))
            userRepository.save(newUser)
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update, user, bot)
        } else if (update.hasMessage()) {
            handleMessage(update, user, bot)
        }
    }

    private fun handleMessage(update: Update, user: User, bot: TelegramLongPollingBot) {
        val message = update.message
        val messageText = message.text ?: ""

        // 1. Глобальные команды (всегда приоритетны)
        when (messageText) {
            "/start" -> {
                resetUser(user, bot)
                return
            }
            "/menu" -> {
                user.state = "MAIN_MENU"
                userRepository.save(user)
                bot.execute(messageFactory.createMainMenu(user.id, "Выберите раздел:"))
                return
            }
        }

        // 2. Обработка состояний (FSM) - ТУТ БЫЛА ПАУЗА
        processState(message, user, bot)
    }

    private fun processState(message: org.telegram.telegrambots.meta.api.objects.Message, user: User, bot: TelegramLongPollingBot) {
        val messageText = message.text ?: ""
        val chatId = user.id

        when (user.state) {
            "WAITING_FOR_NAME" -> {
                user.name = messageText
                user.state = "WAITING_FOR_AGE"
                userRepository.save(user)
                sendText(bot, chatId, "Сколько тебе лет?")
            }
            "WAITING_FOR_AGE" -> {
                val age = messageText.toIntOrNull()
                if (age in 18..110) {
                    user.age = age
                    user.state = "WAITING_FOR_GENDER"
                    userRepository.save(user)
                    bot.execute(messageFactory.createGenderKeyboard(chatId, "Выбери пол:"))
                } else {
                    sendText(bot, chatId, "Введи число от 18 до 110.")
                }
            }
            "WAITING_FOR_GENDER" -> {
                if (messageText == "Мужской" || messageText == "Женский") {
                    user.gender = if (messageText == "Мужской") "MALE" else "FEMALE"
                    user.state = "MAIN_MENU"
                    userRepository.save(user)

                    val welcomeMsg = "Твой профиль готов! 🎉\nИспользуй меню для планирования поездок."
                    bot.execute(messageFactory.createMainMenu(chatId, welcomeMsg))
                }
            }
            // Другие состояния добавим следующим шагом
        }
    }

    private fun resetUser(user: User, bot: TelegramLongPollingBot) {
        user.trips.clear()
        user.name = null
        user.state = "WAITING_FOR_NAME"
        userRepository.save(user)
        tripRepository.deleteByUserId(user.id)

        val msg = SendMessage(user.id.toString(), "Привет! Давай создадим анкету. Как тебя зовут?")
        msg.replyMarkup = org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove(true)
        bot.execute(msg)
    }

    private fun sendText(bot: TelegramLongPollingBot, chatId: Long, text: String) {
        bot.execute(SendMessage(chatId.toString(), text))
    }

    private fun extractChatId(update: Update): Long? {
        return if (update.hasCallbackQuery()) update.callbackQuery.message.chatId else update.message?.chatId
    }

    private fun extractUserName(update: Update): String? {
        return if (update.hasCallbackQuery()) update.callbackQuery.from.userName else update.message?.from?.userName
    }

    private fun handleCallback(update: Update, user: User, bot: TelegramLongPollingBot) {
        // Логику кнопок вернем в следующем сообщении, чтобы не перегружать код
    }
}