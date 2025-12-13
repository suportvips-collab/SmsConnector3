package com.example.smsconnector

import android.util.Log
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmtpSender {

    // CONFIGURAÇÕES DA HOSTINGER (Substitua pelos seus dados reais)
    private const val SMTP_HOST = "smtp.hostinger.com"
    private const val SMTP_PORT = "465" // Porta SSL
    private const val SMTP_USER = "suportvips@master.suportvip.com" // Crie um email: no-reply@...
    private const val SMTP_PASS = "M1lh&1r02025"

    suspend fun sendEmail(toEmail: String, subject: String, body: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val props = Properties().apply {
                    put("mail.smtp.host", SMTP_HOST)
                    put("mail.smtp.port", SMTP_PORT)
                    put("mail.smtp.auth", "true")
                    // Configuração SSL moderna e correta para Jakarta Mail
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.ssl.protocols", "TLSv1.2") // Forçar TLS 1.2
                    put("mail.debug", "true") // Logs detalhados do SMTP
                    // As propriedades abaixo (socketFactory) são legadas e foram removidas
                    // put("mail.smtp.socketFactory.port", SMTP_PORT)
                    // put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(SMTP_USER, SMTP_PASS)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(SMTP_USER, "SMS Connector App"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    setSubject(subject)
                    setText(body)
                }

                Transport.send(message)
                Log.d("SMTP", "📧 E-mail enviado via Hostinger para $toEmail")
                true
            } catch (e: Exception) {
                Log.e("SMTP", "❌ Erro ao enviar e-mail: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
}
