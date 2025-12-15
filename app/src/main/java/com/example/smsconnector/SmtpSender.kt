import android.content.Context
import android.provider.Settings
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SmsSenderService(private val context: Context) {

    // URL do seu Web App (Do Deploy que você fez no Google Apps Script)
    // IMPORTANTE: Deve terminar em /exec
    private val SCRIPT_URL = "https://script.google.com/macros/s/SEU_ID_DO_SCRIPT_AQUI/exec"

    private val client = OkHttpClient.Builder()
        .followRedirects(true) // CRUCIAL: O Google redireciona a requisição
        .followSslRedirects(true)
        .build()

    fun sendSmsToSheet(smsBody: String, senderPhone: String, userToken: String) {

        // 1. Pegar o ID do Dispositivo (O mesmo que será salvo na planilha)
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // 2. Montar o JSON (AQUI ESTÁ A REFERÊNCIA CORRETA COM O SCRIPT)
        val json = JSONObject()
        try {
            // As chaves à esquerda DEVEM ser iguais às do script (data.license_key, etc.)
            json.put("license_key", userToken)      // Script espera: license_key
            json.put("device_id", deviceId)         // Script espera: device_id
            json.put("sms_content", smsBody)        // Script espera: sms_content
            json.put("sender_number", senderPhone)  // Script espera: sender_number
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        // 3. Preparar a Requisição
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(SCRIPT_URL)
            .post(body)
            .build()

        // 4. Enviar em Thread Separada (Network não pode rodar na Main Thread)
        // Se estiver usando Coroutines, use Dispatchers.IO
        Thread {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    Log.d("SuportvipSMS", "Sucesso: $responseBody")
                    // Aqui você pode tratar a resposta "success_filtered" se quiser
                } else {
                    Log.e("SuportvipSMS", "Erro no envio: ${response.code}")
                }
            } catch (e: IOException) {
                Log.e("SuportvipSMS", "Falha de conexão: ${e.message}")
            }
        }.start()
    }
}