package org.example.bot

import org.example.entity.city.CityRepository
import org.example.entity.country.CountryRepository
import org.example.entity.trip.Trip
import org.example.entity.trip.TripRepository
import org.example.entity.user.User
import org.example.entity.user.UserRepository
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import java.time.LocalDate

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
        val messageText = update.message.text ?: ""
        val chatId = user.id

        when (messageText) {
            "/start" -> { resetUser(user, bot); return }
            "/menu" -> {
                user.state = "MAIN_MENU"
                userRepository.save(user)
                bot.execute(messageFactory.createMainMenu(chatId, "Главное меню:"))
                return
            }
            // НОВАЯ ЛОГИКА ТУТ:
            "✈️ Мои планы" -> {
                bot.execute(messageFactory.createTripsList(chatId, user.trips, user.languageCode))
                return
            }

            "👤 Мой профиль" -> {
                messageFactory.sendFullProfile(bot, user)
                return
            }
        }

        processState(update.message, user, bot)
    }

    private fun processState(message: org.telegram.telegrambots.meta.api.objects.Message, user: User, bot: TelegramLongPollingBot) {
        val messageText = message.text ?: ""
        val chatId = user.id

        when (user.state) {
            "WAITING_FOR_BIO" -> {
                user.bio = message.text
                user.state = "MAIN_MENU"
                userRepository.save(user)

                // 1. Уведомляем об успехе
                bot.execute(SendMessage(user.id.toString(), "✅ Био обновлено!"))
                // 2. Показываем обновленный профиль (он подтянет и фото, и город)
                messageFactory.sendFullProfile(bot, user)
            }
            "WAITING_FOR_PHOTO" -> {
                // 1. Проверяем, есть ли в сообщении фотографии
                if (message.hasPhoto()) {
                    // Берем самое качественное фото
                    val fileId = message.photo.maxByOrNull { it.fileSize }?.fileId

                    user.photoFileId = fileId
                    user.state = "MAIN_MENU" // Только теперь меняем состояние
                    userRepository.save(user)

                    bot.execute(SendMessage(user.id.toString(), "✅ Фотография успешно сохранена!"))
                    messageFactory.sendFullProfile(bot, user)
                } else {
                    // 2. Если прислали текст, файл или стикер вместо фото
                    bot.execute(SendMessage(user.id.toString(), "⚠️ Пожалуйста, пришлите именно **фотографию** (как изображение), а не текст или документ."))
                }
            }

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

            "EDIT_HOME_CITY" -> {
                user.state = "WAITING_FOR_HOME_CITY"
                userRepository.save(user)
                sendText(bot, user.id, "Напиши название города, в котором ты живешь:")
            }

            "WAITING_FOR_HOME_CITY" -> {
                val query = message.text
                val cities = cityRepository.searchCities(query)

                if (cities.isEmpty()) {
                    bot.execute(SendMessage(user.id.toString(), "🔍 Город не найден. Попробуй ввести название иначе:"))
                } else {
                    // Создаем кнопки для выбора конкретного города из результатов поиска
                    val buttons = cities.take(8).map { city ->
                        val label = tripService.getFormattedDestinationForSearch(city, null, user.languageCode)
                        listOf(InlineKeyboardButton(label).apply { callbackData = "SET_HOME_CITY_${city.id}" })
                    }

                    bot.execute(SendMessage(user.id.toString(), "Выберите ваш город из списка:").apply {
                        replyMarkup = InlineKeyboardMarkup(buttons)
                    })
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
            "WAITING_FOR_DESTINATION" -> {
                val query = message.text
                val cities = cityRepository.searchCities(query)
                val countries = countryRepository.searchCountries(query)

                if (cities.isEmpty() && countries.isEmpty()) {
                    sendText(bot, user.id, "Ничего не нашлось. Попробуй написать название иначе.")
                } else {
                    bot.execute(messageFactory.createSelectionButtons(user.id, cities, countries, user.languageCode))
                }
            }

            "WAITING_FOR_DATE" -> {
                val dates = tripService.parseStrictDates(message.text)
                val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))

                if (dates == null) {
                    val errorMsg = """
                        ⚠️ *Ошибка валидации дат!*
                        
                        1. Формат строго: `01.01.2025-05.01.2025`
                        2. Дата начала не может быть раньше $today
                        3. Дата окончания не может быть раньше даты начала.
                    """.trimIndent()

                    bot.execute(SendMessage(user.id.toString(), errorMsg).apply { parseMode = "Markdown" })
                } else {
                    saveTripAndFinish(user, dates.first, dates.second, bot)
                }
            }
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
        val data = update.callbackQuery.data
        var chatId = user.id
        val callbackId = update.callbackQuery.id

        when {
            data.startsWith("SET_HOME_CITY_") -> {
                val cityId = data.removePrefix("SET_HOME_CITY_").toLong()
                user.homeCity = cityRepository.findById(cityId).orElse(null)
                user.state = "MAIN_MENU"
                userRepository.save(user)

                bot.execute(SendMessage(user.id.toString(), "✅ Город установлен!"))
                messageFactory.sendFullProfile(bot, user)
            }

            data.startsWith("CONFIRM_DELETE_") -> {
                val tripId = data.removePrefix("CONFIRM_DELETE_").toLong()
                bot.execute(messageFactory.createDeleteConfirmation(chatId, tripId))
            }

            data.startsWith("DELETE_FINAL_") -> {
                val tripId = data.removePrefix("DELETE_FINAL_").toLong()
                tripRepository.deleteById(tripId)
                user.trips.removeIf { it.id == tripId }
                userRepository.save(user)
                // Возвращаемся к списку
                bot.execute(messageFactory.createTripsList(chatId, user.trips, user.languageCode))
            }

            data == "GO_TO_PLANS" -> {
                bot.execute(messageFactory.createTripsList(chatId, user.trips, user.languageCode))
            }

            data == "ADD_TRIP" -> {
                user.state = "WAITING_FOR_DESTINATION"
                userRepository.save(user)
                sendText(bot, chatId, "Куда едем? (Город или страна)")
            }

            data == "EDIT_HOME_CITY" -> {
                user.state = "WAITING_FOR_HOME_CITY"
                userRepository.save(user)
                // Важно: используй bot.execute, если sendText не определен
                bot.execute(SendMessage(chatId.toString(), "🏠 Напиши название города, в котором ты живешь:"))
            }

            data == "TOGGLE_ACTIVE" -> {
                user.isActive = !user.isActive
                userRepository.save(user)

                // После переключения статуса удаляем старое сообщение и шлем обновленный профиль
                // (Так проще, чем редактировать Caption в SendPhoto через API)
                try {
                    bot.execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage(
                        chatId.toString(), update.callbackQuery.message.messageId
                    ))
                } catch (e: Exception) {}

                messageFactory.sendFullProfile(bot, user)
            }

            data.startsWith("DELETE_TRIP_") -> {
                val tripId = data.removePrefix("DELETE_TRIP_").toLong()
                tripRepository.deleteById(tripId)

                // Обновляем список пользователя в памяти и БД
                user.trips.removeIf { it.id == tripId }
                userRepository.save(user)

                // Уведомляем пользователя (всплывающее окно в Telegram)
                bot.execute(org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery(callbackId).apply {
                    text = "Поездка удалена"
                })

                // Перерисовываем список планов
                bot.execute(messageFactory.createTripsList(chatId, user.trips, user.languageCode))
            }

            data == "ADD_TRIP" -> {
                user.state = "WAITING_FOR_DESTINATION"
                userRepository.save(user)
                bot.execute(SendMessage(chatId.toString(), "Куда планируешь поехать? Напиши город или страну."))
            }

            data.startsWith("SELECT_CITY_") -> {
                val cityId = data.removePrefix("SELECT_CITY_").toLong()
                user.tempCityId = cityId
                user.state = "WAITING_FOR_DATE"
                userRepository.save(user)
                sendText(bot, user.id, "Отлично! Введи дату поездки  в формате: 01.01.2027-01.14.2027")
            }

            data.startsWith("SELECT_COUNTRY_") -> {
                val countryId = data.removePrefix("SELECT_COUNTRY_").toLong()
                user.tempCountryId = countryId
                user.state = "WAITING_FOR_DATE"
                userRepository.save(user)
                sendText(bot, user.id, "Записал страну! Введи дату поездки  в формате: 01.01.2027-01.14.2027")
            }

            data == "EDIT_BIO" -> {
                user.state = "WAITING_FOR_BIO"
                userRepository.save(user)
                sendText(bot, user.id, "Напиши немного о себе (интересы, кого ищешь):")
            }
            data == "EDIT_PHOTO" -> {
                user.state = "WAITING_FOR_PHOTO"
                userRepository.save(user)
                sendText(bot, user.id, "Пришли свою фотографию:")
            }
            data == "TOGGLE_ACTIVE" -> {
                user.isActive = !user.isActive
                userRepository.save(user)

                // Исправленный блок EditMessageText
                val editMsg = org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText().apply {
                    setChatId(user.id.toString()) // Используем сеттер для надежности
                    messageId = update.callbackQuery.message.messageId
                    val newView = messageFactory.createProfileView(user)
                    text = newView.text
                    parseMode = "Markdown"
                    replyMarkup = newView.replyMarkup as InlineKeyboardMarkup
                }
                bot.execute(editMsg)
            }
        }
    }
    private fun saveTripAndFinish(user: User, start: LocalDate, end: LocalDate, bot: TelegramLongPollingBot) {
        val trip = Trip(
            user = user,
            city = user.tempCityId?.let { cityRepository.findById(it).get() },
            country = user.tempCountryId?.let { countryRepository.findById(it).get() },
            isCountryWide = user.tempCountryId != null && user.tempCityId == null,
            travelStart = start,
            travelEnd = end
        )
        tripRepository.save(trip)
        user.trips.add(trip)
        user.state = "MAIN_MENU"
        user.tempCityId = null
        user.tempCountryId = null
        userRepository.save(user)

        bot.execute(messageFactory.createMainMenu(user.id, "✅ Поездка добавлена!"))
    }

}