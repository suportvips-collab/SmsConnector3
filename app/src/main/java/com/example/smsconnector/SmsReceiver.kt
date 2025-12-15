package com.example.smsconnector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log

/**
 * A única responsabilidade deste BroadcastReceiver é escutar por SMS recebidos
 * e iniciar o SmsService para fazer o trabalho pesado.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            messages?.forEach { sms ->
                val sender = sms.displayOriginatingAddress ?: "Desconhecido"
                val body = sms.messageBody ?: ""

                Log.d("SMS_RECEIVER", "📨 SMS Recebido. Acordando o Serviço...")

                // Cria a Intent para iniciar o serviço
                val serviceIntent = Intent(context, SmsService::class.java).apply {
                    putExtra("sender", sender)
                    putExtra("body", body)
                }

                // Inicia o serviço em primeiro plano (necessário para APIs 26+)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("SMS_RECEIVER", "❌ Erro ao iniciar serviço: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
}
