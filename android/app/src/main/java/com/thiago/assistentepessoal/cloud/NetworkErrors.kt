package com.thiago.assistentepessoal.cloud

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Explain transport failures without changing DNS, TLS, or retrying an action. */
fun connectionMessage(error:IOException):String=when(error) {
    is UnknownHostException -> "Não consegui encontrar o endereço do servidor da Koi. Confira a internet; se estiver usando VPN ou DNS privado, teste outra rede e tente novamente."
    is SocketTimeoutException -> "A conexão demorou mais do que o esperado. O servidor pode estar iniciando. Aguarde um pouco e tente novamente."
    is SSLException -> "Não consegui confirmar a conexão segura. Confira a data e a hora do celular ou tente outra rede."
    is NoRouteToHostException -> "Não encontrei um caminho até o servidor. Confira a rede e tente novamente."
    is ConnectException -> "Não consegui conectar ao servidor da Koi. Confira a internet e tente novamente."
    is SocketException -> "A conexão foi interrompida. Confira a rede e tente novamente."
    else -> error.message ?: "Não consegui acessar a nuvem. Confira a conexão e tente novamente."
}

fun savedConnectionMessage(message:String?):String = when {
    message==null -> "Envio não concluído."
    message.startsWith("Unable to resolve host") -> connectionMessage(UnknownHostException())
    else -> message
}
