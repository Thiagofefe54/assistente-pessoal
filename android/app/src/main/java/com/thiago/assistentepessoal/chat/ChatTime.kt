package com.thiago.assistentepessoal.chat

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun messageDate(timestamp: Long, timezone: String): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.of(timezone)).toLocalDate()

fun messageTime(message: ChatMessage): String = Instant.ofEpochMilli(message.occurredAt)
    .atZone(ZoneId.of(message.timezone)).format(DateTimeFormatter.ofPattern("HH:mm"))

fun dayLabel(date: String): String = LocalDate.parse(date)
    .format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR")))
