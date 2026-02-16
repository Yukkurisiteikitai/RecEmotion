#include <android/log.h>
#include <jni.h>

#include <chrono>
#include <climits>
#include <cmath>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

constexpr const char *kTag = "llama_jni";
constexpr int kDefaultMaxTokens = 128;

struct LlamaState {
  llama_model *model = nullptr;
  llama_context *ctx = nullptr;
  const llama_vocab *vocab = nullptr;
  llama_batch batch = {};
  int n_ctx = 2048;
  int n_threads = 4;
};

std::mutex g_mutex;
LlamaState g_state;
bool g_backend_initialized = false;

void log_info(const char *msg) {
  __android_log_print(ANDROID_LOG_INFO, kTag, "%s", msg);
}

void log_info(const std::string &msg) {
  log_info(msg.c_str());
}

void log_error(const char *msg) {
  __android_log_print(ANDROID_LOG_ERROR, kTag, "%s", msg);
}

void log_error(const std::string &msg) {
  log_error(msg.c_str());
}

std::string jstring_to_string(JNIEnv *env, jstring value) {
  if (value == nullptr) {
    return std::string();
  }
  const char *chars = env->GetStringUTFChars(value, nullptr);
  std::string result = chars ? chars : "";
  if (chars) {
    env->ReleaseStringUTFChars(value, chars);
  }
  return result;
}

std::string token_to_piece(llama_token token) {
  if (!g_state.vocab) {
    return std::string();
  }
  std::string result;
  result.resize(8 * 1024);
  const int n = llama_token_to_piece(g_state.vocab, token, result.data(),
                                     static_cast<int>(result.size()), 0, false);
  if (n <= 0) {
    return std::string();
  }
  result.resize(static_cast<size_t>(n));
  return result;
}

void release_state() {
  if (g_state.batch.token) {
    llama_batch_free(g_state.batch);
    g_state.batch = {};
  }
  if (g_state.ctx) {
    llama_free(g_state.ctx);
    g_state.ctx = nullptr;
  }
  if (g_state.model) {
    llama_model_free(g_state.model);
    g_state.model = nullptr;
  }
  g_state.vocab = nullptr;
}

} // namespace

