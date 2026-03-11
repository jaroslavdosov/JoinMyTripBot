package org.example.bot

import jakarta.annotation.PostConstruct

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault

@Component
class TelegramBot(
    @Value("\${bot.token}") private val botToken: String,
    @Value("\${bot.username}") private val botName: String,
    private val updateHandler: UpdateHandler
) : TelegramLongPollingBot(botToken) {

    override fun getBotUsername(): String = botName

    override fun onUpdateReceived(update: Update) {
        try {
            updateHandler.handleUpdate(update, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @PostConstruct
    fun init() {
        val commands = listOf(BotCommand("/start", "Сброс"), BotCommand("/menu", "Меню"))
        execute(SetMyCommands(commands, BotCommandScopeDefault(), null))
    }
}