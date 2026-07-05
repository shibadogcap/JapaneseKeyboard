#include <jni.h>
#include <string>
#include <vector>
#include <array>
#include <mutex>
#include <atomic>
#include <cstdint>
#include <cmath>
#include <unordered_map>
#include <limits>
#include <android/log.h>
#include "llama.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "zenz-bridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "zenz-bridge", __VA_ARGS__)

// モデルと語彙のみグローバルで保持する。
// コンテキストはセッションで使い回す。
static llama_model *g_model = nullptr;
static const llama_vocab *g_vocab = nullptr;

// ランタイム設定用パラメータ（Kotlin から変更可能）
static int g_param_n_ctx = 512;
static int g_param_n_threads = 4;
static int g_param_n_threads_batch = 4;
static int g_param_n_batch = 512;
static std::mutex g_param_mutex;   // 設定値の読み書き用
static std::atomic<uint64_t> g_request_seq{0};
static bool g_backend_initialized = false;

struct RuntimeConfig {
    int n_ctx;
    int n_threads;
    int n_threads_batch;
    int n_batch;
};

struct ZenzSession {
    llama_context *ctx = nullptr;
    RuntimeConfig config{0, 0, 0, 0};
    std::mutex mutex;
};

static ZenzSession g_session;

// 候補評価の結果タイプ
enum class CandidateEvaluationResultType {
    ERROR,
    PASS,
    FIX_REQUIRED,
    WHOLE_RESULT
};

// 候補評価の結果
struct CandidateEvaluationResult {
    CandidateEvaluationResultType type;
    float score;                // PASS の場合のスコア
    std::string prefix;         // FIX_REQUIRED の場合の接頭辞
    std::string whole_result;   // WHOLE_RESULT の場合の結果
    std::vector<std::pair<float, std::string>> alternatives; // requestRich 時の ALT 候補
};

static std::string build_v3_prompt(
        const std::string &profile,
        const std::string &topic,
        const std::string &style,
        const std::string &preference,
        const std::string &left,
        const std::string &right,
        const std::string &input,
        bool include_output_tag
) {
    const std::string inputTag = u8"\uEE00";
    const std::string contextTag = u8"\uEE02";
    const std::string profileTag = u8"\uEE03";
    const std::string topicTag = u8"\uEE04";
    const std::string styleTag = u8"\uEE05";
    const std::string preferenceTag = u8"\uEE06";
    const std::string rightContextTag = u8"\uEE07";
    const std::string outputTag = u8"\uEE01";

    std::string conditions;
    if (!profile.empty()) conditions += profileTag + profile;
    if (!topic.empty()) conditions += topicTag + topic;
    if (!style.empty()) conditions += styleTag + style;
    if (!preference.empty()) conditions += preferenceTag + preference;

    std::string prompt = conditions;
    if (!left.empty()) {
        prompt += contextTag + left;
    }
    if (!right.empty()) {
        prompt += rightContextTag + right;
    }
    prompt += inputTag + input;
    if (include_output_tag) {
        prompt += outputTag;
    }
    return prompt;
}

// ------- JNI文字列変換（重要） -------
// llama_token_to_piece() が返すバイト列は不正UTF-8になり得るため、NewStringUTFは禁止。
// UTF-8(不正あり得る) -> UTF-16(不正は U+FFFD 置換) -> NewString で返す。

static inline void append_u16(std::u16string &out, uint32_t cp) {
    if (cp <= 0xFFFF) {
        out.push_back(static_cast<char16_t>(cp));
    } else {
        cp -= 0x10000;
        out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
        out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
    }
}

static std::u16string utf8_to_utf16_lossy(const uint8_t *s, size_t n) {
    std::u16string out;
    out.reserve(n);

    size_t i = 0;
    while (i < n) {
        uint8_t b0 = s[i];

        // ASCII
        if (b0 <= 0x7F) {
            out.push_back(static_cast<char16_t>(b0));
            i += 1;
            continue;
        }

        int len = 0;
        uint32_t cp = 0;
        if ((b0 & 0xE0) == 0xC0) { len = 2; cp = b0 & 0x1F; }
        else if ((b0 & 0xF0) == 0xE0) { len = 3; cp = b0 & 0x0F; }
        else if ((b0 & 0xF8) == 0xF0) { len = 4; cp = b0 & 0x07; }
        else {
            out.push_back(u'\uFFFD');
            i += 1;
            continue;
        }

        if (i + static_cast<size_t>(len) > n) {
            out.push_back(u'\uFFFD');
            break;
        }

        bool ok = true;
        for (int k = 1; k < len; ++k) {
            uint8_t bx = s[i + k];
            if ((bx & 0xC0) != 0x80) { ok = false; break; }
            cp = (cp << 6) | (bx & 0x3F);
        }

        if (ok) {
            // overlong
            if (len == 2 && cp < 0x80) ok = false;
            if (len == 3 && cp < 0x800) ok = false;
            if (len == 4 && cp < 0x10000) ok = false;

            // surrogate / range
            if (cp >= 0xD800 && cp <= 0xDFFF) ok = false;
            if (cp > 0x10FFFF) ok = false;
        }

        if (!ok) {
            out.push_back(u'\uFFFD');
            i += 1; // resync
            continue;
        }

        append_u16(out, cp);
        i += static_cast<size_t>(len);
    }

    return out;
}

static jstring toJString(JNIEnv *env, const std::string &bytes) {
    const auto *p = reinterpret_cast<const uint8_t *>(bytes.data());
    std::u16string u16 = utf8_to_utf16_lossy(p, bytes.size());
    return env->NewString(reinterpret_cast<const jchar *>(u16.data()),
                          static_cast<jsize>(u16.size()));
}

static jstring toJString(JNIEnv *env, const char *cstr) {
    if (!cstr) return env->NewString(reinterpret_cast<const jchar *>(u""), 0);
    return toJString(env, std::string(cstr));
}

// ------- 共通ヘルパー -------

// Swift の preprocessText とほぼ同じ:
// - 半角スペース -> 全角スペース (\u3000)
// - 改行は削除
static std::string preprocess_text(const std::string &text) {
    std::string out;
    out.reserve(text.size());

    for (unsigned char c: text) {
        if (c == ' ') {
            out.append(u8"\u3000");
        } else if (c == '\n' || c == '\r') {
            continue;
        } else {
            out.push_back(static_cast<char>(c));
        }
    }
    return out;
}

// text を tokenize して llama_token の配列にする
static std::vector<llama_token> tokenize_text(const std::string &text, bool add_bos, bool add_eos) {
    std::vector<llama_token> tokens;

    if (!g_vocab) {
        return tokens;
    }

    const int32_t text_len = (int32_t) text.size();

    // 最初は適当に大きめ
    int32_t n_max = text_len + (add_bos ? 2 : 1);
    tokens.resize(n_max);

    int32_t n_tokens = llama_tokenize(
            g_vocab,
            text.c_str(),
            text_len,
            tokens.data(),
            n_max,
            add_bos,
            /*parse_special=*/false);

    if (n_tokens < 0) {
        n_max = -n_tokens;
        tokens.resize(n_max);
        n_tokens = llama_tokenize(
                g_vocab,
                text.c_str(),
                text_len,
                tokens.data(),
                n_max,
                add_bos,
                /*parse_special=*/false);
    }

    if (n_tokens <= 0) {
        tokens.clear();
        return tokens;
    }

    tokens.resize(n_tokens);

    if (add_eos) {
        tokens.push_back(llama_vocab_eos(g_vocab));
    }

    return tokens;
}

