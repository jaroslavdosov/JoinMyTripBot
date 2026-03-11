package org.example.bot

import org.example.entity.user.User
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import java.time.format.DateTimeFormatter

@Component
class MessageFactory {

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
}