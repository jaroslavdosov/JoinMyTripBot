package org.example.bot

import org.example.entity.city.City
import org.example.entity.country.Country
import org.example.entity.trip.Trip
import org.example.entity.user.User
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
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

    fun createMainMenu(chatId: Long, text: String): SendMessage {
        return SendMessage(chatId.toString(), text).apply {
            replyMarkup = ReplyKeyboardMarkup().apply {
                keyboard = listOf(
                    KeyboardRow().apply { add("✈️ Мои планы"); add("👤 Мой профиль") },
                    KeyboardRow().apply { add("⚙️ Поиск попутчиков") }
                )
                resizeKeyboard = true
            }
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
            buttons.add(listOf(InlineKeyboardButton("🚫 Скрыть профиль").apply { callbackData = "TOGGLE_ACTIVE" }))
        } else {
            buttons.add(listOf(InlineKeyboardButton("✅ Включить профиль").apply { callbackData = "TOGGLE_ACTIVE" }))
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
    fun createTripsList(chatId: Long, trips: List<Trip>, lang: String): SendMessage {
        val text = StringBuilder()
        val buttons = mutableListOf<List<InlineKeyboardButton>>()

        if (trips.isEmpty()) {
            text.append("✈️ *У тебя пока нет запланированных поездок.*")
        } else {
            text.append("✈️ *Твои запланированные поездки:*\n\n")

            trips.forEachIndexed { index, trip ->
                // Используем ту же логику именования, что и в поиске
                val destination = tripService.getFormattedDestinationForSearch(trip.city, trip.country, lang)
                val dateRange = "🗓 `${trip.travelStart} - ${trip.travelEnd}`"

                text.append("${index + 1}. 📍 $destination\n$dateRange\n\n")

                // Кнопка удаления с названием под каждым маршрутом
                buttons.add(listOf(
                    InlineKeyboardButton("❌ Удалить $destination").apply {
                        callbackData = "CONFIRM_DELETE_${trip.id}"
                    }
                ))
            }
        }

        buttons.add(listOf(InlineKeyboardButton("➕ Добавить поездку").apply { callbackData = "ADD_TRIP" }))

        return SendMessage(chatId.toString(), text.toString()).apply {
            parseMode = "Markdown"
            replyMarkup = InlineKeyboardMarkup(buttons)
        }
    }
}