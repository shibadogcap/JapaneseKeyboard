import Foundation
import KanaKanjiConverterModuleWithDefaultDictionary

struct EvalInput: Codable {
    var query: String
    var typo: Bool
    var roman2kana: Bool
}

struct EvalOutput: Codable {
    var query: String
    var convertTarget: String
    var top: [String]
}

@main
struct Main {
    static func main() throws {
        let args = CommandLine.arguments
        guard args.count >= 2 else {
            fputs("usage: swift-sequential-eval <input.json>\n", stderr)
            exit(2)
        }
        let url = URL(fileURLWithPath: args[1])
        let inputs = try JSONDecoder().decode([EvalInput].self, from: Data(contentsOf: url))
        let converter = KanaKanjiConverter.withDefaultDictionary()
        var outputs: [EvalOutput] = []

        for item in inputs {
            converter.stopComposition()
            var composing = ComposingText()
            let style: InputStyle = item.roman2kana ? .roman2kana : .direct
            for ch in item.query {
                composing.insertAtCursorPosition(String(ch), inputStyle: style)
            }
            let options = ConvertRequestOptions(
                N_best: 10,
                requireJapanesePrediction: .disabled,
                requireEnglishPrediction: .disabled,
                keyboardLanguage: .ja_JP,
                englishCandidateInRoman2KanaInput: true,
                fullWidthRomanCandidate: false,
                halfWidthKanaCandidate: false,
                learningType: .nothing,
                maxMemoryCount: 0,
                shouldResetMemory: false,
                memoryDirectoryURL: URL(fileURLWithPath: NSTemporaryDirectory()),
                sharedContainerURL: URL(fileURLWithPath: NSTemporaryDirectory()),
                textReplacer: .empty,
                specialCandidateProviders: [],
                typoCorrectionMode: item.typo ? .enabled : .disabled,
                metadata: nil
            )
            let result = converter.requestCandidates(composing, options: options)
            outputs.append(
                EvalOutput(
                    query: item.query,
                    convertTarget: composing.convertTarget,
                    top: result.mainResults.prefix(5).map(\.text)
                )
            )
        }

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(outputs)
        print(String(decoding: data, as: UTF8.self))
    }
}
