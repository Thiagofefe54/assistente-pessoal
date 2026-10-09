package com.thiago.assistentepessoal.tools

import java.text.Normalizer
import java.util.Locale

/** High-confidence reads only. Other messages keep the normal assistant flow. */
internal fun bankChatQuery(message:String):String? {
    val text=Normalizer.normalize(message.lowercase(Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    fun has(pattern:String)=Regex(pattern).containsMatchIn(text)
    if(has("\\b(o que (e|significa)|como funciona|explique|explica|defina)\\b"))return null
    if(has("\\b(poe|groq|tokens?|pontos|gemini|credito de ia)\\b"))return null
    if(has("\\b(salve|salva|anote|anota|registre|registra|gastei|recebi|paguei|transferi)\\b"))return null
    val bank=has("\\b(inter|banco|bancari[oa])\\b")
    val balance=has("\\bsaldo\\b") || (bank && has("\\b(quanto|dinheiro|disponivel|tenho|temos)\\b"))
    val planning=has("\\b(cobre|cobrir|chega|suficiente|sobra|sobrar|reservar|separar|guardar)\\b") &&
        has("\\b(contas?|vencimentos?|semana|mes)\\b")
    if(has("\\bsaldo (do|de) (orcamento|mes|periodo)\\b") && !bank)return null
    if(planning && (balance || bank || has("\\b(reservar|separar)\\b")))return "plan"
    if(balance && (bank || has("\\b(meu|nosso|consultar|consulte|confere|conferir|veja|ver|qual|quanto|saldo)\\b")))return "balance"
    return null
}
