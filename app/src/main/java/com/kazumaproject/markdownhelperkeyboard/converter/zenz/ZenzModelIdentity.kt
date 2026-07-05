package com.kazumaproject.markdownhelperkeyboard.converter.zenz

/**
 * 最後に [com.kazumaproject.zenz.ZenzEngine.initModel] した weight パス。
 * Swift PredictiveInputCacheContext.weightURL 相当のキャッシュ無効化キーに使う。
 */
object ZenzModelIdentity {
    @Volatile
    var currentModelPath: String = ""
        private set

    fun update(modelPath: String) {
        currentModelPath = modelPath
    }
}