// 1トークン -> UTF-8 文字列（不正UTF-8が混ざり得る）
static std::string token_to_piece_str(llama_token token) {
    std::string out;
    if (!g_vocab) return out;

    int32_t buf_size = 8;
    std::vector<char> buf(buf_size);

    int32_t n = llama_token_to_piece(
            g_vocab,
            token,
            buf.data(),
            buf_size,
            /*lstrip=*/0,
            /*special=*/false);

    if (n < 0) {
        buf_size = -n;
        buf.resize(buf_size);
        n = llama_token_to_piece(
                g_vocab,
                token,
                buf.data(),
                buf_size,
                0,
                false);
    }

    if (n > 0) {
        out.assign(buf.data(), buf.data() + n);
    }
    return out;
}

static RuntimeConfig get_runtime_config() {
    std::lock_guard<std::mutex> lock(g_param_mutex);
    return RuntimeConfig{
            g_param_n_ctx,
            g_param_n_threads,
            g_param_n_threads_batch,
            g_param_n_batch
    };
}

static bool same_runtime_config(const RuntimeConfig &lhs, const RuntimeConfig &rhs) {
    return lhs.n_ctx == rhs.n_ctx &&
           lhs.n_threads == rhs.n_threads &&
           lhs.n_threads_batch == rhs.n_threads_batch &&
           lhs.n_batch == rhs.n_batch;
}

static bool is_request_stale(uint64_t request_seq) {
    return request_seq != g_request_seq.load(std::memory_order_relaxed);
}

struct AbortRequestState {
    uint64_t request_seq;
};

static bool abort_if_stale(void *data) {
    auto *state = static_cast<AbortRequestState *>(data);
    return state && is_request_stale(state->request_seq);
}

static bool never_abort(void * /*data*/) {
    return false;
}

static void destroy_session_context_locked() {
    if (!g_session.ctx) {
        return;
    }
    llama_set_abort_callback(g_session.ctx, never_abort, nullptr);
    llama_synchronize(g_session.ctx);
    llama_free(g_session.ctx);
    g_session.ctx = nullptr;
    g_session.config = RuntimeConfig{0, 0, 0, 0};
}

static llama_context *ensure_session_context_locked() {
    if (!g_model) {
        return nullptr;
    }

    const RuntimeConfig config = get_runtime_config();
    if (g_session.ctx && same_runtime_config(g_session.config, config)) {
        return g_session.ctx;
    }

    destroy_session_context_locked();

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = config.n_ctx;
    cparams.n_threads = config.n_threads;
    cparams.n_threads_batch = config.n_threads_batch;
    cparams.n_batch = config.n_batch;

    g_session.ctx = llama_init_from_model(g_model, cparams);
    if (!g_session.ctx) {
        LOGE("Failed to create llama_context");
        return nullptr;
    }

    g_session.config = config;
    LOGI("llama_context created: n_ctx=%d, n_threads=%d, n_batch=%d",
         cparams.n_ctx, cparams.n_threads, cparams.n_batch);
    return g_session.ctx;
}

// Swift の pure_greedy_decoding 相当
static std::string pure_greedy_decoding(
        const std::string &leftSideContext,
        int maxCount,
        uint64_t request_seq
) {
    std::unique_lock<std::mutex> session_lock(g_session.mutex);
    if (is_request_stale(request_seq)) {
        return "";
    }
    if (!g_model || !g_vocab) {
        return "[error] model not initialized";
    }

    llama_context *ctx = ensure_session_context_locked();
    if (!ctx) {
        return "[error] failed to create context";
    }
    llama_kv_cache_clear(ctx);

    AbortRequestState abort_state{request_seq};
    llama_set_abort_callback(ctx, abort_if_stale, &abort_state);

    std::string pre = preprocess_text(leftSideContext);
    auto prompt_tokens = tokenize_text(pre, /*add_bos=*/false, /*add_eos=*/false);
    if (prompt_tokens.empty()) {
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return "";
    }

    {
        llama_batch batch = llama_batch_get_one(
                prompt_tokens.data(),
                (int32_t) prompt_tokens.size()
        );
        int rc = llama_decode(ctx, batch);
        if (rc != 0) {
            LOGE("llama_decode(prompt) failed: %d", rc);
            if (is_request_stale(request_seq)) {
                LOGI("pure_greedy_decoding aborted while decoding prompt");
            }
            llama_set_abort_callback(ctx, never_abort, nullptr);
            return "";
        }
    }

    std::vector<llama_token> generated;
    generated.reserve(maxCount);

    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);

    for (int i = 0; i < maxCount; ++i) {
        float *logits = llama_get_logits_ith(ctx, -1);
        if (!logits) {
            LOGE("logits is null");
            break;
        }

        int best_id = 0;
        float best_logit = logits[0];
        for (int32_t tid = 1; tid < n_vocab; ++tid) {
            if (logits[tid] > best_logit) {
                best_logit = logits[tid];
                best_id = tid;
            }
        }

        llama_token next = (llama_token) best_id;
        if (next == eos) {
            break;
        }

        generated.push_back(next);

        llama_batch next_batch = llama_batch_get_one(&next, 1);
        int rc = llama_decode(ctx, next_batch);
        if (rc != 0) {
            if (is_request_stale(request_seq)) {
                LOGI("pure_greedy_decoding aborted during token generation");
                llama_set_abort_callback(ctx, never_abort, nullptr);
                return "";
            } else {
                LOGE("llama_decode(step) failed: %d", rc);
            }
            break;
        }
    }

    std::string out;
    for (auto t: generated) {
        if (llama_vocab_is_control(g_vocab, t)) {
            continue;
        }
        out += token_to_piece_str(t);
    }

    llama_set_abort_callback(ctx, never_abort, nullptr);
    return out;
}

static std::vector<std::string> utf8_codepoints(const std::string &text) {
    std::vector<std::string> out;
    size_t i = 0;
    while (i < text.size()) {
        unsigned char b0 = static_cast<unsigned char>(text[i]);
        size_t len = 1;
        if (b0 <= 0x7F) {
            len = 1;
        } else if ((b0 & 0xE0) == 0xC0) {
            len = 2;
        } else if ((b0 & 0xF0) == 0xE0) {
            len = 3;
        } else if ((b0 & 0xF8) == 0xF0) {
            len = 4;
        }
        if (i + len > text.size()) {
            out.emplace_back(text.substr(i));
            break;
        }
        out.emplace_back(text.substr(i, len));
        i += len;
    }
    return out;
}

static std::string utf8_suffix_chars(const std::string &text, size_t max_chars) {
    const auto cps = utf8_codepoints(text);
    if (cps.size() <= max_chars) {
        return text;
    }
    std::string out;
    for (size_t i = cps.size() - max_chars; i < cps.size(); ++i) {
        out += cps[i];
    }
    return out;
}

