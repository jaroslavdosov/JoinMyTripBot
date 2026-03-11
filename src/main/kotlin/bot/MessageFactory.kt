package org.example.bot

import org.example.entity.city.City
import org.example.entity.country.Country
import org.example.entity.trip.Trip
import org.example.entity.user.User
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import java.time.format.DateTimeFormatter

@Component
class MessageFactory(
    private val tripService: TripService // Добавь эту строку
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Внутри MessageFactory.kt

    fun createProfileView(user: User): SendMessage {
        val status = if (user.isActive) "✅ Виден в поиске" else "💤 Скрыт"
        val gender = if (user.gender == "MALE") "Мужской" else "Женский"

        val info = """
        👤 *Твой профиль*
        
        *Имя:* ${user.name ?: "Не указано"}
        *Возраст:* ${user.age ?: "Не указан"}
        *Пол:* $gender
        *О себе:* ${user.bio ?: "Пусто"}
        
        *Статус:* $status
    """.trimIndent()

        val buttons = mutableListOf<List<InlineKeyboardButton>>()

        // Первый ряд кнопок
        buttons.add(listOf(
            InlineKeyboardButton("✍️ Изменить БИО").apply { callbackData = "EDIT_BIO" },
            InlineKeyboardButton("📸 Изменить фото").apply { callbackData = "EDIT_PHOTO" }
        ))

        // Второй ряд
        val toggleLabel = if (user.isActive) "🚫 Скрыть анкету" else "✅ Включить анкету"
        buttons.add(listOf(
            InlineKeyboardButton(toggleLabel).apply { callbackData = "TOGGLE_ACTIVE" }
        ))

        return SendMessage(user.id.toString(), info).apply {
            parseMode = "Markdown"
            replyMarkup = InlineKeyboardMarkup(buttons)
        }
    }

    fun createMainMenu(chatId: Long, text: String): SendMessage {
        return SendMessage(chatId.toString(), text).apply {
            replyMarkup = ReplyKeyboardMarkup().apply {
                keyboard = listOf(
                    KeyboardRow().apply { add("✈️ Мои планы"); add("👤 Мой профиль") },
                    KeyboardRow().apply { add("🔍 Поиск попутчиков") }
                )
                resizeKeyboard = true
            }
        }
    }

// Внутри MessageFactory.kt

    fun sendFullProfile(bot: org.telegram.telegrambots.bots.TelegramLongPollingBot, user: User) {
        val genderIcon = if (user.gender == "MALE") "👨" else "👩"
        val status = if (user.isActive) "🟢 Активен" else "🔴 Скрыт"

        val homeCity = user.homeCity?.let {
            tripService.getTranslatedName(it.translations, it.name, user.languageCode)
        } ?: "не указан"

        // Форматтер для полной даты
        val fullDateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

        // Формируем список планов
        val tripsInfo = if (user.trips.isEmpty()) {
            "_Нет активных планов_"
        } else {
            user.trips.joinToString("\n") { trip ->
                val dest = tripService.getFormattedDestinationForSearch(trip.city, trip.country, user.languageCode)
                val start = trip.travelStart?.format(fullDateFormatter) ?: "??"
                val end = trip.travelEnd?.format(fullDateFormatter) ?: "??"
                "• $dest ($start-$end)" // Полный формат дат
            }
        }

        // Текст профиля (убедись, что нет пробелов перед $tripsInfo)
        val profileText = """
$genderIcon *${user.name}, ${user.age}* | 🏠 $homeCity
📝 ${user.bio ?: "_Био не заполнено_"}

✈️ *Планы:*
$tripsInfo

Статус: $status
    """.trimIndent()

        val markup = InlineKeyboardMarkup(listOf(
            listOf(
                InlineKeyboardButton("🏠 Город").apply { callbackData = "EDIT_HOME_CITY" },
                InlineKeyboardButton("✍️ БИО").apply { callbackData = "EDIT_BIO" },
                InlineKeyboardButton("📸 Фото").apply { callbackData = "EDIT_PHOTO" }
            ),
            listOf(
                InlineKeyboardButton(if (user.isActive) "🚫 Скрыть анкету" else "✅ Включить анкету").apply { callbackData = "TOGGLE_ACTIVE" }
            )
        ))

        // Отправка (используем ранее созданную логику с DeleteMessage + SendPhoto/SendMessage)
        if (!user.photoFileId.isNullOrBlank()) {
            bot.execute(SendPhoto().apply {
                setChatId(user.id.toString())
                setPhoto(InputFile(user.photoFileId))
                caption = profileText
                parseMode = "Markdown"
                replyMarkup = markup
            })
        } else {
            bot.execute(SendMessage().apply {
                setChatId(user.id.toString())
                text = "📸 *Добавь фото!*\n\n$profileText"
                parseMode = "Markdown"
                replyMarkup = markup
            })
        }
    }

    fun createProfileMenu(chatId: Long, user: User): SendMessage {
        val statusEmoji = if (user.isActive) "✅ Виден в поиске" else "💤 Скрыт из поиска"
        val text = "👤 *Твой профиль*\n\nСтатус: $statusEmoji\n\nЗдесь ты можешь настроить свою анкету."

        val buttons = mutableListOf<List<InlineKeyboardButton>>()
        if (user.isActive) {
            buttons.add(listOf(
                InlineKeyboardButton("✍️ О себе").apply { callbackData = "EDIT_BIO" },
                InlineKeyboardButton("📸 Фото").apply { callbackData = "EDIT_PHOTO" }
            ))
            buttons.add(listOf(InlineKeyboardButton("👁 Предпросмотр").apply { callbackData = "VIEW_MY_PROFILE" }))
            buttons.add(listOf(InlineKeyboardButton("🚫 Скрыть анкету").apply { callbackData = "TOGGLE_ACTIVE" }))
        } else {
            buttons.add(listOf(InlineKeyboardButton("✅ Включить анкету").apply { callbackData = "TOGGLE_ACTIVE" }))
        }

        return SendMessage(chatId.toString(), text).apply {
            replyMarkup = InlineKeyboardMarkup(buttons)
            parseMode = "Markdown"
        }
    }

    // Добавь это в класс MessageFactory
    fun createGenderKeyboard(chatId: Long, text: String): SendMessage {
        val msg = SendMessage(chatId.toString(), text)
        val keyboard = ReplyKeyboardMarkup().apply {
            keyboard = listOf(KeyboardRow().apply { add("Мужской"); add("Женский") })
            resizeKeyboard = true
            oneTimeKeyboard = true
        }
        msg.replyMarkup = keyboard
        return msg
    }

    fun createDeleteConfirmation(chatId: Long, tripId: Long): SendMessage {
        return SendMessage(chatId.toString(), "⚠️ Ты уверен, что хочешь удалить эту поездку?").apply {
            replyMarkup = InlineKeyboardMarkup(listOf(
                listOf(
                    InlineKeyboardButton("✅ Да, удалить").apply { callbackData = "DELETE_FINAL_$tripId" },
                    InlineKeyboardButton("🔙 Отмена").apply { callbackData = "GO_TO_PLANS" }
                )
            ))
        }
    }

    // Внутри MessageFactory.kt

    // Кнопки при поиске (результаты)
    fun createSelectionButtons(chatId: Long, cities: List<City>, countries: List<Country>, lang: String): SendMessage {
        val buttons = mutableListOf<List<InlineKeyboardButton>>()

        countries.take(5).forEach { country ->
            val label = tripService.getFormattedDestinationForSearch(null, country, lang)
            buttons.add(listOf(InlineKeyboardButton(label).apply {
                callbackData = "SELECT_COUNTRY_${country.id}"
            }))
        }

        cities.take(10).forEach { city ->
            val label = tripService.getFormattedDestinationForSearch(city, null, lang)
            buttons.add(listOf(InlineKeyboardButton(label).apply {
                callbackData = "SELECT_CITY_${city.id}"
            }))
        }

        return SendMessage(chatId.toString(), "Выбери подходящий вариант:").apply {
            replyMarkup = InlineKeyboardMarkup(buttons)
        }
    }

    // Список планов с кнопкой "Удалить [Название]"
// Внутри MessageFactory.kt

    private val fullDateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun createTripsList(chatId: Long, trips: List<Trip>, lang: String): SendMessage {
        val text = StringBuilder()
        val buttons = mutableListOf<List<InlineKeyboardButton>>()

        if (trips.isEmpty()) {
            text.append("✈️ *У тебя пока нет запланированных поездок.*")
        } else {
            text.append("✈️ *Твои запланированные поездки:*\n\n")

            trips.forEachIndexed { index, trip ->
                // 1. Формируем название (город/страна)
                val destination = tripService.getFormattedDestinationForSearch(trip.city, trip.country, lang)

                // 2. Формируем СТРОГИЙ формат даты
                val start = trip.travelStart?.format(fullDateFormatter) ?: "??.??.????"
                val end = trip.travelEnd?.format(fullDateFormatter) ?: "??.??.????"
                val dateRange = "`$start-$end`"

                // 3. Добавляем текст в сообщение (без лишних отступов)
                text.append("${index + 1}. 📍 *$destination*\n   🗓 $dateRange\n\n")

                // 4. Кнопка удаления с названием
                buttons.add(listOf(
                    InlineKeyboardButton("❌ Удалить $destination").apply {
                        callbackData = "CONFIRM_DELETE_${trip.id}"
                    }
                ))
            }
        }

        // Кнопка добавить всегда снизу
        buttons.add(listOf(
            InlineKeyboardButton("➕ Добавить поездку").apply { callbackData = "ADD_TRIP" }
        ))

        return SendMessage(chatId.toString(), text.toString()).apply {
            parseMode = "Markdown"
            replyMarkup = InlineKeyboardMarkup(buttons)
        }
    }

    // В MessageFactory.kt

    // В MessageFactory.kt

    // Внутри MessageFactory.kt

    fun sendMatchProfile(bot: org.telegram.telegrambots.bots.TelegramLongPollingBot, viewer: User, match: User, currentIndex: Int, total: Int) {
        val genderIcon = if (match.gender == "MALE") "👨" else "👩"
        val fullDateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

        // Безопасное получение города проживания
        val homeCity = match.homeCity?.let {
            tripService.getTranslatedName(it.translations, it.name, viewer.languageCode)
        } ?: "не указан"

        // Формируем список планов (как в профиле)
        val tripsInfo = if (match.trips.isEmpty()) {
            "_Нет активных планов_"
        } else {
            match.trips.joinToString("\n") { trip ->
                val dest = tripService.getFormattedDestinationForSearch(trip.city, trip.country, viewer.languageCode)
                val start = trip.travelStart?.format(fullDateFormatter) ?: "??.??"
                val end = trip.travelEnd?.format(fullDateFormatter) ?: "??.??"
                "• $dest ($start-$end)"
            }
        }

        val safeBio = (match.bio ?: "_Био не заполнено_").replace("_", "\\_").replace("*", "\\*")
        val safeName = (match.name ?: "Путешественник").replace("_", "\\_").replace("*", "\\*")

        val profileText = """
🔥 *Найден попутчик!* ($currentIndex из $total)

$genderIcon *${safeName}, ${match.age ?: "??"}* | 🏠 $homeCity
📝 $safeBio

✈️ *Планы:*
$tripsInfo
    """.trimIndent()

        val buttons = mutableListOf<List<InlineKeyboardButton>>()

        // Кнопка связи
        val contactUrl = if (!match.userName.isNullOrBlank()) "https://t.me/${match.userName}" else "tg://user?id=${match.id}"
        buttons.add(listOf(InlineKeyboardButton("📩 Написать").apply { url = contactUrl }))

        // Кнопки навигации
        val navigationRow = mutableListOf<InlineKeyboardButton>()
        if (currentIndex < total) {
            navigationRow.add(InlineKeyboardButton("➡️ Следующий").apply { callbackData = "SEARCH_NEXT_$currentIndex" })
        }
        navigationRow.add(InlineKeyboardButton("🏠 В меню").apply { callbackData = "MAIN_MENU" })
        buttons.add(navigationRow)

        val markup = InlineKeyboardMarkup(buttons)

        // Отправка фото или текста
        if (!match.photoFileId.isNullOrBlank()) {
            bot.execute(SendPhoto().apply {
                setChatId(viewer.id.toString())
                setPhoto(InputFile(match.photoFileId))
                caption = profileText
                parseMode = "Markdown"
                replyMarkup = markup
            })
        } else {
            bot.execute(SendMessage().apply {
                setChatId(viewer.id.toString())
                text = "📸 *Без фото*\n\n$profileText"
                parseMode = "Markdown"
                replyMarkup = markup
            })
        }
    }

    // В MessageFactory.kt

    fun sendMainMenu(bot: org.telegram.telegrambots.bots.TelegramLongPollingBot, chatId: Long, text: String) {
        val sendMessage = org.telegram.telegrambots.meta.api.methods.send.SendMessage()
        sendMessage.chatId = chatId.toString()
        sendMessage.text = text
        sendMessage.parseMode = "Markdown"

        // Создаем кнопки главного меню
        val keyboardMarkup = org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup()
        keyboardMarkup.selective = true
        keyboardMarkup.resizeKeyboard = true
        keyboardMarkup.oneTimeKeyboard = false

        val row1 = org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow()

        row1.add("✈️ Мои планы")
        row1.add("👤 Мой профиль")

        val row2 = org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow()
        row2.add("🔍 Поиск попутчиков")


        keyboardMarkup.keyboard = listOf(row1, row2)
        sendMessage.replyMarkup = keyboardMarkup

        bot.execute(sendMessage)
    }
}