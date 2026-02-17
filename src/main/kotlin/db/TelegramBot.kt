package org.example.db

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

@Component
class TelegramBot(
    private val userRepository: UserRepository,
    @Value("\${bot.token}") private val botToken: String,
    @Value("\${bot.username}") private val botName: String
) : TelegramLongPollingBot(botToken) {

    override fun getBotUsername(): String = botName

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage() && update.message.hasText()) {
            val messageText = update.message.text
            val chatId = update.message.chatId
            val firstName = update.message.from.firstName


            // Создаем объект пользователя для БД
            val userToSave = User(
                name = firstName+"test22222",
                sex = messageText,
                age = 123
            )

            try {
                // Сохраняем в PostgreSQL
                userRepository.save(userToSave)
                

                // Отвечаем пользователю
                sendNotification(chatId, "Записано в базу: $messageText")
            } catch (e: Exception) {
                sendNotification(chatId, "Ошибка БД: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun sendNotification(chatId: Long, text: String) {
        val msg = SendMessage()
        msg.setChatId(chatId.toString())
        msg.text = text
        try {
            execute(msg)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
}