static std::string utf8_prefix_chars(const std::string &text, size_t max_chars) {
    const auto cps = utf8_codepoints(text);
    std::string out;
    const size_t limit = std::min(max_chars, cps.size());
    for (size_t i = 0; i < limit; ++i) {
        out += cps[i];
    }
    return out;
}

static std::string first_utf8_codepoint(const std::string &text) {
    const auto cps = utf8_codepoints(text);
    return cps.empty() ? "" : cps.front();
}

static std::string utf16_codepoint_to_utf8(char16_t c) {
    std::string out;
    if (c <= 0x7F) {
        out.push_back(static_cast<char>(c));
    } else if (c <= 0x7FF) {
        out.push_back(static_cast<char>(0xC0 | (c >> 6)));
        out.push_back(static_cast<char>(0x80 | (c & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xE0 | (c >> 12)));
        out.push_back(static_cast<char>(0x80 | ((c >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (c & 0x3F)));
    }
    return out;
}

/** Swift ZenzInputTextGenerator.toKatakana() 相当（prefix 判定用）。 */
static std::string normalize_katakana_utf8(const std::string &text) {
    const auto cps = utf8_codepoints(text);
    std::string out;
    for (const auto &cp : cps) {
        const auto *p = reinterpret_cast<const uint8_t *>(cp.data());
        std::u16string u16 = utf8_to_utf16_lossy(p, cp.size());
        if (u16.size() == 1) {
            char16_t c = u16[0];
            if (c >= 0x3041 && c <= 0x3096) {
                c = static_cast<char16_t>(c + 0x60);
            } else if (c == 0x309D) {
                c = 0x30FD;
            } else if (c == 0x309E) {
                c = 0x30FE;
            }
            out += utf16_codepoint_to_utf8(c);
        } else {
            out += cp;
        }
    }
    return out;
}

static bool is_input_prediction_stop_char(const std::string &ch) {
    static const char *stops[] = {
            u8"、", u8"。", u8"！", u8"？"
    };
    for (const char *stop : stops) {
        if (ch == stop) {
            return true;
        }
    }
    return false;
}

static std::string build_v3_input_prediction_prompt(
        const std::string &profile,
        const std::string &topic,
        const std::string &style,
        const std::string &preference,
        const std::string &left,
        const std::string &right,
        const std::string &input
) {
    return build_v3_prompt(
            utf8_suffix_chars(profile, 25),
            utf8_suffix_chars(topic, 25),
            utf8_suffix_chars(style, 25),
            utf8_suffix_chars(preference, 25),
            utf8_suffix_chars(left, 20),
            utf8_prefix_chars(right, 40),
            input,
            /*include_output_tag=*/false
    );
}

static std::vector<float> logits_to_log_probs(const float *logits, int32_t n_vocab) {
    std::vector<float> log_probs(static_cast<size_t>(n_vocab), -std::numeric_limits<float>::infinity());
    if (!logits || n_vocab <= 0) {
        return log_probs;
    }
    float max_logit = logits[0];
    for (int32_t i = 1; i < n_vocab; ++i) {
        if (logits[i] > max_logit) {
            max_logit = logits[i];
        }
    }
    float sum_exp = 0.0f;
    for (int32_t i = 0; i < n_vocab; ++i) {
        sum_exp += expf(logits[i] - max_logit);
    }
    if (sum_exp <= 0.0f) {
        return log_probs;
    }
    const float log_sum_exp = max_logit + logf(sum_exp);
    for (int32_t i = 0; i < n_vocab; ++i) {
        log_probs[static_cast<size_t>(i)] = logits[i] - log_sum_exp;
    }
    return log_probs;
}

static bool decode_all_tokens_locked(
        llama_context *ctx,
        const std::vector<llama_token> &tokens,
        bool logits_on_last
) {
    if (tokens.empty()) {
        return false;
    }
    const int32_t cap = static_cast<int32_t>(tokens.size());
    llama_batch batch = llama_batch_init(cap, 0, 1);
    for (size_t i = 0; i < tokens.size(); ++i) {
        batch.token[batch.n_tokens] = tokens[i];
        batch.pos[batch.n_tokens] = static_cast<llama_pos>(i);
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        const bool want_logits = logits_on_last && (i + 1 == tokens.size());
        batch.logits[batch.n_tokens] = want_logits ? 1 : 0;
        batch.n_tokens++;
    }
    const int rc = llama_decode(ctx, batch);
    llama_batch_free(batch);
    return rc == 0;
}

static std::vector<float> next_log_probs_locked(llama_context *ctx) {
    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    float *logits = llama_get_logits_ith(ctx, -1);
    return logits_to_log_probs(logits, n_vocab);
}

static std::string input_prediction_greedy_decoding(
        const std::string &prompt,
        int max_count,
        int min_length,
        float max_entropy,
        const std::vector<std::string> &possible_nexts,
        uint64_t request_seq
) {
    if (max_count <= 0) {
        return "";
    }
    min_length = std::max(1, std::min(min_length, max_count));
    const bool use_entropy = std::isfinite(max_entropy) && max_entropy >= 0.0f;

    std::unique_lock<std::mutex> session_lock(g_session.mutex);
    if (is_request_stale(request_seq) || !g_model || !g_vocab) {
        return "";
    }
    llama_context *ctx = ensure_session_context_locked();
    if (!ctx) {
        return "";
    }
    llama_kv_cache_clear(ctx);

    AbortRequestState abort_state{request_seq};
    llama_set_abort_callback(ctx, abort_if_stale, &abort_state);

    const std::string pre = preprocess_text(prompt);
    auto prompt_tokens = tokenize_text(pre, /*add_bos=*/true, /*add_eos=*/false);
    if (prompt_tokens.empty()) {
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return "";
    }

    std::vector<std::string> predicted_chars;
    predicted_chars.reserve(static_cast<size_t>(max_count));
    std::string predicted_text;

    auto is_allowed_prefix = [&](const std::string &candidate) -> bool {
        if (possible_nexts.empty()) {
            return true;
        }
        const std::string normalized = normalize_katakana_utf8(candidate);
        for (const auto &allowed : possible_nexts) {
            if (allowed.empty()) continue;
            const std::string normalized_allowed = normalize_katakana_utf8(allowed);
            if (normalized_allowed.rfind(normalized, 0) == 0) {
                return true;
            }
        }
        return false;
    };

    for (int step = 0; step < max_count; ++step) {
        if (!decode_all_tokens_locked(ctx, prompt_tokens, /*logits_on_last=*/true)) {
            break;
        }
        float *logits = llama_get_logits_ith(ctx, -1);
        if (!logits) {
            break;
        }
        const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);

        std::unordered_map<llama_token, float> token_penalty;
        for (size_t index = 0; index < prompt_tokens.size(); ++index) {
            const float weight = 2.0f / static_cast<float>(prompt_tokens.size() - index);
            token_penalty[prompt_tokens[index]] += weight;
        }

        float sumexp = 0.0f;
        float sumexp_x = 0.0f;
        float best_value = -std::numeric_limits<float>::infinity();
        std::string best_char;
        std::string best_next_text;

        for (int32_t tid = 0; tid < n_vocab; ++tid) {
            const float repeat_penalty = 1.0f + token_penalty[static_cast<llama_token>(tid)];
            const float value = logits[tid] / repeat_penalty;
            const float exp_value = expf(value);
            sumexp += exp_value;
            sumexp_x += exp_value * value;
            if (value <= best_value) {
                continue;
            }
            const std::string piece = token_to_piece_str(static_cast<llama_token>(tid));
            const std::string ch = first_utf8_codepoint(piece);
            if (ch.empty()) {
                continue;
            }
            const std::string next_text = predicted_text + ch;
            if (!is_allowed_prefix(next_text)) {
                continue;
            }
            best_value = value;
            best_char = ch;
            best_next_text = next_text;
        }

        if (use_entropy &&
            static_cast<int>(predicted_chars.size()) >= min_length &&
            sumexp > 0.0f) {
            const float entropy = logf(sumexp) - (sumexp_x / sumexp);
            if (entropy >= max_entropy) {
                break;
            }
        }

        if (best_char.empty()) {
            break;
        }
        if (static_cast<int>(predicted_chars.size()) >= min_length &&
            is_input_prediction_stop_char(best_char)) {
            break;
        }
        if (!is_allowed_prefix(best_next_text)) {
            break;
        }

        predicted_chars.push_back(best_char);
        predicted_text = best_next_text;
        const auto appended = tokenize_text(best_char, /*add_bos=*/false, /*add_eos=*/false);
        if (appended.empty()) {
            break;
        }
        prompt_tokens.insert(prompt_tokens.end(), appended.begin(), appended.end());
    }

    llama_set_abort_callback(ctx, never_abort, nullptr);
    std::string out;
    for (const auto &ch : predicted_chars) {
        out += ch;
    }
    return out;
}

static std::vector<float> typo_next_log_probs_internal(
        const std::string &prompt_prefix,
        const std::vector<int32_t> &emitted_token_ids,
        uint64_t request_seq
) {
    std::unique_lock<std::mutex> session_lock(g_session.mutex);
    if (is_request_stale(request_seq) || !g_model || !g_vocab) {
        return {};
    }
    llama_context *ctx = ensure_session_context_locked();
    if (!ctx) {
        return {};
    }
    llama_kv_cache_clear(ctx);

    AbortRequestState abort_state{request_seq};
    llama_set_abort_callback(ctx, abort_if_stale, &abort_state);

    auto prompt_tokens = tokenize_text(preprocess_text(prompt_prefix), /*add_bos=*/false, /*add_eos=*/false);
    std::vector<llama_token> all_tokens = prompt_tokens;
    all_tokens.reserve(prompt_tokens.size() + emitted_token_ids.size());
    for (int32_t token_id : emitted_token_ids) {
        all_tokens.push_back(static_cast<llama_token>(token_id));
    }
    if (all_tokens.empty()) {
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return {};
    }
    if (!decode_all_tokens_locked(ctx, all_tokens, /*logits_on_last=*/true)) {
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return {};
    }
    auto log_probs = next_log_probs_locked(ctx);
    llama_set_abort_callback(ctx, never_abort, nullptr);
    return log_probs;
}

static bool is_japanese_conversion_codepoint(char32_t code) {
    if (code >= 0x3041 && code <= 0x3096) return true; // hiragana
    if (code >= 0x30A1 && code <= 0x30F6) return true; // katakana
    if (code >= 0xFF66 && code <= 0xFF9F) return true; // half-width kana
    if (code >= 0x4E00 && code <= 0x9FFF) return true; // CJK unified
    if (code >= 0x3400 && code <= 0x4DBF) return true; // CJK ext A
    if (code >= 0xAC00 && code <= 0xD7A3) return false; // hangul syllables
    if (code >= 0x1100 && code <= 0x11FF) return false; // hangul jamo
    if (code >= 0x3130 && code <= 0x318F) return false; // hangul compat jamo
    if ((code >= 'a' && code <= 'z') || (code >= 'A' && code <= 'Z')) return true;
    if ((code >= '0' && code <= '9')) return true;
    if (code == 0x30FC || code == 0x301C) return true; // prolonged sound marks
    return code <= 0x7F; // ASCII symbols / space
}

static bool is_valid_japanese_conversion_text(const std::string &text) {
    if (text.empty()) return false;
    size_t index = 0;
    while (index < text.size()) {
        unsigned char lead = static_cast<unsigned char>(text[index]);
        char32_t code = 0;
        if ((lead & 0x80) == 0) {
            code = lead;
            index += 1;
        } else if ((lead & 0xE0) == 0xC0 && index + 1 < text.size()) {
            code = ((lead & 0x1F) << 6) |
                   (static_cast<unsigned char>(text[index + 1]) & 0x3F);
            index += 2;
        } else if ((lead & 0xF0) == 0xE0 && index + 2 < text.size()) {
            code = ((lead & 0x0F) << 12) |
                   ((static_cast<unsigned char>(text[index + 1]) & 0x3F) << 6) |
                   (static_cast<unsigned char>(text[index + 2]) & 0x3F);
            index += 3;
        } else if ((lead & 0xF8) == 0xF0 && index + 3 < text.size()) {
            code = ((lead & 0x07) << 18) |
                   ((static_cast<unsigned char>(text[index + 1]) & 0x3F) << 12) |
                   ((static_cast<unsigned char>(text[index + 2]) & 0x3F) << 6) |
                   (static_cast<unsigned char>(text[index + 3]) & 0x3F);
            index += 4;
        } else {
            return false;
        }
        if (!is_japanese_conversion_codepoint(code)) {
            return false;
        }
    }
    return true;
}

// Swift の evaluate_candidate 相当
static CandidateEvaluationResult candidate_evaluate(
        const std::string &prompt,
        const std::string &candidate_text,
        uint64_t request_seq,
        bool request_rich = false
) {
    CandidateEvaluationResult result;
    result.type = CandidateEvaluationResultType::ERROR;
    result.score = 0.0f;

    std::unique_lock<std::mutex> session_lock(g_session.mutex);
    if (is_request_stale(request_seq)) {
        return result;
    }
    if (!g_model || !g_vocab) {
        LOGE("candidate_evaluate: model not initialized");
        return result;
    }

    llama_context *ctx = ensure_session_context_locked();
    if (!ctx) {
        LOGE("candidate_evaluate: failed to create context");
        return result;
    }
    llama_kv_cache_clear(ctx);

    AbortRequestState abort_state{request_seq};
    llama_set_abort_callback(ctx, abort_if_stale, &abort_state);

    std::string pre_prompt = preprocess_text(prompt);
    std::string pre_candidate = preprocess_text(candidate_text);

    auto prompt_tokens = tokenize_text(pre_prompt, /*add_bos=*/false, /*add_eos=*/false);
    auto candidate_tokens = tokenize_text(pre_candidate, /*add_bos=*/false, /*add_eos=*/false);

    if (prompt_tokens.empty()) {
        LOGE("candidate_evaluate: prompt tokens empty");
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return result;
    }

    std::vector<llama_token> all_tokens = prompt_tokens;
    all_tokens.insert(all_tokens.end(), candidate_tokens.begin(), candidate_tokens.end());

    // ★ 512固定だと長文で overflow するので必要量で確保
    const int32_t cap = (int32_t) all_tokens.size();
    llama_batch batch = llama_batch_init(cap, 0, 1);

    // プロンプト部分: logits不要（最後のトークンを除く）
    for (size_t i = 0; i + 1 < prompt_tokens.size(); ++i) {
        batch.token[batch.n_tokens] = prompt_tokens[i];
        batch.pos[batch.n_tokens] = (llama_pos) i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = 0;
        batch.n_tokens++;
    }

    // プロンプトの最後のトークンから候補の最後のトークンまで: logits必要
    size_t logits_start_pos = prompt_tokens.size() - 1;
    for (size_t i = logits_start_pos; i < all_tokens.size(); ++i) {
        batch.token[batch.n_tokens] = all_tokens[i];
        batch.pos[batch.n_tokens] = (llama_pos) i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = 1;
        batch.n_tokens++;
    }

    int rc = llama_decode(ctx, batch);
    if (rc != 0) {
        if (is_request_stale(request_seq)) {
            LOGI("candidate_evaluate aborted");
        } else {
            LOGE("candidate_evaluate: llama_decode failed: %d", rc);
        }
        llama_batch_free(batch);
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return result;
    }

    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);

    float *all_logits = llama_get_logits(ctx);
    if (!all_logits) {
        LOGE("candidate_evaluate: all_logits is null");
        llama_batch_free(batch);
        llama_set_abort_callback(ctx, never_abort, nullptr);
        return result;
    }

    float total_score = 0.0f;

    for (size_t i = prompt_tokens.size(); i < all_tokens.size(); ++i) {
        llama_token expected_token = all_tokens[i];

        size_t logits_offset = (i - 1 - logits_start_pos) * (size_t) n_vocab;
        float *logits = all_logits + logits_offset;

        int32_t max_id = 0;
        float max_logit = logits[0];
        for (int32_t tid = 1; tid < n_vocab; ++tid) {
            if (logits[tid] > max_logit) {
                max_logit = logits[tid];
                max_id = tid;
            }
        }

        llama_token max_token = (llama_token) max_id;

        float sum_exp = 0.0f;
        for (int32_t tid = 0; tid < n_vocab; ++tid) {
            sum_exp += expf(logits[tid] - max_logit);
        }
        float log_prob = logits[expected_token] - max_logit - logf(sum_exp);
        total_score += log_prob;

        if (request_rich && max_token == expected_token) {
            struct TokenScore {
                int32_t tid;
                float logit;
            };
            std::array<TokenScore, 3> top{};
            for (int32_t tid = 0; tid < n_vocab; ++tid) {
                const float value = logits[tid];
                for (auto &slot : top) {
                    if (slot.tid == 0 || value > slot.logit) {
                        TokenScore incoming{tid, value};
                        std::swap(incoming, slot);
                        if (incoming.tid != 0) {
                            for (auto &next : top) {
                                if (&next != &slot && next.tid != 0 && next.logit < incoming.logit) {
                                    std::swap(incoming, next);
                                }
                            }
                        }
                        break;
                    }
                }
            }
            std::string accepted_prefix;
            for (size_t j = prompt_tokens.size(); j < i; ++j) {
                llama_token t = all_tokens[j];
                if (!llama_vocab_is_control(g_vocab, t)) {
                    accepted_prefix += token_to_piece_str(t);
                }
            }
            for (const auto &slot : top) {
                if (slot.tid <= 0 || slot.tid == expected_token) continue;
                const float ratio = expf(logits[slot.tid] - max_logit) / sum_exp;
                if (ratio < 0.1f) continue;
                std::string alt_prefix = accepted_prefix;
                if (!llama_vocab_is_control(g_vocab, (llama_token) slot.tid)) {
                    alt_prefix += token_to_piece_str((llama_token) slot.tid);
                }
                if (!alt_prefix.empty() && is_valid_japanese_conversion_text(alt_prefix)) {
                    result.alternatives.emplace_back(ratio, alt_prefix);
                }
            }
        }

        if (max_token != expected_token) {
            if (max_token == eos) {
                std::string partial;
                for (size_t j = prompt_tokens.size(); j < i; ++j) {
                    llama_token t = all_tokens[j];
                    if (!llama_vocab_is_control(g_vocab, t)) {
                        partial += token_to_piece_str(t);
                    }
                }
                result.type = CandidateEvaluationResultType::WHOLE_RESULT;
                result.whole_result = partial;
                LOGI("candidate_evaluate: WHOLE_RESULT at pos %zu, result=%s", i, partial.c_str());
                llama_batch_free(batch);
                llama_set_abort_callback(ctx, never_abort, nullptr);
                return result;
            } else {
                std::string prefix;
                for (size_t j = prompt_tokens.size(); j < i; ++j) {
                    llama_token t = all_tokens[j];
                    if (!llama_vocab_is_control(g_vocab, t)) {
                        prefix += token_to_piece_str(t);
                    }
                }
                if (!llama_vocab_is_control(g_vocab, max_token)) {
                    prefix += token_to_piece_str(max_token);
                }
                result.type = CandidateEvaluationResultType::FIX_REQUIRED;
                result.prefix = prefix;
                LOGI("candidate_evaluate: FIX_REQUIRED at pos %zu, prefix=%s", i, prefix.c_str());
                llama_batch_free(batch);
                llama_set_abort_callback(ctx, never_abort, nullptr);
                return result;
            }
        }
    }

    result.type = CandidateEvaluationResultType::PASS;
    result.score = total_score;
    LOGI("candidate_evaluate: PASS, score=%f", total_score);

    llama_batch_free(batch);
    llama_set_abort_callback(ctx, never_abort, nullptr);
    return result;
}

static bool prefill_prompt_prefix_locked(
        llama_context *ctx,
        const std::vector<llama_token> &prompt_tokens
) {
    llama_kv_cache_clear(ctx);

    if (prompt_tokens.size() <= 1) {
        return true;
    }

    const int32_t cap = (int32_t) (prompt_tokens.size() - 1);
    llama_batch batch = llama_batch_init(cap, 0, 1);
    for (size_t i = 0; i + 1 < prompt_tokens.size(); ++i) {
        batch.token[batch.n_tokens] = prompt_tokens[i];
        batch.pos[batch.n_tokens] = (llama_pos) i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = 0;
        batch.n_tokens++;
    }

    const int rc = llama_decode(ctx, batch);
    llama_batch_free(batch);
    return rc == 0;
}

static float score_candidate_avg_logprob_reuse_prompt_locked(
        llama_context *ctx,
        const std::vector<llama_token> &prompt_tokens,
        const std::vector<llama_token> &candidate_tokens,
        uint64_t request_seq
) {
    if (is_request_stale(request_seq)) {
        return -INFINITY;
    }
    if (prompt_tokens.empty() || candidate_tokens.empty()) {
        return -INFINITY;
    }

    const llama_pos suffix_start = (llama_pos) (prompt_tokens.size() - 1);
    llama_kv_cache_seq_rm(ctx, 0, suffix_start, -1);

    const int32_t cap = (int32_t) (1 + candidate_tokens.size());
    llama_batch batch = llama_batch_init(cap, 0, 1);

    batch.token[batch.n_tokens] = prompt_tokens.back();
    batch.pos[batch.n_tokens] = suffix_start;
    batch.n_seq_id[batch.n_tokens] = 1;
    batch.seq_id[batch.n_tokens][0] = 0;
    batch.logits[batch.n_tokens] = 1;
    batch.n_tokens++;

    for (size_t i = 0; i < candidate_tokens.size(); ++i) {
        batch.token[batch.n_tokens] = candidate_tokens[i];
        batch.pos[batch.n_tokens] = suffix_start + 1 + (llama_pos) i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = 1;
        batch.n_tokens++;
    }

    const int rc = llama_decode(ctx, batch);
    if (rc != 0) {
        if (!is_request_stale(request_seq)) {
            LOGE("score_candidate_avg_logprob_reuse_prompt_locked: llama_decode failed: %d", rc);
        }
        llama_batch_free(batch);
        return -INFINITY;
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    float *all_logits = llama_get_logits(ctx);
    if (!all_logits) {
        LOGE("score_candidate_avg_logprob_reuse_prompt_locked: all_logits is null");
        llama_batch_free(batch);
        return -INFINITY;
    }

    float total_score = 0.0f;
    for (size_t i = 0; i < candidate_tokens.size(); ++i) {
        llama_token expected_token = candidate_tokens[i];
        float *logits = all_logits + ((size_t) i * (size_t) n_vocab);

        float max_logit = logits[0];
        for (int32_t tid = 1; tid < n_vocab; ++tid) {
            if (logits[tid] > max_logit) {
                max_logit = logits[tid];
            }
        }

        double sum_exp = 0.0;
        for (int32_t tid = 0; tid < n_vocab; ++tid) {
            sum_exp += exp((double) logits[tid] - (double) max_logit);
        }
        total_score += logits[expected_token] - max_logit - (float) log(sum_exp);
    }

    llama_batch_free(batch);
    return total_score / (float) candidate_tokens.size();
}

// ------- JNI: モデル初期化 -------
// package com.kazumaproject.zenz; class ZenzEngine

extern "C"
JNIEXPORT void JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_initModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jModelPath
) {
    const char *c_model_path = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("initModel: %s", c_model_path);

    std::lock_guard<std::mutex> lock(g_session.mutex);
    destroy_session_context_locked();

    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
    }

    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;

    g_model = llama_model_load_from_file(c_model_path, mparams);
    if (!g_model) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(jModelPath, c_model_path);
        return;
    }

    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        LOGE("Failed to get vocab");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(jModelPath, c_model_path);
        return;
    }

    env->ReleaseStringUTFChars(jModelPath, c_model_path);
}

