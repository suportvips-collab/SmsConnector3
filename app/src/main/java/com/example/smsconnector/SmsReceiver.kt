package com.example.smsconnector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.e("SMS_DEBUG", "📢 O RECEIVER ACORDOU! Ação recebida: ${intent.action}")

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            messages?.forEach { sms ->
                val sender = sms.displayOriginatingAddress ?: "Desconhecido"
                val body = sms.messageBody ?: ""

                Log.d("SMS_DEBUG", "Recebido de: $sender")

                val prefs = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
                val targetEmail = prefs.getString("target_email", "")
                val licenseKey = prefs.getString("license_key", "")

                if (!targetEmail.isNullOrEmpty() && !licenseKey.isNullOrEmpty()) {
                    sendToApi(licenseKey, targetEmail, sender, body)
                }
            }
        }
    }

    private fun sendToApi(license: String, email: String, sender: String, body: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ApiService::class.java)

        // Cria o objeto de dados (payload) usando a data class.
        val payload = SmsPayload(
            licenseKey = license,
            targetEmail = email,
            smsSender = sender,
            smsBody = body,
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        // Envia o objeto payload, que o Retrofit converterá para JSON.
        api.sendSmsData(payload).enqueue(object : retrofit2.Callback<ResponseBody> {
            override fun onResponse(call: retrofit2.Call<ResponseBody>, response: retrofit2.Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val responseString = response.body()?.string()
                    Log.d("API_SUCCESS", "✅ Sucesso! Código: ${response.code()}. Resposta: $responseString")
                } else {
                    val errorBodyString = response.errorBody()?.string()
                    Log.e("API_ERROR", "❌ O servidor respondeu com ERRO: ${response.code()}. Resposta: $errorBodyString")
                }
            }

            override fun onFailure(call: retrofit2.Call<ResponseBody>, t: Throwable) {
                Log.e("API_ERROR", "💀 Falha grave na conexão: ${t.message}")
            }
        })
    }
}
