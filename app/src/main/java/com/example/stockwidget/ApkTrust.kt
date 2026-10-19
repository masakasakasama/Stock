package com.example.stockwidget

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

object ApkTrust {
    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File, expectedVersion: Int? = null) {
        val pm = context.packageManager
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            ?: error("APKを解析できません")
        require(candidate.packageName == context.packageName) { "別アプリのAPKです" }
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val actual = candidate.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        val trusted = installed.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        require(actual.isNotEmpty() && actual == trusted) { "署名が違います。通常更新はできません。バックアップ検証前に旧アプリを削除しないでください" }
        require(candidate.versionCode > installed.versionCode) { "旧版への更新を拒否しました" }
        if (expectedVersion != null) require(candidate.versionCode == expectedVersion) { "配布バージョンとAPKが不一致です" }
    }
}
