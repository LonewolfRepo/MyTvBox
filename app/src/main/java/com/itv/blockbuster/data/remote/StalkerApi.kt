package com.itv.blockbuster.data.remote

import com.itv.blockbuster.data.remote.dto.CategoryResponse
import com.itv.blockbuster.data.remote.dto.ChannelListResponse
import com.itv.blockbuster.data.remote.dto.CreateLinkResponse
import com.itv.blockbuster.data.remote.dto.EpgTableResponse
import com.itv.blockbuster.data.remote.dto.EpgWeekResponse
import com.itv.blockbuster.data.remote.dto.HandshakeResponse
import com.itv.blockbuster.data.remote.dto.ProfileResponse
import com.itv.blockbuster.data.remote.dto.VodListResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface StalkerApi {

    @GET
    suspend fun handshake(@Url url: String): HandshakeResponse

    @GET
    suspend fun getProfile(@Url url: String): ProfileResponse

    @GET
    suspend fun getLiveCategories(@Url url: String): CategoryResponse

    @GET
    suspend fun getLiveChannels(@Url url: String): ChannelListResponse

    @GET
    suspend fun getVodCategories(@Url url: String): CategoryResponse

    @GET
    suspend fun getSeriesCategories(@Url url: String): CategoryResponse

    @GET
    suspend fun getVodList(@Url url: String): VodListResponse

    @GET
    suspend fun createLink(@Url url: String): CreateLinkResponse

    @GET
    suspend fun getShortEpg(@Url url: String): EpgTableResponse

    @GET
    suspend fun getEpgTable(@Url url: String): EpgTableResponse

    @GET
    suspend fun getEpgWeek(@Url url: String): EpgWeekResponse
}