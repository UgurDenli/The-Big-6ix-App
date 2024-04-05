package com.invenium.thebig6ix.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiHelper {
    private const val BASE_URL = "http://api.football-data.org/v4/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun getFootballApiService(): FootballApiService {
        return retrofit.create(FootballApiService::class.java)
    }
}
