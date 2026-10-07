package com.thiago.assistentepessoal.chat

data class ChatContextEntry(val role: String, val content: String)

fun recentChatContext(messages: List<ChatMessage>, current: ChatMessage): List<ChatContextEntry> {
    var remaining = 12000
    val selected = mutableListOf<ChatContextEntry>()
    messages.asSequence()
        .filter { it.id != current.id && it.occurredAt <= current.occurredAt &&
            it.status == MessageStatus.SENT && it.role in setOf("user", "assistant") &&
            it.content.isNotBlank() }
        .sortedWith(compareByDescending<ChatMessage> { it.occurredAt }.thenByDescending { it.sequence })
        .take(20)
        .forEach { message ->
            if (remaining > 0) {
                val content = message.content.take(minOf(4000, remaining))
                selected.add(ChatContextEntry(message.role, content))
                remaining -= content.length
            }
        }
    return selected.reversed()
}