// ------- JNI: ランタイム設定 (n_ctx / n_threads) -------

extern "C"
JNIEXPORT void JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_setRuntimeConfig(
        JNIEnv * /*env*/,
        jobject /*thiz*/,
        jint jNCtx,
        jint jNThreads
) {
    int n_ctx = jNCtx > 0 ? jNCtx : 512;
    int n_threads = jNThreads > 0 ? jNThreads : 4;

    if (n_ctx < 128) n_ctx = 128;
    if (n_ctx > 4096) n_ctx = 4096;

    if (n_threads < 1) n_threads = 1;
    if (n_threads > 8) n_threads = 8;

    RuntimeConfig new_config{
            n_ctx,
            n_threads,
            n_threads,
            n_ctx
    };

    {
        std::lock_guard<std::mutex> lock(g_param_mutex);
        g_param_n_ctx = new_config.n_ctx;
        g_param_n_threads = new_config.n_threads;
        g_param_n_threads_batch = new_config.n_threads_batch;
        g_param_n_batch = new_config.n_batch;
    }

    {
        std::lock_guard<std::mutex> session_lock(g_session.mutex);
        if (g_session.ctx && !same_runtime_config(g_session.config, new_config)) {
            destroy_session_context_locked();
        }
    }

    LOGI("setRuntimeConfig: n_ctx=%d, n_threads=%d", n_ctx, n_threads);
}

