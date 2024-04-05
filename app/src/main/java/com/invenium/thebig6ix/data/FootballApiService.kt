package com.invenium.thebig6ix.data

import retrofit2.http.GET
import retrofit2.http.Query

interface FootballApiService {
    @GET("competitions/2003/matches")
    suspend fun getFootballFixtures(@Query("matchday") matchday: Int): FootballFixturesResponse
}