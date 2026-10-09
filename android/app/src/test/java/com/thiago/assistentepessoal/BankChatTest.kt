package com.thiago.assistentepessoal

import com.thiago.assistentepessoal.tools.bankChatQuery
import org.junit.Assert.*
import org.junit.Test

class BankChatTest {
    @Test fun variedBalanceQuestions(){
        listOf("quanto eu tenho no meu banco?","Coi, qual é meu saldo?","Koi, veja meu saldo no Inter", "Quanto dinheiro temos no Inter?").forEach{assertEquals(it,"balance",bankChatQuery(it))}
    }
    @Test fun planningQuestions(){
        listOf("Meu saldo cobre as próximas contas?","O saldo é suficiente para as contas?","O que preciso reservar esta semana?","Quanto separar para as contas?").forEach{assertEquals(it,"plan",bankChatQuery(it))}
    }
    @Test fun keepsOtherMoneyFlows(){
        listOf("Qual meu saldo do Poe?","Saldo do orçamento","Recebi 50 no Inter","Anote meu saldo de 100","Gastei 30 no banco","Quais contas faltam pagar?","Quanto é 1 mais 1?","Abra o Inter","O que é saldo bancário?","Como funciona o Inter?").forEach{assertNull(it,bankChatQuery(it))}
    }
}