// ========== Library Load Confirmation ==========
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  log_info(
      "✅ libllama_jni.so JNI_OnLoad called - Library loaded successfully");
  return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_recemotion_LLMInferenceHelper_nativeInit(JNIEnv *env,
                                                          jobject /*thiz*/,
                                                          jstring modelPath,
                                                          jint nCtx,
                                                          jint nThreads) {
  std::lock_guard<std::mutex> lock(g_mutex);

  const std::string path = jstring_to_string(env, modelPath);
  if (path.empty()) {
    log_error("Model path is empty");
    return env->NewStringUTF("Error: Model path is empty");
  }

  log_info(("Attempting to load model from: " + path).c_str());

  if (!g_backend_initialized) {
    llama_backend_init();
    g_backend_initialized = true;
    log_info("llama backend initialized");
  }

  release_state();

  g_state.n_ctx = nCtx > 0 ? nCtx : g_state.n_ctx;
  g_state.n_threads = nThreads > 0 ? nThreads : g_state.n_threads;

  llama_model_params model_params = llama_model_default_params();
  model_params.n_gpu_layers = 0; // CPU only on Android

  llama_context_params ctx_params = llama_context_default_params();
  ctx_params.n_ctx = g_state.n_ctx;
  ctx_params.n_threads = g_state.n_threads;
  ctx_params.n_threads_batch = g_state.n_threads;

  g_state.model = llama_model_load_from_file(path.c_str(), model_params);
  if (!g_state.model) {
    log_error(
        "Failed to load model - file may be corrupted or invalid GGUF format");
    return env->NewStringUTF(
        "Failed to load model.\n\nPossible causes:\n- File is not a valid GGUF "
        "format\n- File is corrupted\n- Insufficient memory\n- Model "
        "architecture not supported");
  }

  log_info("Model loaded successfully");

  g_state.ctx = llama_init_from_model(g_state.model, ctx_params);
  if (!g_state.ctx) {
    log_error(
        "Failed to create context - insufficient memory or invalid parameters");
    release_state();
    return env->NewStringUTF(
        "Failed to create context.\n\nPossible causes:\n- Insufficient "
        "memory\n- Context size too large\n- Try a smaller model");
  }

  log_info("Context created successfully");

  g_state.n_ctx = static_cast<int>(llama_n_ctx(g_state.ctx));

  g_state.vocab = llama_model_get_vocab(g_state.model);
  if (!g_state.vocab) {
    log_error("Failed to get vocab");
    release_state();
    return env->NewStringUTF("Failed to get vocab from model");
  }

  // Pre-allocate batch for reuse
  g_state.batch = llama_batch_init(g_state.n_ctx, 0, 1);
  if (!g_state.batch.token) {
    log_error("Failed to allocate batch");
    release_state();
    return env->NewStringUTF("Failed to allocate batch - insufficient memory");
  }

  log_info("✅ Model initialized successfully");
  return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_recemotion_LLMInferenceHelper_nativeGenerate(
    JNIEnv *env, jobject /*thiz*/, jstring promptText, jint maxTokens) {
  std::lock_guard<std::mutex> lock(g_mutex);

  if (!g_state.model || !g_state.ctx || !g_state.vocab) {
    return env->NewStringUTF("Model is not initialized");
  }

  const std::string prompt = jstring_to_string(env, promptText);
  const int max_tokens = maxTokens > 0 ? maxTokens : kDefaultMaxTokens;
  const int n_vocab = llama_vocab_n_tokens(g_state.vocab);

  log_info("=== nativeGenerate START ===");
  log_info("Prompt length: " + std::to_string(prompt.size()) + " chars");
  log_info("Max tokens: " + std::to_string(max_tokens));
  log_info("Vocab size: " + std::to_string(n_vocab));

  const auto start_time = std::chrono::steady_clock::now();

  if (n_vocab <= 0) {
    log_error("Invalid vocab size");
    return env->NewStringUTF("Invalid vocab size");
  }

  llama_memory_t mem = llama_get_memory(g_state.ctx);
  if (mem) {
    llama_memory_clear(mem, true);
  }

  // Tokenize prompt
  log_info("Tokenizing prompt...");
  const auto tokenize_start = std::chrono::steady_clock::now();
  std::vector<llama_token> prompt_tokens;
  int n_prompt = 0;
  {
    int tokens_capacity = static_cast<int>(prompt.size()) + 8;
    prompt_tokens.resize(static_cast<size_t>(tokens_capacity));
    n_prompt = llama_tokenize(
        g_state.vocab, prompt.c_str(), static_cast<int>(prompt.size()),
        prompt_tokens.data(), tokens_capacity, true, true);

    if (n_prompt == INT32_MIN) {
      return env->NewStringUTF("Tokenization overflow");
    }

    if (n_prompt < 0) {
      tokens_capacity = -n_prompt;
      prompt_tokens.resize(static_cast<size_t>(tokens_capacity));
      n_prompt = llama_tokenize(
          g_state.vocab, prompt.c_str(), static_cast<int>(prompt.size()),
          prompt_tokens.data(), tokens_capacity, true, true);
    }
  }

  if (n_prompt <= 0) {
    log_error("Failed to tokenize prompt");
    return env->NewStringUTF("Failed to tokenize prompt");
  }
  prompt_tokens.resize(static_cast<size_t>(n_prompt));

  const auto tokenize_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                                std::chrono::steady_clock::now() - tokenize_start)
                                .count();
  log_info("Tokenization done in " + std::to_string(tokenize_ms) + " ms");

  log_info("Tokenized: " + std::to_string(n_prompt) + " tokens");
  log_info("Context size: " + std::to_string(g_state.n_ctx));

  if (n_prompt > g_state.n_ctx - 4) {
    log_error("Prompt too long for context window");
    return env->NewStringUTF("Prompt too long for context window");
  }

  // Decode prompt tokens in batch
  log_info("Decoding prompt tokens...");
  const auto prompt_decode_start = std::chrono::steady_clock::now();
  for (int i = 0; i < n_prompt; ++i) {
    if (i % 50 == 0) {
      log_info("Decoded " + std::to_string(i) + "/" + std::to_string(n_prompt) +
               " prompt tokens");
    }

    g_state.batch.n_tokens = 1;
    g_state.batch.token[0] = prompt_tokens[i];
    g_state.batch.pos[0] = i;
    g_state.batch.n_seq_id[0] = 1;
    g_state.batch.seq_id[0][0] = 0;
    g_state.batch.logits[0] = (i == n_prompt - 1) ? 1 : 0;

    if (llama_decode(g_state.ctx, g_state.batch) != 0) {
      log_error("Failed to decode prompt at token " + std::to_string(i));
      return env->NewStringUTF("Failed to decode prompt");
    }
  }
  const auto prompt_decode_ms =
      std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::steady_clock::now() - prompt_decode_start)
          .count();
  log_info("Prompt decoding complete in " + std::to_string(prompt_decode_ms) +
           " ms");

  std::string output;
  output.reserve(static_cast<size_t>(max_tokens) * 4);

  int n_past = n_prompt;
  llama_token eos_token = llama_vocab_eos(g_state.vocab);
  bool stop_generation = false;

  log_info("Starting token generation (max: " + std::to_string(max_tokens) +
           ")");

  const auto generation_start = std::chrono::steady_clock::now();
  int generated_tokens = 0;

  for (int i = 0;
       i < max_tokens && n_past < g_state.n_ctx - 4 && !stop_generation; ++i) {
    if (i % 5 == 0) {
      log_info("Generated " + std::to_string(i) + "/" +
               std::to_string(max_tokens) + " tokens");
    }

    const float *logits = llama_get_logits_ith(g_state.ctx, -1);
    if (!logits) {
      logits = llama_get_logits(g_state.ctx);
    }

    if (!logits) {
      log_error("Failed to get logits at token " + std::to_string(i));
      break;
    }

    // Greedy sampling: select token with highest logit
    llama_token best_token = 0;
    float best_logit = logits[0];

    // This loop can be VERY slow if n_vocab is large (e.g., 32000+ tokens)
    for (int token_id = 1; token_id < n_vocab; ++token_id) {
      if (logits[token_id] > best_logit) {
        best_logit = logits[token_id];
        best_token = token_id;
      }
    }

    // Check for EOS token
    if (best_token == eos_token) {
      stop_generation = true;
      break;
    }

    // Convert token to text
    const std::string piece = token_to_piece(best_token);
    if (piece.empty()) {
      continue;
    }
    output += piece;

    // Prepare next token for decoding
    g_state.batch.n_tokens = 1;
    g_state.batch.token[0] = best_token;
    g_state.batch.pos[0] = n_past;
    g_state.batch.n_seq_id[0] = 1;
    g_state.batch.seq_id[0][0] = 0;
    g_state.batch.logits[0] = 1;

    if (llama_decode(g_state.ctx, g_state.batch) != 0) {
      log_error("Failed during decoding generation at token " +
                std::to_string(i));
      return env->NewStringUTF("Failed during decoding generation");
    }
    n_past += 1;
    generated_tokens = i + 1;
  }

  const auto generation_ms =
      std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::steady_clock::now() - generation_start)
          .count();
  log_info("Generation loop finished in " + std::to_string(generation_ms) +
           " ms (generated " + std::to_string(generated_tokens) +
           " tokens)");

  const auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                            std::chrono::steady_clock::now() - start_time)
                            .count();
  log_info("Generation complete. Output length: " +
           std::to_string(output.size()) + " chars (total " +
           std::to_string(total_ms) + " ms)");

  return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_recemotion_LLMInferenceHelper_nativeRelease(JNIEnv * /*env*/,
                                                             jobject /*thiz*/) {
  std::lock_guard<std::mutex> lock(g_mutex);
  release_state();
  if (g_backend_initialized) {
    llama_backend_free();
    g_backend_initialized = false;
    log_info("llama backend freed");
  }
}

// ========== TensorFlow Lite Wrapper Functions ==========

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_recemotion_LLMInferenceHelper_nativeInitTFLite(
    JNIEnv *env, jobject /*thiz*/, jstring modelPath) {
  // TODO: Implement TensorFlow Lite model initialization
  // For now, return error message
  log_error("TensorFlow Lite support not yet implemented");
  return env->NewStringUTF(
      "TensorFlow Lite support not yet implemented. Please use .gguf format.");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_recemotion_LLMInferenceHelper_nativeGenerateTFLite(
    JNIEnv *env, jobject /*thiz*/, jstring promptText, jint maxTokens) {
  // TODO: Implement TensorFlow Lite inference
  log_error("TensorFlow Lite inference not yet implemented");
  return env->NewStringUTF("TensorFlow Lite inference not yet implemented");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_recemotion_LLMInferenceHelper_nativeReleaseTFLite(
    JNIEnv * /*env*/, jobject /*thiz*/) {
  // TODO: Implement TensorFlow Lite cleanup
  log_info("TensorFlow Lite released");
}