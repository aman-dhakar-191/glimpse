package com.glimpse.app.data.update

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    // The GitHub API asset URL (not browser_download_url) — the repo is
    // private, so downloading requires the same Bearer token, sent with an
    // Accept: application/octet-stream header.
    val downloadAssetUrl: String,
    // GitHub's auto-generated release body (see generate_release_notes in
    // build.yml), with GitHub-specific link boilerplate stripped — shown
    // in-app only, never linked out to github.com.
    val releaseNotes: String
)
