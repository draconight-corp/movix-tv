package com.example.movix.update

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GithubRelease(
    @Json(name = "tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @Json(name = "html_url") val htmlUrl: String? = null,
    val assets: List<GithubAsset> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GithubAsset(
    val name: String,
    val size: Long,
    @Json(name = "browser_download_url") val browserDownloadUrl: String
)
