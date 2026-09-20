package com.devlens.network

import com.devlens.data.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface DevLensApiService {

    @GET("/health")
    suspend fun checkHealth(
        @Header("X-Mesh-Token") token: String
    ): HealthResponse

    @GET("/system")
    suspend fun getSystemInfo(
        @Header("X-Mesh-Token") token: String
    ): SystemInfoResponse

    @POST("/pair")
    suspend fun pairNode(
        @Body request: PairRequest
    ): PairResponse

    @POST("/query")
    suspend fun queryFiles(
        @Header("X-Mesh-Token") token: String,
        @Body request: FileQueryRequest
    ): FileQueryResponse

    @POST("/read-file")
    suspend fun readFile(
        @Header("X-Mesh-Token") token: String,
        @Body request: ReadFileRequest
    ): ReadFileResponse

    @GET("/permissions")
    suspend fun checkPermissions(
        @Header("X-Mesh-Token") token: String
    ): PermissionAuditReport

    @POST("/permissions/fix")
    suspend fun fixPermission(
        @Header("X-Mesh-Token") token: String,
        @Body request: PermissionFixRequest
    ): PermissionFixResponse

    companion object {
        val fastClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()
        }

        val pairingClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        val auditClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }

        val fileClient: OkHttpClient by lazy {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(logging)
                .build()
        }

        fun create(
            baseUrl: String,
            isPairing: Boolean = false,
            isAudit: Boolean = false,
            isFile: Boolean = false,
            client: OkHttpClient? = null
        ): DevLensApiService {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val httpUrl = normalizedUrl.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Nieprawidłowy adres URL węzła: $normalizedUrl")

            val okClient = client ?: when {
                isPairing -> pairingClient
                isAudit -> auditClient
                isFile -> fileClient
                else -> fastClient
            }

            return Retrofit.Builder()
                .baseUrl(httpUrl)
                .client(okClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DevLensApiService::class.java)
        }
    }
}
