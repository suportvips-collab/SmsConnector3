package com.example.smsconnector

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Interface que define os endpoints da API para o Retrofit.
 */
interface ApiService {
    /**
     * Envia os dados de um SMS para o endpoint do Google Apps Script.
     *
     * @param payload O objeto SmsPayload, que será convertido para JSON e enviado no corpo da requisição.
     * @return Um objeto Call<ResponseBody> que permite a leitura da resposta crua do servidor.
     */
    @POST("https://script.google.com/macros/s/AKfycbw6U1f8ccnH3V5_Vw386g6aSGRF7sTJdFGDU24wBl66aoHNcd1oDwIfcYXcS1_H-2qI/exec")
    fun sendSmsData(@Body payload: SmsPayload): Call<ResponseBody>
}