// ------- JNI: 「後半の変換結果」を返す（v1 型） -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_generate(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jPrompt,
        jint maxTokens
) {
    const char *c_prompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt(c_prompt ? c_prompt : "");
    env->ReleaseStringUTFChars(jPrompt, c_prompt);

    uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;
    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/maxTokens, request_seq);

    // ★ NewStringUTFは禁止（不正UTF-8の可能性）
    return toJString(env, result);
}

// ------- JNI: 文脈 + 読み で変換 -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_generateWithContext(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jLeftContext,
        jstring jInput,
        jint maxTokens
) {
    const char *c_left = env->GetStringUTFChars(jLeftContext, nullptr);
    const char *c_input = env->GetStringUTFChars(jInput, nullptr);

    std::string left = c_left ? c_left : "";
    std::string input = c_input ? c_input : "";

    env->ReleaseStringUTFChars(jLeftContext, c_left);
    env->ReleaseStringUTFChars(jInput, c_input);

    const std::string inputTag = u8"\uEE00";
    const std::string contextTag = u8"\uEE02";
    const std::string outputTag = u8"\uEE01";

    std::string prompt;
    if (!left.empty()) {
        prompt = contextTag + left + inputTag + input + outputTag;
    } else {
        prompt = inputTag + input + outputTag;
    }

    uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;
    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/maxTokens, request_seq);
    return toJString(env, result);
}

