package com.thiago.assistentepessoal.tools

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.net.Uri

fun openIntent(context:Context,intent:Intent){
    try{context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
    catch(e:ActivityNotFoundException){Toast.makeText(context,"Nenhum aplicativo disponível para esta ação.",Toast.LENGTH_LONG).show()}
    catch(e:SecurityException){Toast.makeText(context,"O Android bloqueou esta abertura. Confira as permissões ou abra o aplicativo diretamente.",Toast.LENGTH_LONG).show()}
}
fun shareText(context:Context,title:String,text:String)=openIntent(context,Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
    type="text/plain";putExtra(Intent.EXTRA_SUBJECT,title);putExtra(Intent.EXTRA_TEXT,"$title\n\n$text")
},"Compartilhar com…"))
fun searchWeb(context:Context,query:String)=openIntent(context,Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(query))))

/** Bounded decimal parser: no eval, executable names or floating-point money. */
fun calculate(expression:String):String {
    val source=expression.trim().replace(',','.').replace('×','*').replace('÷','/')
    require(source.length in 1..160 && source.all{it.isDigit() || it in " .+-*/()%"}) {"Use números, +, −, ×, ÷, % e parênteses."}
    class Parser {
        var position=0;var depth=0
        val precision=java.math.MathContext(24,java.math.RoundingMode.HALF_UP)
        fun space(){while(position<source.length && source[position].isWhitespace())position++}
        fun eat(c:Char):Boolean{space();if(position<source.length && source[position]==c){position++;return true};return false}
        fun factor():java.math.BigDecimal {
            space();require(++depth<=32){"Expressão muito complexa."}
            val value=when{
                eat('+')->factor()
                eat('-')->factor().negate()
                eat('(')->sum().also{require(eat(')')){"Feche os parênteses."}}
                else->{val start=position;while(position<source.length && (source[position].isDigit() || source[position]=='.'))position++
                    require(position>start){"Expressão incompleta."};java.math.BigDecimal(source.substring(start,position),precision)}
            }
            depth--
            return if(eat('%'))value.divide(java.math.BigDecimal(100),precision) else value
        }
        fun product():java.math.BigDecimal{var value=factor();while(true)value=when{eat('*')->value.multiply(factor(),precision);eat('/')->value.divide(factor(),precision);else->return value}}
        fun sum():java.math.BigDecimal{var value=product();while(true)value=when{eat('+')->value.add(product(),precision);eat('-')->value.subtract(product(),precision);else->return value}}
    }
    val parser=Parser();val result=parser.sum();parser.space();require(parser.position==source.length){"Confira a expressão."}
    require(result.precision()-result.scale()<=60 && result.scale()<=60){"Resultado muito grande."}
    return result.stripTrailingZeros().toPlainString().replace('.',',')
}
fun calculationReply(message:String):String? {
    var expression=message.trim().removeSuffix("?").trim()
    expression=expression.replace(Regex("^(?:koi[,!]?\\s+)?(?:calcule|calcula|quanto (?:é|e|dá|da)|quanto custa)\\s+",RegexOption.IGNORE_CASE),"")
    if(!expression.matches(Regex("[0-9\\s.,+*/()%×÷-]+")) || expression.none{it in "+-*/%×÷"})return null
    return runCatching{"${calculate(expression)} 💜"}.getOrElse{"Não consegui calcular: ${it.message ?: "confira a expressão"}"}
}
