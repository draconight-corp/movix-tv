package com.example.movix.data

import com.example.movix.MovixApp
import com.example.movix.config.MovixConfig
import com.example.movix.update.GithubApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(FlexLinkAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Réécrit l'hôte de toute requête qui pointe vers un domaine api.movix.*
     * vers l'hôte actif configuré au moment de la requête. Permet de changer
     * le domaine Movix sans relancer l'app et sans recréer Retrofit.
     */
    private val hostInterceptor = Interceptor { chain ->
        val req = chain.request()
        val host = req.url.host
        val isMovixApi = host.startsWith("api.movix.") ||
                host == "api.movix.cloud" || host == "api.movix.tax" || host == "api.movix.health"
        if (!isMovixApi) return@Interceptor chain.proceed(req)

        val current = MovixConfig.currentApiHost(MovixApp.instance)
        if (current == host) return@Interceptor chain.proceed(req)

        val rewritten = req.url.newBuilder().host(current).build()
        chain.proceed(req.newBuilder().url(rewritten).build())
    }

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(hostInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .addInterceptor { chain ->
                val req = chain.request()
                // Headers spécifiques aux requêtes Movix
                if (req.url.host.startsWith("api.movix.")) {
                    val site = MovixConfig.currentSiteHost(MovixApp.instance)
                    val newReq = req.newBuilder()
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) MovixTV/1.0"
                        )
                        .header("Accept", "application/json, */*")
                        .header("Origin", "https://$site")
                        .header("Referer", "https://$site/")
                        .build()
                    chain.proceed(newReq)
                } else {
                    chain.proceed(req)
                }
            }
            .build()
    }

    val tmdb: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.TMDB_BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApi::class.java)
    }

    val movix: MovixApi by lazy {
        // Base URL = placeholder ; le hostInterceptor réécrit le host à la volée.
        Retrofit.Builder()
            .baseUrl("https://${MovixConfig.DEFAULT_API_HOST}/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(MovixApi::class.java)
    }

    val github: GithubApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
            .create(GithubApi::class.java)
    }
}
