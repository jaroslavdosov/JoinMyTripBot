package org.example.bot

import org.example.entity.city.CityRepository
import org.example.entity.country.CountryRepository
import org.example.entity.trip.Trip
import org.example.entity.trip.TripRepository
import org.example.entity.user.User
import org.example.entity.user.UserRepository
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
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
            "/start" -> {
                // 1. Удаляем старого пользователя, если он есть
                userRepository.findById(update.message.from.id).ifPresent { existingUser ->
                    userRepository.delete(existingUser)
                }

                // 2. Создаем нового "чистого" пользователя
                val newUser = User(id = update.message.from.id).apply {
                    state = "WAITING_FOR_NAME" // Устанавливаем первый шаг регистрации
                    languageCode = update.message.from.languageCode ?: "ru"
                }
                userRepository.save(newUser)

                // 3. Сразу отправляем первый вопрос
                bot.execute(SendMessage(newUser.id.toString(), "Привет! Давай создадим профиль с нуля. Как тебя зовут?"))
                return
            }
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

            "🔍 Поиск попутчиков" -> {
                user.state = "SEARCH_WAITING_LOCATION"
                userRepository.save(user)
                bot.execute(SendMessage(user.id.toString(), "Куда ищем попутчиков? Напиши название города или страны:"))
                return
            }
        }

        processState(update.message, user, bot)
    }

    private fun processState(message: org.telegram.telegrambots.meta.api.objects.Message, user: User, bot: TelegramLongPollingBot) {
        val messageText = message.text ?: ""
        val chatId = user.id

        when (user.state) {
            "START_SEARCH_AGAIN" -> {
                user.state = "SEARCH_WAITING_LOCATION"
                userRepository.save(user)
                bot.execute(SendMessage(user.id.toString(), "Куда ищем попутчиков? Напиши название города или страны:"))
            }

            "SEARCH_WAITING_LOCATION" -> {
                val query = message.text
                val cities = cityRepository.searchCities(query).take(4)
                val countries = countryRepository.searchCountries(query).take(3) // Предположим, у вас есть countryRepository

                if (cities.isEmpty() && countries.isEmpty()) {
                    bot.execute(SendMessage(user.id.toString(), "🔍 Ничего не найдено. Попробуйте другое название:"))
                } else {
                    val buttons = mutableListOf<List<InlineKeyboardButton>>()

                    // Сначала выводим страны (Вся страна)
                    countries.forEach { country ->
                        val label = "🌍 ${tripService.getTranslatedName(country.translations, country.name, user.languageCode)} (Вся страна)"
                        buttons.add(listOf(InlineKeyboardButton(label).apply {
                            callbackData = "SEARCH_SELECT_COUNTRY_${country.id}"
                        }))
                    }

                    // Затем выводим города
                    cities.forEach { city ->
                        val label = "📍 ${tripService.getFormattedDestinationForSearch(city, null, user.languageCode)}"
                        buttons.add(listOf(InlineKeyboardButton(label).apply {
                            callbackData = "SEARCH_SELECT_CITY_${city.id}"
                        }))
                    }

                    bot.execute(SendMessage(user.id.toString(), "Выберите направление поиска:").apply {
                        replyMarkup = InlineKeyboardMarkup(buttons)
                    })
                }
            }

            "SEARCH_WAITING_AGE" -> {
                val text = message.text.replace(" ", "")
                val parts = text.split("-")

                try {
                    var min: Int
                    var max: Int

                    if (parts.size == 2) {
                        min = parts[0].toInt()
                        max = parts[1].toInt()
                    } else {
                        val age = text.toInt()
                        min = age - 3
                        max = age + 3
                    }

                    // ПРОВЕРКИ:
                    // 1. Меняем местами, если ввели наоборот (30-20 -> 20-30)
                    if (min > max) {
                        val temp = min
                        min = max
                        max = temp
                    }

                    // 2. Адекватные границы возраста
                    if (min < 18 || max > 100) {
                        sendText(bot, user.id, "🔞 Поиск доступен только для лиц от 18 до 100 лет. Введи корректный возраст:")
                        return
                    }

                    user.searchAgeMin = min
                    user.searchAgeMax = max
                    user.state = "SEARCH_WAITING_DATES"
                    userRepository.save(user)

                    sendText(bot, user.id, "Принято: от $min до $max лет.\nВведи даты в формате `дд.мм.гггг-дд.мм.гггг`:")

                } catch (e: Exception) {
                    sendText(bot, user.id, "⚠️ Ошибка! Введи возраст числом (например, `25`) или диапазоном (например, `20-30`):")
                }
            }

            "SEARCH_WAITING_DATES" -> {
                val dates = tripService.parseStrictDates(message.text)
                if (dates == null) {
                    sendText(bot, user.id, "⚠️ Неверный формат или даты в прошлом. Попробуй еще раз (дд.мм.гггг-дд.мм.гггг):")
                } else {
                    user.searchDateStart = dates.first
                    user.searchDateEnd = dates.second
                    user.state = "MAIN_MENU"
                    userRepository.save(user)

                    // Запускаем сам поиск!
                    executeSearch(bot, user)
                }
            }
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
            data.startsWith("SEARCH_NEXT_") -> {
                val nextIndex = data.removePrefix("SEARCH_NEXT_").toInt()
                // Вызываем поиск снова, но передаем индекс, чтобы показать следующего
                executeSearch(bot, user, pageIndex = nextIndex)
            }

            data.startsWith("SEARCH_GENDER_") -> {
                user.searchGender = data.removePrefix("SEARCH_GENDER_")
                user.state = "SEARCH_WAITING_AGE"
                userRepository.save(user)
                bot.execute(SendMessage(user.id.toString(), "Введите диапазон возраста (например, 20-30):"))
            }

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

            data.startsWith("SEARCH_SELECT_COUNTRY_") -> {
                val countryId = data.removePrefix("SEARCH_SELECT_COUNTRY_").toLong()
                user.searchCountryId = countryId
                user.searchCityId = null // Сбрасываем город, так как ищем по всей стране
                goToGenderSelection(bot, user)
            }

            data.startsWith("SEARCH_SELECT_CITY_") -> {
                val cityId = data.removePrefix("SEARCH_SELECT_CITY_").toLong()
                user.searchCityId = cityId
                user.searchCountryId = null // Сбрасываем страну
                goToGenderSelection(bot, user)
            }

            data == "MAIN_MENU" -> {
                user.state = "MAIN_MENU"
                userRepository.save(user)
                // Теперь метод существует и принимает (bot, Long, String)
                messageFactory.sendMainMenu(bot, user.id, "Вы вернулись в главное меню. Что выберем?")
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

            data == "START_SEARCH_AGAIN" -> {
                user.state = "SEARCH_WAITING_LOCATION"
                // Сбрасываем старые фильтры, чтобы начать с чистого листа
                user.searchCityId = null
                user.searchCountryId = null
                userRepository.save(user)

                bot.execute(SendMessage(chatId.toString(), "Окей, давай попробуем еще раз! 🌍\nКуда планируешь поехать? Напиши город или страну:"))

                // Важно: отвечаем на callback, чтобы у пользователя пропали "часики" на кнопке
                bot.execute(AnswerCallbackQuery().apply { callbackQueryId = update.callbackQuery.id })
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

            data.startsWith("SEARCH_SELECT_LOC_") -> {
                val locId = data.removePrefix("SEARCH_SELECT_LOC_").toLong()
                user.searchCityId = locId
                user.state = "SEARCH_WAITING_GENDER"
                userRepository.save(user)

                val buttons = listOf(
                    listOf(InlineKeyboardButton("👨 Мужской").apply { callbackData = "SEARCH_GENDER_MALE" }),
                    listOf(InlineKeyboardButton("👩 Женский").apply { callbackData = "SEARCH_GENDER_FEMALE" }),
                    listOf(InlineKeyboardButton("👫 Любой").apply { callbackData = "SEARCH_GENDER_ALL" })
                )
                bot.execute(SendMessage(user.id.toString(), "Кого ищем?").apply {
                    replyMarkup = InlineKeyboardMarkup(buttons)
                })
            }

            data.startsWith("SEARCH_GENDER_") -> {
                user.searchGender = data.removePrefix("SEARCH_GENDER_")
                user.state = "SEARCH_WAITING_AGE"
                userRepository.save(user)
                sendText(bot, user.id, "Укажи возраст попутчика (например, 20-35):")
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

    // В UpdateHandler.kt
    private fun executeSearch(bot: TelegramLongPollingBot, user: User, pageIndex: Int = 0) {
        val sStart = user.searchDateStart ?: return
        val sEnd = user.searchDateEnd ?: return

        val matches = tripRepository.findMatches(
            cityId = user.searchCityId,
            countryId = user.searchCountryId, // Передаем ID страны из юзера
            currentUserId = user.id,
            gender = user.searchGender ?: "ALL",
            minAge = user.searchAgeMin ?: 18,
            maxAge = user.searchAgeMax ?: 99,
            searchStart = sStart,
            searchEnd = sEnd
        )

        if (matches.isEmpty()) {
            val markup = InlineKeyboardMarkup(listOf(
                listOf(InlineKeyboardButton("🔍 Попробовать снова").apply { callbackData = "START_SEARCH_AGAIN" }),
                listOf(InlineKeyboardButton("🏠 В главное меню").apply { callbackData = "MAIN_MENU" })
            ))

            bot.execute(SendMessage(user.id.toString(), "😔 К сожалению, попутчиков по таким параметрам пока нет. Попробуйте изменить даты или город!").apply {
                replyMarkup = markup
            })
            return
        }

        // Проверяем, не вышли ли мы за пределы списка
        if (pageIndex < matches.size) {
            val currentMatch = matches[pageIndex]
            // Отображаем профиль. Передаем pageIndex + 1 для визуального счетчика (1/3, 2/3...)
            messageFactory.sendMatchProfile(bot, user, currentMatch, pageIndex + 1, matches.size)
        } else {
            bot.execute(SendMessage(user.id.toString(), "✅ Это были все найденные анкеты."))
        }
    }

    private fun goToGenderSelection(bot: TelegramLongPollingBot, user: User) {
        user.state = "SEARCH_WAITING_GENDER"
        userRepository.save(user)
        val buttons = listOf(
            listOf(InlineKeyboardButton("👨 Мужской").apply { callbackData = "SEARCH_GENDER_MALE" }),
            listOf(InlineKeyboardButton("👩 Женский").apply { callbackData = "SEARCH_GENDER_FEMALE" }),
            listOf(InlineKeyboardButton("👫 Любой").apply { callbackData = "SEARCH_GENDER_ALL" })
        )
        bot.execute(SendMessage(user.id.toString(), "Кого ищем?").apply {
            replyMarkup = InlineKeyboardMarkup(buttons)
        })
    }
}