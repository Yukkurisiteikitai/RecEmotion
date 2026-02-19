cmake_minimum_required(VERSION 3.22.1)
project(cabocha_jni)
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ========================================
# Android NDK 設定
# ========================================
if(ANDROID)
    message(STATUS "Building for Android NDK - iconv disabled")
    add_compile_options(-Wno-typedef-redefinition)
endif()

# ========================================
# Iconv Stub Library (Android has no iconv)
# ========================================
add_library(iconv_stub STATIC iconv_stub.cpp)

# ========================================
# MeCab Library
# ========================================
set(MECAB_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/mecab/src)

# MeCab のコアソースファイル（ライブラリ部分のみ）
# iconv_utils.cpp は Android では使用しない（iconv がないため）
set(MECAB_SOURCES
    ${MECAB_SRC_DIR}/char_property.cpp
    ${MECAB_SRC_DIR}/connector.cpp
    ${MECAB_SRC_DIR}/context_id.cpp
    ${MECAB_SRC_DIR}/dictionary.cpp
    ${MECAB_SRC_DIR}/dictionary_compiler.cpp
    ${MECAB_SRC_DIR}/dictionary_generator.cpp
    ${MECAB_SRC_DIR}/dictionary_rewriter.cpp
    ${MECAB_SRC_DIR}/eval.cpp
    ${MECAB_SRC_DIR}/feature_index.cpp
    ${MECAB_SRC_DIR}/lbfgs.cpp
    ${MECAB_SRC_DIR}/learner.cpp
    ${MECAB_SRC_DIR}/learner_tagger.cpp
    ${MECAB_SRC_DIR}/libmecab.cpp
    ${MECAB_SRC_DIR}/nbest_generator.cpp
    ${MECAB_SRC_DIR}/param.cpp
    ${MECAB_SRC_DIR}/string_buffer.cpp
    ${MECAB_SRC_DIR}/tagger.cpp
    ${MECAB_SRC_DIR}/tokenizer.cpp
    ${MECAB_SRC_DIR}/utils.cpp
    ${MECAB_SRC_DIR}/viterbi.cpp
    ${MECAB_SRC_DIR}/writer.cpp
)

add_library(mecab STATIC ${MECAB_SOURCES})
target_include_directories(mecab PUBLIC ${MECAB_SRC_DIR})

# MeCab の必須定義
target_compile_definitions(mecab PRIVATE
    HAVE_CONFIG_H
    DIC_VERSION=102
    VERSION="0.996"
    PACKAGE="mecab"
    __ANDROID__
    DISABLE_ICONV=1
)

# Android 向けの設定
target_compile_options(mecab PRIVATE
    -fPIC
    -O2
    -Wall
    -Wno-deprecated
    -Wno-typedef-redefinition
)

target_link_libraries(mecab iconv_stub)

# ========================================
# CRF++ Library
# ========================================
set(CRFPP_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/crfpp)

set(CRFPP_SOURCES
    ${CRFPP_SRC_DIR}/encoder.cpp
    ${CRFPP_SRC_DIR}/feature.cpp
    ${CRFPP_SRC_DIR}/feature_cache.cpp
    ${CRFPP_SRC_DIR}/feature_index.cpp
    ${CRFPP_SRC_DIR}/lbfgs.cpp
    ${CRFPP_SRC_DIR}/libcrfpp.cpp
    ${CRFPP_SRC_DIR}/node.cpp
    ${CRFPP_SRC_DIR}/param.cpp
    ${CRFPP_SRC_DIR}/path.cpp
    ${CRFPP_SRC_DIR}/tagger.cpp
)

add_library(crfpp STATIC ${CRFPP_SOURCES})

target_include_directories(crfpp PUBLIC ${CRFPP_SRC_DIR})

target_compile_definitions(crfpp PRIVATE
    HAVE_CONFIG_H
    VERSION="0.58"
    PACKAGE="crfpp"
    __ANDROID__
)

target_compile_options(crfpp PRIVATE
    -fPIC
    -O2
    -Wall
    -Wno-deprecated
    -Wno-typedef-redefinition
)

# ========================================
# CaboCha Library
# ========================================
set(CABOCHA_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/cabocha/src)
# ucs.cpp は iconv を使用するため Android では除外
set(CABOCHA_SOURCES
    ${CABOCHA_SRC_DIR}/chunk_learner.cpp
    ${CABOCHA_SRC_DIR}/chunker.cpp
    ${CABOCHA_SRC_DIR}/dep.cpp
    ${CABOCHA_SRC_DIR}/dep_learner.cpp
    ${CABOCHA_SRC_DIR}/eval.cpp
    ${CABOCHA_SRC_DIR}/learner.cpp
    ${CABOCHA_SRC_DIR}/libcabocha.cpp
    ${CABOCHA_SRC_DIR}/morph.cpp
    ${CABOCHA_SRC_DIR}/ne.cpp
    ${CABOCHA_SRC_DIR}/normalizer.cpp
    ${CABOCHA_SRC_DIR}/param.cpp
    ${CABOCHA_SRC_DIR}/parser.cpp
    ${CABOCHA_SRC_DIR}/selector.cpp
    ${CABOCHA_SRC_DIR}/string_buffer.cpp
    ${CABOCHA_SRC_DIR}/svm.cpp
    ${CABOCHA_SRC_DIR}/svm_learn.cpp
    ${CABOCHA_SRC_DIR}/tree.cpp
    ${CABOCHA_SRC_DIR}/tree_allocator.cpp
    ${CABOCHA_SRC_DIR}/utils.cpp
)

add_library(cabocha STATIC ${CABOCHA_SOURCES})
target_include_directories(cabocha PUBLIC
    ${CABOCHA_SRC_DIR}
    ${MECAB_SRC_DIR}
    ${CRFPP_SRC_DIR}
)

target_compile_definitions(cabocha PRIVATE
    HAVE_CONFIG_H
    VERSION="0.69"
    PACKAGE="cabocha"
    MODEL_VERSION=102
    __ANDROID__
    DISABLE_ICONV=1
)

target_compile_options(cabocha PRIVATE
    -fPIC
    -O2
    -Wall
    -Wno-deprecated
    -Wno-typedef-redefinition
)

target_link_libraries(cabocha mecab crfpp iconv_stub)

# ========================================
# JNI Wrapper Library
# ========================================
# JNI Wrapper for CaboCha
# ========================================
add_library(cabocha_jni SHARED
    cabocha_jni.cpp
)

target_include_directories(cabocha_jni PRIVATE
    ${CABOCHA_SRC_DIR}
    ${MECAB_SRC_DIR}
    ${CRFPP_SRC_DIR}
)

target_link_libraries(cabocha_jni
    cabocha
    mecab
    crfpp
    iconv_stub
    log
)

# Android の log ライブラリをリンク
find_library(log-lib log)
target_link_libraries(cabocha_jni ${log-lib})