// ------- JNI: 条件 + 文脈 + 読み で変換 -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_generateWithContextAndConditions(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jint maxTokens
) {
    const char *c_profile = jProfile ? env->GetStringUTFChars(jProfile, nullptr) : nullptr;
    const char *c_topic = jTopic ? env->GetStringUTFChars(jTopic, nullptr) : nullptr;
    const char *c_style = jStyle ? env->GetStringUTFChars(jStyle, nullptr) : nullptr;
    const char *c_preference = jPreference ? env->GetStringUTFChars(jPreference, nullptr) : nullptr;
    const char *c_left = jLeftContext ? env->GetStringUTFChars(jLeftContext, nullptr) : nullptr;
    const char *c_right = jRightContext ? env->GetStringUTFChars(jRightContext, nullptr) : nullptr;
    const char *c_input = jInput ? env->GetStringUTFChars(jInput, nullptr) : nullptr;

    std::string profile = c_profile ? c_profile : "";
    std::string topic = c_topic ? c_topic : "";
    std::string style = c_style ? c_style : "";
    std::string preference = c_preference ? c_preference : "";
    std::string left = c_left ? c_left : "";
    std::string right = c_right ? c_right : "";
    std::string input = c_input ? c_input : "";

    if (c_profile) env->ReleaseStringUTFChars(jProfile, c_profile);
    if (c_topic) env->ReleaseStringUTFChars(jTopic, c_topic);
    if (c_style) env->ReleaseStringUTFChars(jStyle, c_style);
    if (c_preference) env->ReleaseStringUTFChars(jPreference, c_preference);
    if (c_left) env->ReleaseStringUTFChars(jLeftContext, c_left);
    if (c_right) env->ReleaseStringUTFChars(jRightContext, c_right);
    if (c_input) env->ReleaseStringUTFChars(jInput, c_input);

    std::string prompt = build_v3_prompt(profile, topic, style, preference, left, right, input, /*include_output_tag=*/true);

    uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;
    std::string result = pure_greedy_decoding(prompt, /*maxCount=*/maxTokens, request_seq);
    return toJString(env, result);
}

