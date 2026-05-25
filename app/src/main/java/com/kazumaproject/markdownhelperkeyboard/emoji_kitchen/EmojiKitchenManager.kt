package com.kazumaproject.markdownhelperkeyboard.emoji_kitchen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object EmojiKitchenManagerBackup {

    data class EmojiItem(
        val char: String,
        val codepoint: String
    )

    private var emojiList: List<EmojiItem> = emptyList()
    private var combinations: Map<String, String> = emptyMap()
    private var isLoaded = false

    /**
     * assets/emoji_kitchen_data.json からメタデータをロードします。
     */
    fun loadMetadata(context: Context) {
        if (isLoaded) return
        try {
            val jsonString = context.assets.open("emoji_kitchen_data.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            // 絵文字リストのパース
            val emojisArray = root.getJSONArray("emojis")
            val parsedEmojis = mutableListOf<EmojiItem>()
            for (i in 0 until emojisArray.length()) {
                val obj = emojisArray.getJSONObject(i)
                parsedEmojis.add(
                    EmojiItem(
                        char = obj.getString("char"),
                        codepoint = obj.getString("codepoint")
                    )
                )
            }
            emojiList = parsedEmojis

            // 組み合わせマップのパース
            val comboObj = root.getJSONObject("combinations")
            val parsedCombos = mutableMapOf<String, String>()
            val keys = comboObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                parsedCombos[key] = comboObj.getString(key)
            }
            combinations = parsedCombos
            isLoaded = true
            Timber.d("EmojiKitchenManager: メタデータのロードに成功しました。絵文字数=${emojiList.size}, 組み合わせ数=${combinations.size}")
        } catch (e: Exception) {
            Timber.e(e, "EmojiKitchenManager: メタデータのロードに失敗しました。")
        }
    }

    /**
     * ロード済みの絵文字リストを返します。
     */
    fun getSupportedEmojis(context: Context): List<EmojiItem> {
        loadMetadata(context)
        return emojiList
    }

    /**
     * 1つ目の絵文字に対して、組み合わせ可能な2つ目の絵文字のコードポイントリストを返します。
     */
    fun getCombinableEmojis(context: Context, firstCodepoint: String): List<EmojiItem> {
        loadMetadata(context)
        return emojiList.filter { second ->
            val key = getComboKey(firstCodepoint, second.codepoint)
            combinations.containsKey(key)
        }
    }

    /**
     * 2つの絵文字の組み合わせから gstatic の URL を生成します。
     */
    fun getStickerUrl(context: Context, code1: String, code2: String): String? {
        loadMetadata(context)
        val key = getComboKey(code1, code2)
        val date = combinations[key] ?: return null

        // 辞書順で並び替えて uCODE1/uCODE1_uCODE2.png の形式にする
        val sortedList = listOf(code1, code2).sorted()
        val c1 = sortedList[0]
        val c2 = sortedList[1]

        return "https://www.gstatic.com/android/keyboard/emojikitchen/$date/u$c1/u${c1}_u$c2.png"
    }

    /**
     * 指定された URL から画像を非同期でダウンロードし、Bitmapとして返します。
     */
    suspend fun downloadSticker(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.inputStream
                BitmapFactory.decodeStream(inputStream)
            } else {
                Timber.e("EmojiKitchenManager: ダウンロードに失敗しました。レスポンスコード=$responseCode")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "EmojiKitchenManager: ダウンロード中に例外が発生しました。")
            null
        } finally {
            inputStream?.close()
            connection?.disconnect()
        }
    }

    /**
     * 2つのコードポイントをソートして combinations のキー形式（c1_c2）に変換します。
     */
    private fun getComboKey(code1: String, code2: String): String {
        val sorted = listOf(code1, code2).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }
}
