#include <jni.h>
#include <string>
#include <sstream>
#include <android/log.h>
#include "cabocha.h"

#define TAG "CaboCha_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_recemotion_data_parser_CabochaDependencyParser_nativeParse(
    JNIEnv* env,
    jobject /* this */,
    jstring jtext) {
    
    // Java 文字列を C++ 文字列に変換
    const char* text = env->GetStringUTFChars(jtext, nullptr);
    if (text == nullptr) {
        LOGE("Failed to get text string");
        return env->NewStringUTF("");
    }
    
    LOGI("Parsing text: %s", text);
    
    // CaboCha パーサーの初期化
    // 辞書がない場合はフォールバック用の簡易JSONを返す
    cabocha::CaboCha* parser = nullptr;
    
    // 辞書パスを試行（後で assets から取得するように拡張可能）
    const char* dict_paths[] = {
        "/data/local/tmp/mecab/dic",  // テスト用
        "",  // デフォルト
        nullptr
    };
    
    for (int i = 0; dict_paths[i] != nullptr && parser == nullptr; i++) {
        parser = cabocha::createCaboCha(dict_paths[i]);
    }
    
    if (parser == nullptr) {
        LOGE("Failed to create CaboCha parser, returning fallback");
        env->ReleaseStringUTFChars(jtext, text);
        
        // フォールバック: 全体を1つのチャンクとして返す
        std::string fallback = "{\"chunks\":[{\"id\":0,\"link\":-1,\"tokens\":[{\"surface\":\"";
        fallback += text;
        fallback += "\",\"pos\":\"Unknown\"}]}]}";
        return env->NewStringUTF(fallback.c_str());
    }
    
    // パース実行
    const cabocha::Tree* tree = parser->parse(text);
    env->ReleaseStringUTFChars(jtext, text);
    
    if (tree == nullptr) {
        LOGE("Parse failed");
        delete parser;
        return env->NewStringUTF("");
    }
    
    // JSON 形式で結果を構築
    std::ostringstream json;
    json << "{\"chunks\":[";
    
    for (size_t i = 0; i < tree->chunk_size(); ++i) {
        const cabocha::Chunk* chunk = tree->chunk(i);
        if (i > 0) json << ",";
        
        json << "{\"id\":" << i 
             << ",\"link\":" << chunk->link
             << ",\"tokens\":[";
        
        // このチャンクに属するトークンを収集
        size_t token_start = chunk->token_pos;
        size_t token_end = (i + 1 < tree->chunk_size()) 
            ? tree->chunk(i + 1)->token_pos 
            : tree->token_size();
        
        for (size_t j = token_start; j < token_end; ++j) {
            const cabocha::Token* token = tree->token(j);
            if (j > token_start) json << ",";
            
            // 表層形と品詞を取得
            std::string surface = token->surface ? token->surface : "";
            std::string feature = token->feature ? token->feature : "";
            
            // 品詞は feature の最初の要素（カンマ区切り）
            std::string pos = "";
            size_t comma_pos = feature.find(',');
            if (comma_pos != std::string::npos) {
                pos = feature.substr(0, comma_pos);
            }
            
            json << "{\"surface\":\"" << surface 
                 << "\",\"pos\":\"" << pos << "\"}";
        }
        
        json << "]}";
    }
    
    json << "]}";
    
    delete parser;
    
    std::string result = json.str();
    LOGI("Parse result: %s", result.c_str());
    
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
