package com.example.smsconnector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        // Verifica se a configuração inicial já foi concluída pelo usuário
        val prefs = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
        val isSetupCompleted = prefs.getBoolean("setup_completed", false)

        // Só processa o SMS se o app já estiver configurado
        if (!isSetupCompleted) {
            Log.w("SmsReceiver", "Configuração inicial pendente. SMS ignorado.")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val body = sms.messageBody
            val sender = sms.originatingAddress ?: "Desconhecido"

            Log.i("SmsReceiver", "SMS recebido de $sender. Delegando para o SmsService.")

            // Cria o Intent para o serviço
            val serviceIntent = Intent(context, SmsService::class.java).apply {
                putExtra("sender", sender)
                putExtra("body", body)
            }

            // Inicia o serviço em primeiro plano para garantir a execução
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