// ------- JNI: 投機的デコーディングによる候補評価 -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_candidateEvaluate(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jstring jCandidate,
        jboolean jRequestRich
) {
    const char *c_profile = jProfile ? env->GetStringUTFChars(jProfile, nullptr) : nullptr;
    const char *c_topic = jTopic ? env->GetStringUTFChars(jTopic, nullptr) : nullptr;
    const char *c_style = jStyle ? env->GetStringUTFChars(jStyle, nullptr) : nullptr;
    const char *c_preference = jPreference ? env->GetStringUTFChars(jPreference, nullptr) : nullptr;
    const char *c_left = jLeftContext ? env->GetStringUTFChars(jLeftContext, nullptr) : nullptr;
    const char *c_right = jRightContext ? env->GetStringUTFChars(jRightContext, nullptr) : nullptr;
    const char *c_input = jInput ? env->GetStringUTFChars(jInput, nullptr) : nullptr;
    const char *c_candidate = jCandidate ? env->GetStringUTFChars(jCandidate, nullptr) : nullptr;

    std::string profile = c_profile ? c_profile : "";
    std::string topic = c_topic ? c_topic : "";
    std::string style = c_style ? c_style : "";
    std::string preference = c_preference ? c_preference : "";
    std::string left = c_left ? c_left : "";
    std::string right = c_right ? c_right : "";
    std::string input = c_input ? c_input : "";
    std::string candidate = c_candidate ? c_candidate : "";

    if (c_profile) env->ReleaseStringUTFChars(jProfile, c_profile);
    if (c_topic) env->ReleaseStringUTFChars(jTopic, c_topic);
    if (c_style) env->ReleaseStringUTFChars(jStyle, c_style);
    if (c_preference) env->ReleaseStringUTFChars(jPreference, c_preference);
    if (c_left) env->ReleaseStringUTFChars(jLeftContext, c_left);
    if (c_right) env->ReleaseStringUTFChars(jRightContext, c_right);
    if (c_input) env->ReleaseStringUTFChars(jInput, c_input);
    if (c_candidate) env->ReleaseStringUTFChars(jCandidate, c_candidate);

    if (candidate.empty()) {
        return toJString(env, "ERROR");
    }

    std::string prompt = build_v3_prompt(profile, topic, style, preference, left, right, input, /*include_output_tag=*/true);

    uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;
    CandidateEvaluationResult eval_result = candidate_evaluate(
            prompt,
            candidate,
            request_seq,
            jRequestRich == JNI_TRUE
    );

    std::string result_str;
    switch (eval_result.type) {
        case CandidateEvaluationResultType::PASS:
            result_str = "PASS:" + std::to_string(eval_result.score);
            for (const auto &alt : eval_result.alternatives) {
                result_str += "|ALT:" + std::to_string(alt.first) + ":" + alt.second;
            }
            break;
        case CandidateEvaluationResultType::FIX_REQUIRED:
            result_str = "FIX:" + eval_result.prefix;          // ここも不正UTF-8が混ざり得るので toJString 必須
            break;
        case CandidateEvaluationResultType::WHOLE_RESULT:
            result_str = "WHOLE:" + eval_result.whole_result;  // 同上
            break;
        case CandidateEvaluationResultType::ERROR:
        default:
            result_str = "ERROR";
            break;
    }

    return toJString(env, result_str);
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_scoreCandidates(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jobjectArray jCandidates
) {
    const jsize candidate_count = jCandidates ? env->GetArrayLength(jCandidates) : 0;
    jfloatArray result_array = env->NewFloatArray(candidate_count);
    if (!result_array) {
        return nullptr;
    }

    std::vector<jfloat> scores((size_t) candidate_count, -INFINITY);
    if (candidate_count <= 0) {
        return result_array;
    }

    const char *c_profile = jProfile ? env->GetStringUTFChars(jProfile, nullptr) : nullptr;
    const char *c_topic = jTopic ? env->GetStringUTFChars(jTopic, nullptr) : nullptr;
    const char *c_style = jStyle ? env->GetStringUTFChars(jStyle, nullptr) : nullptr;
    const char *c_preference = jPreference ? env->GetStringUTFChars(jPreference, nullptr) : nullptr;
    const char *c_left = jLeftContext ? env->GetStringUTFChars(jLeftContext, nullptr) : nullptr;
    const char *c_right = jRightContext ? env->GetStringUTFChars(jRightContext, nullptr) : nullptr;
    const char *c_input = jInput ? env->GetStringUTFChars(jInput, nullptr) : nullptr;

    std::string profile = c_profile ? c_profile : "";
    std::string topic = c_topic ? c_topic : "";
    std::string style = c_style ? c_style : "";
    std::string preference = c_preference ? c_preference : "";
    std::string left = c_left ? c_left : "";
    std::string right = c_right ? c_right : "";
    std::string input = c_input ? c_input : "";

    if (c_profile) env->ReleaseStringUTFChars(jProfile, c_profile);
    if (c_topic) env->ReleaseStringUTFChars(jTopic, c_topic);
    if (c_style) env->ReleaseStringUTFChars(jStyle, c_style);
    if (c_preference) env->ReleaseStringUTFChars(jPreference, c_preference);
    if (c_left) env->ReleaseStringUTFChars(jLeftContext, c_left);
    if (c_right) env->ReleaseStringUTFChars(jRightContext, c_right);
    if (c_input) env->ReleaseStringUTFChars(jInput, c_input);

    std::string prompt = build_v3_prompt(profile, topic, style, preference, left, right, input, /*include_output_tag=*/true);

    const std::string pre_prompt = preprocess_text(prompt);
    uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;

    std::vector<std::string> candidate_strings((size_t) candidate_count);
    for (jsize i = 0; i < candidate_count; ++i) {
        auto *j_candidate = (jstring) env->GetObjectArrayElement(jCandidates, i);
        if (!j_candidate) {
            continue;
        }
        const char *c_candidate = env->GetStringUTFChars(j_candidate, nullptr);
        candidate_strings[(size_t) i] = c_candidate ? c_candidate : "";
        if (c_candidate) {
            env->ReleaseStringUTFChars(j_candidate, c_candidate);
        }
        env->DeleteLocalRef(j_candidate);
    }

    {
        std::unique_lock<std::mutex> session_lock(g_session.mutex);
        if (!g_model || !g_vocab) {
            LOGE("scoreCandidates: model not initialized");
            env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
            return result_array;
        }

        llama_context *ctx = ensure_session_context_locked();
        if (!ctx) {
            LOGE("scoreCandidates: failed to create context");
            env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
            return result_array;
        }

        AbortRequestState abort_state{request_seq};
        llama_set_abort_callback(ctx, abort_if_stale, &abort_state);

        auto prompt_tokens = tokenize_text(pre_prompt, /*add_bos=*/false, /*add_eos=*/false);
        if (prompt_tokens.empty()) {
            llama_set_abort_callback(ctx, never_abort, nullptr);
            env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
            return result_array;
        }

        if (!prefill_prompt_prefix_locked(ctx, prompt_tokens)) {
            LOGE("scoreCandidates: failed to prefill prompt prefix");
            llama_set_abort_callback(ctx, never_abort, nullptr);
            env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
            return result_array;
        }

        std::vector<std::vector<llama_token>> candidate_tokens_list((size_t) candidate_count);
        for (jsize i = 0; i < candidate_count; ++i) {
            if (candidate_strings[(size_t) i].empty()) {
                continue;
            }
            candidate_tokens_list[(size_t) i] = tokenize_text(
                    preprocess_text(candidate_strings[(size_t) i]),
                    /*add_bos=*/false,
                    /*add_eos=*/false
            );
        }

        for (jsize i = 0; i < candidate_count; ++i) {
            if (is_request_stale(request_seq)) {
                break;
            }
            if (candidate_tokens_list[(size_t) i].empty()) {
                scores[(size_t) i] = -INFINITY;
                continue;
            }

            scores[(size_t) i] = score_candidate_avg_logprob_reuse_prompt_locked(
                    ctx,
                    prompt_tokens,
                    candidate_tokens_list[(size_t) i],
                    request_seq
            );
        }
        llama_set_abort_callback(ctx, never_abort, nullptr);
    }

    env->SetFloatArrayRegion(result_array, 0, candidate_count, scores.data());
    return result_array;
}

// ------- JNI: v3 input prediction (ZenzInputTextGenerator) -------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_predictNextInputText(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jProfile,
        jstring jTopic,
        jstring jStyle,
        jstring jPreference,
        jstring jLeftContext,
        jstring jRightContext,
        jstring jInput,
        jint count,
        jint minLength,
        jfloat maxEntropy,
        jobjectArray jPossibleNexts
) {
    const char *c_profile = jProfile ? env->GetStringUTFChars(jProfile, nullptr) : nullptr;
    const char *c_topic = jTopic ? env->GetStringUTFChars(jTopic, nullptr) : nullptr;
    const char *c_style = jStyle ? env->GetStringUTFChars(jStyle, nullptr) : nullptr;
    const char *c_preference = jPreference ? env->GetStringUTFChars(jPreference, nullptr) : nullptr;
    const char *c_left = jLeftContext ? env->GetStringUTFChars(jLeftContext, nullptr) : nullptr;
    const char *c_right = jRightContext ? env->GetStringUTFChars(jRightContext, nullptr) : nullptr;
    const char *c_input = jInput ? env->GetStringUTFChars(jInput, nullptr) : nullptr;

    std::string profile = c_profile ? c_profile : "";
    std::string topic = c_topic ? c_topic : "";
    std::string style = c_style ? c_style : "";
    std::string preference = c_preference ? c_preference : "";
    std::string left = c_left ? c_left : "";
    std::string right = c_right ? c_right : "";
    std::string input = c_input ? c_input : "";

    if (c_profile) env->ReleaseStringUTFChars(jProfile, c_profile);
    if (c_topic) env->ReleaseStringUTFChars(jTopic, c_topic);
    if (c_style) env->ReleaseStringUTFChars(jStyle, c_style);
    if (c_preference) env->ReleaseStringUTFChars(jPreference, c_preference);
    if (c_left) env->ReleaseStringUTFChars(jLeftContext, c_left);
    if (c_right) env->ReleaseStringUTFChars(jRightContext, c_right);
    if (c_input) env->ReleaseStringUTFChars(jInput, c_input);

    std::vector<std::string> possible_nexts;
    if (jPossibleNexts) {
        const jsize n = env->GetArrayLength(jPossibleNexts);
        possible_nexts.reserve(static_cast<size_t>(n));
        for (jsize i = 0; i < n; ++i) {
            auto *entry = (jstring) env->GetObjectArrayElement(jPossibleNexts, i);
            if (!entry) continue;
            const char *c_entry = env->GetStringUTFChars(entry, nullptr);
            if (c_entry) {
                possible_nexts.emplace_back(c_entry);
                env->ReleaseStringUTFChars(entry, c_entry);
            }
            env->DeleteLocalRef(entry);
        }
    }

    const std::string prompt = build_v3_input_prediction_prompt(
            profile, topic, style, preference, left, right, input
    );
    const uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;
    const float entropy_limit = maxEntropy < 0.0f ? std::numeric_limits<float>::quiet_NaN() : maxEntropy;
    const std::string result = input_prediction_greedy_decoding(
            prompt,
            static_cast<int>(count),
            static_cast<int>(minLength),
            entropy_limit,
            possible_nexts,
            request_seq
    );
    return toJString(env, result);
}

