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
    @POST("macros/s/AKfycbwRzaf607FMAzepf8NF-KBf-DkzX9Mi0lQ-6YpC6KsqYCUonTMGPbSG7rmYxHyKWqGg/exec")
    fun sendSmsData(@Body payload: SmsPayload): Call<ResponseBody>
}
