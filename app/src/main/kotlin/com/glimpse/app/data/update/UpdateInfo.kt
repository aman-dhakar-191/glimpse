package com.glimpse.app.data.update

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    // The GitHub API asset URL (not browser_download_url) — the repo is
    // private, so downloading requires the same Bearer token, sent with an
    // Accept: application/octet-stream header.
    val downloadAssetUrl: String,
    val releaseUrl: String
)
