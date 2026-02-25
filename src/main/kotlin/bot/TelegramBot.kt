package org.example.bot

import jakarta.annotation.PostConstruct
import org.example.entity.trip.Trip
import org.example.entity.trip.TripRepository
import org.example.entity.user.User
import org.example.entity.user.UserRepository
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
    private val tripRepository: TripRepository,
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
            "👤 Мой профиль", "⚙️ Настройки" -> {
                sendMsg(chatId, "Этот раздел находится в разработке 🛠")
                return
            }
            "✈️ Мои планы" -> {
                if (user.trips.isEmpty()) {
                    sendMsg(chatId, "У вас пока нет добавленных поездок. Добавим первую? Куда она направляется?")
                    user.state = "WAITING_FOR_DESTINATION"
                } else {
                    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                    val tripsList = user.trips.joinToString("\n") {
                        "📍 ${it.destination}: ${it.travelStart.format(dateFormatter)} — ${it.travelEnd.format(dateFormatter)}"
                    }
                    sendMsg(chatId, "Ваши планы:\n$tripsList\n\nХотите добавить новый пункт назначения? Введите пункт назначения.")
                    user.state = "WAITING_FOR_DESTINATION"
                }
                userRepository.save(user)
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
                    user.state = "WAITING_FOR_DESTINATION"
                    userRepository.save(user)
                    sendMainMenu(chatId, "Твой профиль готов! 🎉\nТеперь давай запланируем твою первую поездку. В какой город или страну ты собираешься?")
                } else {
                    sendMsg(chatId, "Используйте кнопки.")
                }
            }

            "WAITING_FOR_DESTINATION" -> {
                // Пользователь ввел название места
                val destination = messageText.trim()

                if (destination.length < 2) {
                    sendMsg(chatId, "Название слишком короткое. Напиши, куда именно ты едешь?")
                } else {
                    user.tempDestination = destination // Сохраняем город в "память" объекта
                    user.state = "WAITING_FOR_DATES"
                    userRepository.save(user)

                    sendMsg(chatId, "Записал: **$destination**! 📍\nТеперь напиши даты поездки в формате:\n`дд.мм.гггг - дд.мм.гггг` (например, 01.05.2026 - 15.05.2026)")
                }
            }
            
            "WAITING_FOR_DATES" -> {
                try {
                    val dates = messageText.split("-").map { it.trim() }
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

                    val start = java.time.LocalDate.parse(dates[0], formatter)
                    val end = java.time.LocalDate.parse(dates[1], formatter)

                    // Проверка дат
                    if (start.isBefore(java.time.LocalDate.now())) {
                        sendMsg(chatId, "Дата начала не может быть в прошлом. Попробуй еще раз:")
                    } else if(start  > end) {
                        sendMsg(chatId, "Дата начала не может быть позже даты конца. Попробуй еще раз:")
                    }
                    else {
                        // Создаем объект поездки
                        val newTrip = Trip(
                            user = user,
                            destination = user.tempDestination ?: "Неизвестно",
                            travelStart = start,
                            travelEnd = end
                        )

                        tripRepository.save(newTrip) // Сохраняем в таблицу trips

                        user.state = "MAIN_MENU"
                        user.tempDestination = null // Очищаем временное поле
                        userRepository.save(user)

                        sendMainMenu(chatId, "Поездка в ${newTrip.destination} добавлена! Теперь ты можешь найти попутчиков через меню.")
                    }
                } catch (e: Exception) {
                    sendMsg(chatId, "Неверный формат дат. Напиши еще раз, например: 10.06.2026-20.06.2026")
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
