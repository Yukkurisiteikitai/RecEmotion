#include <jni.h>
#include <string>
#include <sstream>
#include <android/log.h>
#include "cabocha.h"

#define TAG "CaboCha_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// JSON 文字列のエスケープ
static std::string escape_json(const std::string& s) {
    std::string out;
    out.reserve(s.size());
    for (char c : s) {
        if (c == '"')       out += "\\\"";
        else if (c == '\\') out += "\\\\";
        else if (c == '\n') out += "\\n";
        else if (c == '\r') out += "\\r";
        else                out += c;
    }
    return out;
}

extern "C" {

/**
 * CaboCha で文字列を解析し JSON を返す。
 *
 * @param jMecabDicDir    MeCab 辞書ディレクトリのパス（filesDir 以下にコピーしたもの）
 * @param jCabochaModelDir CaboCha モデルディレクトリのパス（dep.ipa.model 等を含む）
 * @param jtext           解析対象テキスト
 * @return JSON 文字列 { "chunks": [...] }
 */
JNIEXPORT jstring JNICALL
Java_com_example_recemotion_data_parser_NativeCabochaParser_nativeParse(
    JNIEnv* env,
    jobject /* this */,
    jstring jMecabDicDir,
    jstring jCabochaModel,
    jstring jtext)
{
    const char* mecabDicDir  = env->GetStringUTFChars(jMecabDicDir,  nullptr);
    const char* cabochaModel = env->GetStringUTFChars(jCabochaModel, nullptr);
    const char* text         = env->GetStringUTFChars(jtext,         nullptr);

    if (!text || text[0] == '\0') {
        env->ReleaseStringUTFChars(jMecabDicDir,  mecabDicDir);
        env->ReleaseStringUTFChars(jCabochaModel, cabochaModel);
        env->ReleaseStringUTFChars(jtext,         text);
        return env->NewStringUTF("{\"chunks\":[]}");
    }

    LOGI("nativeParse: mecabDic=%s, text_len=%zu", mecabDicDir, strlen(text));

    // CaboCha 初期化引数を組み立て
    // -m: dep model, -M: chunk model, -d: MeCab 辞書
    std::string args;
    if (mecabDicDir && mecabDicDir[0] != '\0') {
        args += " -d ";
        args += mecabDicDir;
    }
    if (cabochaModel && cabochaModel[0] != '\0') {
        std::string modelDir(cabochaModel);
        args += " -m " + modelDir + "/dep.ipa.model";
        args += " -M " + modelDir + "/chunk.ipa.model";
    }

    CaboCha::Parser* parser = nullptr;
    try {
        parser = CaboCha::Parser::create(args.c_str());
    } catch (const std::exception& e) {
        LOGE("CaboCha init exception: %s", e.what());
    }

    env->ReleaseStringUTFChars(jMecabDicDir,  mecabDicDir);
    env->ReleaseStringUTFChars(jCabochaModel, cabochaModel);

    if (!parser) {
        LOGE("CaboCha init failed – returning fallback");
        std::string escText = escape_json(text);
        std::string fb = "{\"error\":\"init_failed\",\"chunks\":[{\"id\":0,\"link\":-1,"
                         "\"tokens\":[{\"surface\":\"" + escText + "\",\"pos\":\"\"}]}]}";
        env->ReleaseStringUTFChars(jtext, text);
        return env->NewStringUTF(fb.c_str());
    }

    const CaboCha::Tree* tree = nullptr;
    try {
        tree = parser->parse(text);
    } catch (const std::exception& e) {
        LOGE("CaboCha parse exception: %s", e.what());
    }
    env->ReleaseStringUTFChars(jtext, text);

    if (!tree) {
        delete parser;
        return env->NewStringUTF("{\"error\":\"parse_failed\",\"chunks\":[]}");
    }

    // 結果を JSON に変換
    std::ostringstream json;
    json << "{\"chunks\":[";

    for (size_t i = 0; i < tree->chunk_size(); ++i) {
        const CaboCha::Chunk* chunk = tree->chunk(i);
        if (i > 0) json << ",";

        json << "{\"id\":" << i
             << ",\"link\":" << chunk->link
             << ",\"tokens\":[";

        // chunk->token_pos と chunk->token_size でトークン範囲を特定
        size_t token_start = chunk->token_pos;
        size_t token_end   = chunk->token_pos + chunk->token_size;

        for (size_t j = token_start; j < token_end; ++j) {
            const CaboCha::Token* token = tree->token(j);
            if (j > token_start) json << ",";

            std::string surface = token->surface ? escape_json(token->surface) : "";
            std::string feature = token->feature ? token->feature : "";

            std::string pos;
            size_t cp = feature.find(',');
            pos = (cp != std::string::npos) ? feature.substr(0, cp) : feature;

            json << "{\"surface\":\"" << surface
                 << "\",\"pos\":\"" << escape_json(pos) << "\"}";
        }
        json << "]}";
    }
    json << "]}";

    // chunk_size() は parser 削除前に取得する（Tree は Parser が所有）
    size_t chunk_count = tree->chunk_size();
    std::string result = json.str();
    delete parser;  // ← これ以降 tree は dangling pointer

    LOGI("nativeParse done: %zu chunks", chunk_count);
    return env->NewStringUTF(result.c_str());
}

/**
 * CaboCha が使用可能か検証する（辞書・モデルロードテスト）。
 * @return 0=OK, 1=init失敗, 2=parse失敗
 */
JNIEXPORT jint JNICALL
Java_com_example_recemotion_data_parser_NativeCabochaParser_nativeVerify(
    JNIEnv* env,
    jobject,
    jstring jMecabDicDir,
    jstring jCabochaModelDir)
{
    const char* mecabDicDir    = env->GetStringUTFChars(jMecabDicDir,      nullptr);
    const char* cabochaModelDir = env->GetStringUTFChars(jCabochaModelDir, nullptr);

    std::string args;
    if (mecabDicDir && mecabDicDir[0] != '\0') {
        args += " -d ";
        args += mecabDicDir;
    }
    if (cabochaModelDir && cabochaModelDir[0] != '\0') {
        std::string modelDir(cabochaModelDir);
        args += " -m " + modelDir + "/dep.ipa.model";
        args += " -M " + modelDir + "/chunk.ipa.model";
    }
    env->ReleaseStringUTFChars(jCabochaModelDir, cabochaModelDir);

    CaboCha::Parser* parser = nullptr;
    try {
        parser = CaboCha::Parser::create(args.c_str());
    } catch (...) {}

    env->ReleaseStringUTFChars(jMecabDicDir, mecabDicDir);

    if (!parser) {
        LOGE("nativeVerify: parser init failed (args=%s)", args.c_str());
        return 1;
    }

    const CaboCha::Tree* tree = nullptr;
    try {
        tree = parser->parse("テスト。");
    } catch (...) {}

    // chunk_size() は parser 削除前に取得する（Tree は Parser が所有）
    if (!tree) {
        delete parser;
        LOGE("nativeVerify: parse failed");
        return 2;
    }

    size_t chunk_count = tree->chunk_size();
    delete parser;  // ← これ以降 tree は dangling pointer

    LOGI("nativeVerify: OK (chunks=%zu)", chunk_count);
    return 0;
}

} // extern "C"
