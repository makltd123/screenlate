// Thin JNI bridge to SentencePiece: load a model, split text into pieces, join pieces into text.
// Text crosses the boundary as UTF-8 byte arrays because JNI's own string encoding is modified
// UTF-8, which mangles characters outside the Basic Multilingual Plane such as emoji.

#include <jni.h>

#include <memory>
#include <string>
#include <vector>

#include "sentencepiece_processor.h"

namespace {

using sentencepiece::SentencePieceProcessor;

SentencePieceProcessor* processor(jlong handle) {
    return reinterpret_cast<SentencePieceProcessor*>(handle);
}

std::string readBytes(JNIEnv* env, jbyteArray array) {
    const jsize length = env->GetArrayLength(array);
    std::string out(static_cast<size_t>(length), '\0');
    env->GetByteArrayRegion(array, 0, length, reinterpret_cast<jbyte*>(out.data()));
    return out;
}

jbyteArray toBytes(JNIEnv* env, const std::string& value) {
    const auto length = static_cast<jsize>(value.size());
    jbyteArray array = env->NewByteArray(length);
    if (array != nullptr) {
        env->SetByteArrayRegion(array, 0, length, reinterpret_cast<const jbyte*>(value.data()));
    }
    return array;
}

void throwIllegalState(JNIEnv* env, const std::string& message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_screenlate_app_translate_SentencePieceJni_nativeLoad(JNIEnv* env, jclass, jbyteArray model) {
    auto spm = std::make_unique<SentencePieceProcessor>();
    const auto status = spm->LoadFromSerializedProto(readBytes(env, model));
    if (!status.ok()) {
        throwIllegalState(env, status.ToString());
        return 0;
    }
    return reinterpret_cast<jlong>(spm.release());
}

JNIEXPORT jobjectArray JNICALL
Java_com_screenlate_app_translate_SentencePieceJni_nativeEncode(JNIEnv* env, jclass, jlong handle, jbyteArray text) {
    std::vector<std::string> pieces;
    const auto status = processor(handle)->Encode(readBytes(env, text), &pieces);
    if (!status.ok()) {
        throwIllegalState(env, status.ToString());
        return nullptr;
    }
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(pieces.size()), byteArrayClass, nullptr);
    if (result == nullptr) return nullptr;
    for (size_t i = 0; i < pieces.size(); ++i) {
        jbyteArray piece = toBytes(env, pieces[i]);
        env->SetObjectArrayElement(result, static_cast<jsize>(i), piece);
        env->DeleteLocalRef(piece);
    }
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_screenlate_app_translate_SentencePieceJni_nativeDecode(JNIEnv* env, jclass, jlong handle, jobjectArray pieces) {
    const jsize count = env->GetArrayLength(pieces);
    std::vector<std::string> values;
    values.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto piece = static_cast<jbyteArray>(env->GetObjectArrayElement(pieces, i));
        values.push_back(readBytes(env, piece));
        env->DeleteLocalRef(piece);
    }
    std::string text;
    const auto status = processor(handle)->Decode(values, &text);
    if (!status.ok()) {
        throwIllegalState(env, status.ToString());
        return nullptr;
    }
    return toBytes(env, text);
}

JNIEXPORT void JNICALL
Java_com_screenlate_app_translate_SentencePieceJni_nativeFree(JNIEnv*, jclass, jlong handle) {
    delete processor(handle);
}

}  // extern "C"