// ------- JNI: typo correction LM helpers -------

extern "C"
JNIEXPORT jint JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_vocabSize(
        JNIEnv * /* env */,
        jobject /* thiz */
) {
    return g_vocab ? llama_vocab_n_tokens(g_vocab) : 0;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_typoEncodeRaw(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jText
) {
    const char *c_text = jText ? env->GetStringUTFChars(jText, nullptr) : nullptr;
    std::string text = c_text ? c_text : "";
    if (c_text) env->ReleaseStringUTFChars(jText, c_text);

    const auto tokens = tokenize_text(text, /*add_bos=*/false, /*add_eos=*/false);
    jintArray result = env->NewIntArray(static_cast<jsize>(tokens.size()));
    if (!result) {
        return nullptr;
    }
    std::vector<jint> values(tokens.size());
    for (size_t i = 0; i < tokens.size(); ++i) {
        values[i] = static_cast<jint>(tokens[i]);
    }
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_typoTokenToSingleCharacter(
        JNIEnv *env,
        jobject /* thiz */,
        jint tokenId
) {
    const std::string piece = token_to_piece_str(static_cast<llama_token>(tokenId));
    const std::string ch = first_utf8_codepoint(piece);
    const auto cps = utf8_codepoints(piece);
    if (cps.size() != 1) {
        return env->NewString(reinterpret_cast<const jchar *>(u""), 0);
    }
    return toJString(env, ch);
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_kazumaproject_zenz_ZenzEngine_typoNextLogProbs(
        JNIEnv *env,
        jobject /* thiz */,
        jstring jPromptPrefix,
        jintArray jEmittedTokenIds
) {
    const char *c_prompt = jPromptPrefix ? env->GetStringUTFChars(jPromptPrefix, nullptr) : nullptr;
    std::string prompt_prefix = c_prompt ? c_prompt : "";
    if (c_prompt) env->ReleaseStringUTFChars(jPromptPrefix, c_prompt);

    std::vector<int32_t> emitted;
    if (jEmittedTokenIds) {
        const jsize n = env->GetArrayLength(jEmittedTokenIds);
        emitted.resize(static_cast<size_t>(n));
        env->GetIntArrayRegion(jEmittedTokenIds, 0, n, reinterpret_cast<jint *>(emitted.data()));
    }

    const uint64_t request_seq = g_request_seq.fetch_add(1, std::memory_order_relaxed) + 1;
    const auto log_probs = typo_next_log_probs_internal(prompt_prefix, emitted, request_seq);
    const jsize vocab = static_cast<jsize>(log_probs.size());
    jfloatArray result = env->NewFloatArray(vocab);
    if (!result) {
        return nullptr;
    }
    if (!log_probs.empty()) {
        env->SetFloatArrayRegion(result, 0, vocab, log_probs.data());
    }
    return result;
}
