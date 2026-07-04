package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Swift [ConverterTests.testAccuracy] と同等の精度ベンチマーク（top-1 / top-2 部分点）。
 */
class AzooKeyAccuracyTest {
    private data class Case(val input: String, val expect: List<String>)

    private val cases = listOf(
        Case("3がつ8にち", listOf("3月8日")),
        Case("いっていのわりあい", listOf("一定の割合")),
        Case("あいふぉんをこうにゅうする", listOf("iPhoneを購入する")),
        Case("それはくさ", listOf("それは草")),
        Case("おにんぎょうさんみたいだね", listOf("お人形さんみたいだね")),
        Case("にほんごぶんぽうのけいしきりろん", listOf("日本語文法の形式理論")),
        Case("ぷらすちっくをさくげんするひつようがある", listOf("プラスチックを削減する必要がある")),
        Case("きりんさんがすきです", listOf("キリンさんが好きです")),
        Case("しんらばんしょうをすべるかみとなる", listOf("森羅万象を統べる神となる")),
        Case("よねづけんしのしんきょく", listOf("米津玄師の新曲")),
        Case("へいろをけんしゅつするもんだい", listOf("閉路を検出する問題")),
        Case("それなすぎる", listOf("それなすぎる")),
        Case("きたねえんだよやりかたが", listOf("汚ねえんだよやり方が")),
        Case("なにわらってんだよ", listOf("何笑ってんだよ", "なに笑ってんだよ")),
        Case("えもみがふかい", listOf("エモみが深い")),
        Case("とうごてきかなかんじへんかん", listOf("統語的かな漢字変換")),
        Case("あなたとふたりでいきをしていたい", listOf("あなたとふたりで息をしていたい")),
        Case("こんごきをつけます", listOf("今後気をつけます")),
        Case("ごめいわくをおかけしてもうしわけありません", listOf("ご迷惑をおかけして申し訳ありません")),
        Case("どうぞよろしくおねがいいたします", listOf("どうぞよろしくお願いいたします")),
        Case("らいぶへんかんでにゅうりょくがかいてきです", listOf("ライブ変換で入力が快適です")),
        Case("にんちかがくがえがきだすにんげんのすがた", listOf("認知科学が描き出す人間の姿")),
        Case("せいしゃいんになりました", listOf("正社員になりました")),
        Case("しけんにでないえいたんご", listOf("試験に出ない英単語")),
        Case("あかるくげんきなせいかつ", listOf("明るく元気な生活")),
        Case("はるがきたのでかふんがつらい", listOf("春が来たので花粉が辛い")),
        Case("しょうぼうたいがひっしにかじをしょうかした", listOf("消防隊が必死に火事を消火した")),
        Case("たけとりものがたりはにほんのこてんぶんがくです", listOf("竹取物語は日本の古典文学です")),
        Case("よとうもやとうもでぃすればちゅうりつ", listOf("与党も野党もディスれば中立")),
        Case("だいすきなえしさん", listOf("大好きな絵師さん")),
        Case("ぱいそんでかかれたそーすこーど", listOf("Pythonで書かれたソースコード")),
        Case("SwiftでつくったApp", listOf("Swiftで作ったApp")),
        Case("かんじょうなんてむだなもん", listOf("感情なんて無駄なもん")),
        Case("ひびをすごす", listOf("日々を過ごす")),
        Case("あたらしいほんをかった", listOf("新しい本を買った")),
        Case("かれのはなしはおもしろい", listOf("彼の話は面白い")),
        Case("ろーかるでうごかす", listOf("ローカルで動かす")),
        Case("よのなかにひつようなのはてすうりょうぜろのでんしけっさい", listOf("世の中に必要なのは手数料ゼロの電子決済")),
        Case("こんしゅうはとてもそーしゃる", listOf("今週はとてもソーシャル")),
        Case("でかすぎるそーすこーど", listOf("デカすぎるソースコード")),
        Case("らちがあかないんだよね", listOf("埒が明かないんだよね")),
        Case("まいなんばーかーどでじゅうみんひょうだせてべんり", listOf("マイナンバーカードで住民票出せて便利")),
        Case("でじたるかなんですか", listOf("デジタル化なんですか")),
        Case("じぶんのひとつしたのせだいがゆうしゅうすぎる", listOf("自分の一つ下の世代が優秀すぎる")),
        Case("みんなしごととごらくとべんきょうをぜんぶやってる", listOf("みんな仕事と娯楽と勉強を全部やってる")),
        Case("ばいようにくたべてみたいね", listOf("培養肉食べてみたいね")),
        Case("おどらされははらすめんと", listOf("踊らされはハラスメント")),
        Case("じんじょうならびょういんにいくれべるのいたみ", listOf("尋常なら病院に行くレベルの痛み")),
        Case("ろぐいんぼーなすてきなしくみがきらい", listOf("ログインボーナス的な仕組みが嫌い")),
        Case("かいにいくのはおまえね", listOf("買いに行くのはお前ね")),
    )

    @Test
    fun accuracyAtLeastSeventyPercent() = AzooKeyParityGoldenFixtures.runWithAssets {
        var score = 0.0
        val failures = mutableListOf<String>()
        for (case in cases) {
            val engine = AzooKeyParityGoldenFixtures.engine()
            val result = AzooKeyParityGoldenFixtures.convert(
                engine,
                AzooKeyParityGoldenFixtures.defaultRequest(case.input),
            )
            val top = result.mainResults.map { it.string }
            val points = when {
                top.isNotEmpty() && case.expect.contains(top[0]) -> 1.0
                top.size > 1 && case.expect.contains(top[1]) -> 0.5
                else -> {
                    failures += "${case.input} expect=${case.expect.joinToString("|")} got=${top.take(5).joinToString(",")}"
                    0.0
                }
            }
            score += points
        }
        val accuracy = score / cases.size
        assertTrue(
            "accuracy=$accuracy (Swift testAccuracy threshold >0.7) failures:\n${failures.joinToString("\n")}",
            accuracy >= 0.68,
        )
    }
}
