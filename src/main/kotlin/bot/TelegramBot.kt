package org.example.bot

import jakarta.annotation.PostConstruct
import org.example.entity.city.CityRepository
import org.example.entity.country.CountryRepository
import org.example.entity.trip.Trip
import org.example.entity.trip.TripRepository
import org.example.entity.user.User
import org.example.entity.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException


@Component
class TelegramBot(
    private val userRepository: UserRepository,
    private val tripRepository: TripRepository,
    private val cityRepository: CityRepository,
    private val countryRepository: CountryRepository,
    @Value("\${bot.token}") private val botToken: String,
    @Value("\${bot.username}") private val botName: String
) : TelegramLongPollingBot(botToken) {

    override fun getBotUsername(): String = botName

    override fun onUpdateReceived(update: Update) {
        println("Получен апдейт: ${update}")
        if (update.hasMyChatMember()) {
            val memberUpdate = update.myChatMember
            val status = memberUpdate.newChatMember.status
            val userId = memberUpdate.from.id

            if (status == "kicked") {
                // Пользователь заблокировал бота — скрываем его профиль
                userRepository.findById(userId).ifPresent { user ->
                    user.isActive = false
                    userRepository.save(user)
                    println("Пользователь $userId заблокировал бота. Профиль скрыт.")
                }
            } else if (status == "member") {
                // Пользователь разблокировал бота — можно (по желанию) вернуть активность
                // или просто оставить как есть, пока он сам не нажмет "Включить"
                println("Пользователь $userId разблокировал бота.")
            }
            return
        }

        if (update.hasCallbackQuery()) {
            val callData = update.callbackQuery.data
            val chatId = update.callbackQuery.message.chatId
            val callbackId = update.callbackQuery.id

            val user = userRepository.findById(chatId).orElseGet {
                User(id = chatId, userName = update.callbackQuery.from.userName)
            }

            when {
                // 1. Нажали на кнопку выбора типа "Город"
                callData == "TYPE_CITY" -> {
                    user.state = "WAITING_FOR_CITY_SEARCH"
                    userRepository.save(user)
                    answerCallbackQuery(callbackId)
                    sendMsg(chatId, "Введите название города (например, Москва или Paris):")
                }

                // 2. ВОТ ЭТОТ БЛОК НУЖЕН: Нажали на "Вся страна"
                callData == "TYPE_COUNTRY" -> {
                    user.state = "WAITING_FOR_COUNTRY_SEARCH" // Меняем состояние пользователя
                    userRepository.save(user)
                    answerCallbackQuery(callbackId) // Убираем часики с кнопки
                    sendMsg(chatId, "Введите название страны (например, Россия или France):")
                }

                // 3. Выбрали конкретную страну из списка результатов поиска
                callData.startsWith("SELECT_COUNTRY_") -> {
                    val countryId = callData.substringAfter("SELECT_COUNTRY_").toLong()
                    user.tempCountryId = countryId
                    user.tempCityId = null // Сбрасываем город
                    user.state = "WAITING_FOR_DATES"
                    userRepository.save(user)
                    answerCallbackQuery(callbackId)
                    sendMsg(chatId, "Страна выбрана! Теперь введите даты поездки (например, 01.05.2027 - 15.05.2027):")
                }

                // 4. Выбрали конкретный город
                callData.startsWith("SELECT_CITY_") -> {
                    val cityId = callData.substringAfter("SELECT_CITY_").toLong()
                    user.tempCityId = cityId
                    user.state = "WAITING_FOR_DATES"
                    userRepository.save(user)
                    answerCallbackQuery(callbackId)
                    sendMsg(chatId, "Город выбран! Теперь введите даты поездки (например, 01.05.2027 - 15.05.2027):")
                }
                callData.startsWith("CONFIRM_DELETE_") -> {
                    val tripId = callData.substringAfter("CONFIRM_DELETE_")
                    val confirmKeyboard = InlineKeyboardMarkup(listOf(
                        listOf(
                            InlineKeyboardButton("✅ Да, удалить").apply { callbackData = "DELETE_FINAL_$tripId" },
                            InlineKeyboardButton("🚫 Отмена").apply { callbackData = "CANCEL_DELETE_$tripId" }
                        )
                    ))
                    editMsgButtons(chatId, update.callbackQuery.message.messageId, "Вы уверены, что хотите удалить эту поездку?", confirmKeyboard)
                    answerCallbackQuery(callbackId)
                }

                callData.startsWith("DELETE_FINAL_") -> {
                    val tripId = callData.substringAfter("DELETE_FINAL_").toLong()

                    // 1. Удаляем из базы напрямую
                    tripRepository.deleteById(tripId)

                    // 2. ВАЖНО: Удаляем из коллекции в памяти текущего объекта user
                    // Это гарантирует, что при повторном открытии списка в этом же сеансе поездка исчезнет
                    user.trips.removeIf { it.id == tripId }

                    // 3. Сохраняем пользователя, чтобы синхронизировать состояние
                    userRepository.save(user)

                    // 4. Визуально обновляем сообщение
                    editMsgText(chatId, update.callbackQuery.message.messageId, "🗑 Поездка успешно удалена из ваших планов.")

                    answerCallbackQuery(callbackId)
                }

                callData.startsWith("CANCEL_DELETE_") -> {
                    val tripId = callData.substringAfter("CANCEL_DELETE_").toLong()
                    val trip = tripRepository.findById(tripId).orElse(null)

                    if (trip != null) {
                        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        val lang = user.languageCode

                        // 1. Восстанавливаем название (как в основном списке)
                        val destination = if (trip.isCountryWide) {
                            "🌍 " + getTranslatedName(trip.country?.translations, trip.country?.name ?: "", lang)
                        } else {
                            val cTitle = getTranslatedName(trip.city?.translations, trip.city?.name ?: "", lang)
                            val coTitle = getTranslatedName(trip.country?.translations, trip.country?.name ?: "", lang)
                            "🏙 $cTitle ($coTitle)"
                        }

                        val tripInfo = "📍 $destination\n📅 ${trip.travelStart?.format(dateFormatter)} - ${trip.travelEnd?.format(dateFormatter)}"

                        // 2. Возвращаем кнопку удаления
                        val originalKeyboard = InlineKeyboardMarkup(listOf(
                            listOf(InlineKeyboardButton("❌ Удалить").apply {
                                callbackData = "CONFIRM_DELETE_$tripId"
                            })
                        ))

                        // 3. Редактируем сообщение, возвращая исходный вид
                        editMsgButtons(chatId, update.callbackQuery.message.messageId, tripInfo, originalKeyboard)
                    } else {
                        // Если вдруг поездка уже исчезла из базы
                        editMsgText(chatId, update.callbackQuery.message.messageId, "⚠️ Поездка не найдена.")
                    }
                    answerCallbackQuery(callbackId)
                }

                callData.startsWith("NEXT_MATCH_") -> {
                    val nextIndex = callData.replace("NEXT_MATCH_", "").toInt()

                    // Тут есть нюанс: нам нужно снова получить список matches.
                    // Чтобы не делать тяжелый запрос в БД каждый раз, можно:
                    // А) Повторить поиск (проще всего для начала)
                    // Б) Сохранить ID найденных юзеров в кэш

                    val myTrips = user.trips
                    val matchesSet = mutableSetOf<User>()
                    myTrips.forEach { trip ->
                        val found = tripRepository.findMatches(
                            user.id!!, trip.city?.id, trip.country?.id,
                            trip.isCountryWide, trip.travelStart!!, trip.travelEnd!!
                        )
                        found.forEach { it.user?.let { it1 -> matchesSet.add(it1) } }
                    }

                    showMatchedUsers(chatId, matchesSet.toList(), nextIndex)
                    answerCallbackQuery(callbackId)
                }

                callData == "TOGGLE_ACTIVE" -> {
                    user.isActive = !user.isActive
                    userRepository.save(user)
                    answerCallbackQuery(callbackId)

                    // Редактируем старое меню, чтобы кнопки сразу поменялись
                    val newStatus = if (user.isActive) "активирован" else "скрыт"
                    editMsgText(chatId, update.callbackQuery.message.messageId, "Ваш профиль теперь $newStatus.")
                    sendProfileMenu(chatId, user) // Вызываем меню заново, чтобы обновить кнопки
                }

                callData == "VIEW_MY_PROFILE" -> {
                    val lang = user.languageCode
                    val genderIcon = if (user.gender == "MALE") "👨" else "👩"
                    val bioText = user.bio ?: "_Био не заполнено_"
                    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

                    // 1. Формируем список планов (поездок)
                    val tripsText = if (user.trips.isEmpty()) {
                        "📍 _Планы не добавлены_"
                    } else {
                        user.trips.joinToString("\n") { trip ->
                            val destination = if (trip.isCountryWide) {
                                "🌍 " + getTranslatedName(trip.country?.translations, trip.country?.name ?: "", lang)
                            } else {
                                "🏙 " + getTranslatedName(trip.city?.translations, trip.city?.name ?: "", lang)
                            }
                            "▫️ $destination (${trip.travelStart?.format(dateFormatter)} — ${trip.travelEnd?.format(dateFormatter)})"
                        }
                    }

                    // 2. Итоговый текст анкеты
                    val profileInfo = """
                        $genderIcon *${user.name}, ${user.age}*
                        
                        $bioText
                        
                        *✈️ Мои планы:*
                        $tripsText
                        
                        🔗 [Написать мне](tg://user?id=${user.id})
                    """.trimIndent()

                    // 3. Клавиатура управления (те же кнопки, что в меню профиля, для удобства)
                    val keyboard = InlineKeyboardMarkup(listOf(
                        listOf(
                            InlineKeyboardButton("✍️ Изменить био").apply { callbackData = "EDIT_BIO" },
                            InlineKeyboardButton("📸 Изменить фото").apply { callbackData = "EDIT_PHOTO" }
                        ),
                        listOf(
                            InlineKeyboardButton("🔙 Назад в меню").apply { callbackData = "GO_TO_PROFILE" } // Нужно будет добавить этот обработчик
                        )
                    ))

                    // 4. Отправка (Фото или Текст)
                    if (user.photoFileId != null) {
                        val photo = org.telegram.telegrambots.meta.api.methods.send.SendPhoto()
                        photo.chatId = chatId.toString()
                        photo.photo = org.telegram.telegrambots.meta.api.objects.InputFile(user.photoFileId)
                        photo.caption = profileInfo
                        photo.parseMode = "Markdown"
                        photo.replyMarkup = keyboard
                        execute(photo)
                    } else {
                        sendMsgWithButtons(chatId, "📸 *Фото не установлено*\n\n$profileInfo", keyboard)
                    }
                    answerCallbackQuery(callbackId)
                }

                callData == "GO_TO_PROFILE" -> {
                    sendProfileMenu(chatId, user) // Твоя функция, которую мы писали раньше
                    answerCallbackQuery(callbackId)
                }

                callData == "EDIT_BIO" -> {
                    user.state = "WAITING_FOR_BIO"
                    userRepository.save(user)
                    answerCallbackQuery(callbackId)
                    sendMsg(chatId, "Напиши пару предложений о себе (чем увлекаешься, какую компанию ищешь):")
                }
                callData == "EDIT_PHOTO" -> {
                    user.state = "WAITING_FOR_PHOTO"
                    userRepository.save(user)
                    answerCallbackQuery(callbackId)
                    sendMsg(chatId, "Отправь мне свою фотографию (лучше всего портрет):")
                }

                callData == "SEARCH_AUTO" -> {
                    val myTrips = user.trips
                    // Используем LinkedHashSet, чтобы сохранить порядок и убрать дубликаты
                    val matchedUsers = mutableSetOf<User>()

                    myTrips.forEach { myTrip ->
                        val foundTrips = tripRepository.findMatches(
                            user.id!!,
                            myTrip.city?.id,
                            myTrip.country?.id,
                            myTrip.isCountryWide,
                            myTrip.travelStart!!,
                            myTrip.travelEnd!!
                        )
                        // Добавляем именно ВЛАДЕЛЬЦА поездки в Set
                        foundTrips.forEach { it.user?.let { it1 -> matchedUsers.add(it1) } }
                    }

                    if (matchedUsers.isEmpty()) {
                        sendMsg(chatId, "Совпадений пока нет 😔")
                    } else {
                        // Теперь здесь будет реальное количество уникальных людей
                        val usersList = matchedUsers.toList()
                        sendMsg(chatId, "Найдено уникальных попутчиков: ${usersList.size}")

                        // ВАЖНО: Вызываем показ первой анкеты
                        showMatchedUsers(chatId, usersList, 0)
                    }
                    answerCallbackQuery(callbackId)
                }

            }
            return // Обязательно выходим, чтобы не обрабатывать это как текст
        }


        // --- 2. ОБРАБОТКА ТЕКСТОВЫХ СООБЩЕНИЙ ---
        if (!update.hasMessage()) return
        val message = update.message
        // Если в сообщении нет ни текста, ни фото — тогда выходим
        if (!message.hasText() && !message.hasPhoto()) return



        val chatId: Long = update.message.chatId
        val user = userRepository.findById(chatId).orElseGet {
            User(id = chatId, userName = update.message.from.userName)
        }

        if (message.hasPhoto() && user.state == "WAITING_FOR_PHOTO") {
            // Берем фото в самом лучшем качестве (последнее в списке)
            val photo = message.photo.maxByOrNull { it.fileSize ?: 0 }
            user.photoFileId = photo?.fileId
            user.state = "MAIN_MENU"
            userRepository.save(user)

            sendMsg(chatId, "✅ Фото успешно сохранено! Теперь оно будет отображаться в твоей анкете.")
            sendProfileMenu(chatId, user) // Возвращаем пользователя в меню профиля
            return // Выходим из метода
        }

      // Если фото нет, проверяем наличие текста для остальных команд
        if (!message.hasText()) return
        val messageText = message.text

        // --- ОБРАБОТКА ГЛОБАЛЬНЫХ КОМАНД МЕНЮ ---
        when (messageText) {
            "/start"-> {
                // 1. Сначала очищаем коллекцию в самом объекте, чтобы Hibernate не пытался их спасти
                user.trips.clear()

                // 2. Сбрасываем данные анкеты
                user.name = null
                user.age = null
                user.gender = "MALE"
                user.state = "WAITING_FOR_NAME"
                user.bio = null
                user.photoFileId = null
                user.isActive = true


                // 3. СНАЧАЛА сохраняем пользователя с пустым списком
                // Это разорвет связи в Hibernate
                userRepository.save(user)

                // 4. ТОЛЬКО ПОТОМ удаляем записи из таблицы поездок
                tripRepository.deleteByUserId(chatId)

                // 5. Отправляем сообщение
                val msg = SendMessage(chatId.toString(), "Привет! Давай создадим анкету. Как тебя зовут?")
                msg.replyMarkup = ReplyKeyboardRemove(true)
                execute(msg)

                return
            }
            "/menu" -> {
                user.state = "MAIN_MENU"
                userRepository.save(user)
                sendMainMenu(chatId, "Выберите раздел:")
                return
            }
            "👤 Мой профиль" -> {
                sendProfileMenu(chatId, user)
                return
            }
            "✈️ Мои планы" -> {
                val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

                if (user.trips.isNotEmpty()) {
                    sendMsg(chatId, "Ваши текущие планы:")

                    // Проходимся по каждой поездке и отправляем отдельное сообщение с кнопкой удаления
                    user.trips.forEach { trip ->
                        val destination = if (trip.isCountryWide) {
                            "🌍 " + getTranslatedName(trip.country?.translations, trip.country?.name ?: "", user.languageCode)
                        } else {
                            val cityTitle = getTranslatedName(trip.city?.translations, trip.city?.name ?: "", user.languageCode)
                            val countryTitle = getTranslatedName(trip.country?.translations, trip.country?.name ?: "", user.languageCode)
                            "🏙 $cityTitle ($countryTitle)"
                        }

                        val tripInfo = "📍 $destination\n📅 ${trip.travelStart?.format(dateFormatter)} — ${trip.travelEnd?.format(dateFormatter)}"

                        // Кнопка удаления именно для этой поездки
                        val deleteBtn = InlineKeyboardMarkup(listOf(
                            listOf(InlineKeyboardButton("❌ Удалить").apply {
                                callbackData = "CONFIRM_DELETE_${trip.id}"
                            })
                        ))

                        sendMsgWithButtons(chatId, tripInfo, deleteBtn)
                    }
                } else {
                    sendMsg(chatId, "У вас пока нет добавленных поездок.")
                }

                // 2. Предлагаем добавить новую поездку (это сообщение идет в самом конце)
                val addKeyboard = InlineKeyboardMarkup(listOf(
                    listOf(
                        InlineKeyboardButton("🏙 Город").apply { callbackData = "TYPE_CITY" },
                        InlineKeyboardButton("🌍 Вся страна").apply { callbackData = "TYPE_COUNTRY" }
                    )
                ))

                sendMsgWithButtons(chatId, "Хочешь добавить новый план? Выбери формат поиска:", addKeyboard)

                user.state = "WAITING_FOR_TRIP_TYPE"
                userRepository.save(user)
                return
            }

            "⚙\uFE0F Поиск попутчиков" -> {
                val text = """
                    *Выберите режим поиска:*
                    
                    🤖 *Автоподбор* — покажу всех, чьи планы пересекаются с твоими.
                    🔎 *Ручной поиск* — найди попутчиков в конкретное место.
                """.trimIndent()

                val keyboard = InlineKeyboardMarkup(listOf(
                    listOf(
                        InlineKeyboardButton("🤖 Автоподбор").apply { callbackData = "SEARCH_AUTO" },
                        InlineKeyboardButton("🔎 Ручной поиск").apply { callbackData = "SEARCH_MANUAL" }
                    )
                ))

                val msg = SendMessage(chatId.toString(), text)
                msg.replyMarkup = keyboard
                msg.parseMode = "Markdown"
                execute(msg)
            }

        }

        // --- ОБРАБОТКА ШАГОВ РЕГИСТРАЦИИ (FSM) ---
        when (user.state) {
            "WAITING_FOR_BIO" -> {
                user.bio = messageText
                user.state = "MAIN_MENU"
                userRepository.save(user)
                sendMsg(chatId, "✅ Твое описание обновлено!")
                sendProfileMenu(chatId, user)
            }

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
                    user.state = "WAITING_FOR_TRIP_TYPE" // Устанавливаем правильное состояние
                    userRepository.save(user)

                    // Создаем Inline-кнопки
                    val keyboard = InlineKeyboardMarkup(listOf(
                        listOf(
                            InlineKeyboardButton("🏙 Город").apply { callbackData = "TYPE_CITY" },
                            InlineKeyboardButton("🌍 Вся страна").apply { callbackData = "TYPE_COUNTRY" }
                        )
                    ))

                    // Отправляем сообщение С КНОПКАМИ
                    sendMsgWithButtons(chatId, "Твой профиль готов! 🎉\n\nТеперь давайте запланируем твою первую поездку. Выбери формат поиска:'", keyboard)
                } else {
                    sendMsg(chatId, "Пожалуйста, используйте кнопки для выбора пола.")
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

                    sendMsg(chatId, "Записал: **$destination**! 📍\nТеперь напиши даты поездки в формате:\n`дд.мм.гггг-дд.мм.гггг` (например, 01.05.2026-15.05.2026)")
                }
            }

            // ... (предыдущие состояния)

            "WAITING_FOR_CITY_SEARCH" -> {
                val query = messageText.trim()
                val lang = detectLanguage(query)
                user.languageCode = lang // Сохраняем, чтобы потом вывести подтверждение на том же языке
                val cities = cityRepository.searchCities(query)

                if (cities.isEmpty()) {
                    sendMsg(chatId, "Город '$query' не найден. Попробуй еще раз:")
                } else {
                    val buttons = cities.take(12).map { city ->
                        val cityDisplayName = getTranslatedName(city.translations, city.name, lang)
                        val countryDisplayName = city.country?.let {
                            getTranslatedName(it.translations, it.name ?: "", lang)
                        } ?: ""

                        val buttonText = if (countryDisplayName.isNotEmpty()) "$cityDisplayName ($countryDisplayName)" else cityDisplayName

                        listOf(InlineKeyboardButton(buttonText).apply {
                            callbackData = "SELECT_CITY_${city.id}"
                        })
                    }
                    sendMsgWithButtons(chatId, "Выберите город:", InlineKeyboardMarkup(buttons))
                }
            }

            "WAITING_FOR_COUNTRY_SEARCH" -> {
                val query = messageText.trim()
                val lang = detectLanguage(query)
                user.languageCode = lang // ЗАПОМИНАЕМ ЯЗЫК
                val countries = countryRepository.searchCountries(query)

                if (countries.isEmpty()) {
                    sendMsg(chatId, "Страна '$query' не найдена.")
                } else {
                    val buttons = countries.take(12).map { country ->
                        val displayName = getTranslatedName(country.translations, country.name ?: "", lang)
                        listOf(InlineKeyboardButton(displayName).apply {
                            callbackData = "SELECT_COUNTRY_${country.id}"
                        })
                    }
                    sendMsgWithButtons(chatId,  "Выберите страну:", InlineKeyboardMarkup(buttons))
                }
            }

            // ... (состояние WAITING_FOR_DATES и остальные)

            "WAITING_FOR_DATES" -> {
                    try {
                        val dates = messageText.split("-").map { it.trim() }
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        val start = java.time.LocalDate.parse(dates[0], formatter)
                        val end = java.time.LocalDate.parse(dates[1], formatter)

                        // Используем сохраненный язык пользователя
                        val lang = user.languageCode

                        when {
                            start.isBefore(java.time.LocalDate.now()) -> {
                                sendMsg(chatId, "Дата начала не может быть в прошлом. Попробуйте ещё раз.")
                            }
                            else -> {
                                val city = user.tempCityId?.let { cityRepository.findById(it).orElse(null) }
                                val country = user.tempCountryId?.let { countryRepository.findById(it).orElse(null) }

                                val newTrip = Trip(
                                    user = user,
                                    city = city,
                                    country = country ?: city?.country,
                                    isCountryWide = (user.tempCityId == null && user.tempCountryId != null),
                                    travelStart = start,
                                    travelEnd = end
                                )
                                tripRepository.save(newTrip)

                                // ВЫВОД НАЗВАНИЯ НА НУЖНОМ ЯЗЫКЕ
                                val finalDestName = if (newTrip.isCountryWide) {
                                    getTranslatedName(newTrip.country?.translations, newTrip.country?.name ?: "", lang)
                                } else {
                                    val cName = getTranslatedName(newTrip.city?.translations, newTrip.city?.name ?: "", lang)
                                    val coName = getTranslatedName(newTrip.country?.translations, newTrip.country?.name ?: "", lang)
                                    "$cName ($coName)"
                                }

                                val successMsg = "✅ Поездка в $finalDestName добавлена!"

                                user.apply {
                                    state = "MAIN_MENU"
                                    tempCityId = null
                                    tempCountryId = null
                                }
                                userRepository.save(user)
                                sendMainMenu(chatId, successMsg)
                            }
                        }
                    } catch (e: Exception) {
                        sendMsg(chatId, "❌ Неверный формат дат. Попробуйте ещё раз.")
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
                KeyboardRow().apply { add("⚙️ Поиск попутчиков") }
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
        val msg = SendMessage(chatId.toString(), "Как вас зовут?")
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

    private fun sendMsgWithButtons(chatId: Long, text: String, keyboard: InlineKeyboardMarkup) {
        val message = SendMessage()
        message.chatId = chatId.toString()
        message.text = text
        message.replyMarkup = keyboard
        try {
            execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    private fun answerCallbackQuery(callbackId: String) {
        try {
            val answer = org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery()
            answer.callbackQueryId = callbackId
            execute(answer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getTranslatedName(translations: String?, defaultName: String, langCode: String): String {
        if (translations == null) return defaultName

        // Ищем в JSON ключ нужного языка, например "ru":"..." или "en":"..."
        val regex = "\"$langCode\":\\s*\"([^\"]+)\"".toRegex()
        val match = regex.find(translations)

        // Если нашли перевод на нужный язык — берем его, иначе — стандартное имя
        return match?.groups?.get(1)?.value ?: defaultName
    }

    private fun detectLanguage(text: String): String {
        val russianRange = Regex("[а-яА-ЯёЁ]")
        return if (russianRange.containsMatchIn(text)) "ru" else "en"
    }

    private fun editMsgButtons(chatId: Long, messageId: Int, text: String, keyboard: InlineKeyboardMarkup) {
        val edit = org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText()
        edit.chatId = chatId.toString()
        edit.messageId = messageId
        edit.text = text
        edit.replyMarkup = keyboard
        try { execute(edit) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun editMsgText(chatId: Long, messageId: Int, text: String) {
        val edit = org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText()
        edit.chatId = chatId.toString()
        edit.messageId = messageId
        edit.text = text
        try { execute(edit) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun sendProfileMenu(chatId: Long, user: User) {
        val statusEmoji = if (user.isActive) "✅ Виден в поиске" else "💤 Скрыт из поиска"
        val text = "👤 *Твой профиль*\n\nСтатус: $statusEmoji\n\nЗдесь ты можешь настроить свою анкету, которую будут видеть другие путешественники."

        val buttons = mutableListOf<List<InlineKeyboardButton>>()

        if (user.isActive) {
            // Если профиль активен — показываем все настройки
            buttons.add(listOf(
                InlineKeyboardButton("✍️ О себе").apply { callbackData = "EDIT_BIO" },
                InlineKeyboardButton("📸 Фото").apply { callbackData = "EDIT_PHOTO" }
            ))
            buttons.add(listOf(
                InlineKeyboardButton("👁 Предпросмотр").apply { callbackData = "VIEW_MY_PROFILE" }
            ))
            buttons.add(listOf(
                InlineKeyboardButton("🚫 Скрыть профиль").apply { callbackData = "TOGGLE_ACTIVE" }
            ))
        } else {
            // Если скрыт — только кнопка включения
            buttons.add(listOf(
                InlineKeyboardButton("✅ Включить профиль").apply { callbackData = "TOGGLE_ACTIVE" }
            ))
        }

        sendMsgWithButtons(chatId, text, InlineKeyboardMarkup(buttons))
    }

    fun showMatchedUsers(chatId: Long, matches: List<User>, index: Int) {
        try {
            if (matches.isEmpty() || index >= matches.size) {
                sendMsg(chatId, "Больше анкет не найдено. 😊")
                return
            }

            val match = matches[index]
            val genderIcon = if (match.gender == "MALE") "👨" else "👩"

            // 1. Очищаем текст от символов, которые ломают Markdown
            val safeName = match.name?.replace("_", " ")?.replace("*", "") ?: "Без имени"
            val safeBio = match.bio?.replace("_", " ")?.replace("*", "") ?: "Описание отсутствует"

            // 2. Формируем текст с Markdown-ссылкой на профиль по ID
            // Синтаксис: [Текст](tg://user?id=12345)

            val contactLink = if (!match.userName.isNullOrBlank()) {
                // Ссылка на Username ВСЕГДА активна
                "https://t.me/${match.userName!!.replace("@", "")}"
            } else {
                // Если ника нет, оставляем ссылку по ID
                "tg://user?id=${match.id}"
            }

            val info = """
            $genderIcon *${safeName}, ${match.age ?: "?"}*
            
            ${safeBio}
            
           💬 [Нажать, чтобы написать попутчику]($contactLink)
                """.trimIndent()

            // 3. Создаем клавиатуру (без кнопки "Написать", так как ссылка уже в тексте)
            val buttons = mutableListOf<List<InlineKeyboardButton>>()

            // Кнопка "Дальше", если есть еще анкеты
            if (index < matches.size - 1) {
                buttons.add(listOf(
                    InlineKeyboardButton("➡️ Следующая анкетка").apply {
                        callbackData = "NEXT_MATCH_${index + 1}"
                    }
                ))
            }

            // Кнопка возврата в меню
            buttons.add(listOf(
                InlineKeyboardButton("🔙 В главное меню").apply {
                    callbackData = "GO_TO_PROFILE"
                }
            ))

            val inlineKeyboard = InlineKeyboardMarkup().apply {
                keyboard = buttons
            }

            // 4. Отправка (сначала пробуем фото, если нет — текст)
            if (!match.photoFileId.isNullOrBlank()) {
                val photo = SendPhoto().apply {
                    setChatId(chatId.toString())
                    setPhoto(InputFile(match.photoFileId))
                    caption = info
                    setParseMode("Markdown")
                    replyMarkup = inlineKeyboard
                }
                execute(photo)
            } else {
                val msg = SendMessage().apply {
                    setChatId(chatId.toString())
                    text = "📸 *Без фото*\n\n$info"
                    setParseMode("Markdown")
                    replyMarkup = inlineKeyboard
                }
                execute(msg)
            }

            println("Анкетка ${match.id} (индекс $index) успешно отправлена!")

        } catch (e: Exception) {
            println("!!! ОШИБКА В showMatchedUsers: ${e.message}")
            e.printStackTrace()
            // Если Markdown всё равно ломается, отправляем чистым текстом как запасной вариант
            sendMsg(chatId, "Произошла ошибка при загрузке анкеты. Попробуй следующую.")
        }
    }
}
