#include <jni.h>

#include "solver.h"

#include <atomic>
#include <chrono>
#include <cstdint>
#include <exception>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace {
std::mutex cancellation_mutex;
std::unordered_map<jlong, std::shared_ptr<std::atomic_bool>> cancellations;
std::atomic<jlong> next_cancellation_id{1};

std::mutex model_mutex;
std::optional<blocks::PatternValue> pattern_model;
std::optional<blocks::SymmetricPatternValue> symmetric_model;

void throw_java(JNIEnv* env, const char* type, const std::string& message) {
    jclass exception = env->FindClass(type);
    if (exception) env->ThrowNew(exception, message.c_str());
}

std::shared_ptr<std::atomic_bool> cancellation(jlong id) {
    std::lock_guard lock(cancellation_mutex);
    auto found = cancellations.find(id);
    if (found == cancellations.end()) throw std::invalid_argument("invalid cancellation handle");
    return found->second;
}

std::vector<jint> ints(JNIEnv* env, jintArray array) {
    if (!array) throw std::invalid_argument("missing solver array");
    std::vector<jint> values(env->GetArrayLength(array));
    if (!values.empty()) env->GetIntArrayRegion(array, 0, values.size(), values.data());
    return values;
}

std::string chars(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* raw = env->GetStringUTFChars(value, nullptr);
    if (!raw) throw std::runtime_error("could not read model path");
    std::string result(raw);
    env->ReleaseStringUTFChars(value, raw);
    return result;
}

jobject make_result(JNIEnv* env, const blocks::Result& result) {
    jclass result_class = env->FindClass("com/flo/blocks/game/NativeSolver$NativeResult");
    if (!result_class) return nullptr;
    jmethodID constructor = env->GetMethodID(result_class, "<init>", "([I[IIFZZZJDD)V");
    if (!constructor) return nullptr;

    jintArray moves = env->NewIntArray(result.count * 3);
    jintArray clears = env->NewIntArray(result.count);
    if (!moves || !clears) return nullptr;
    jint flat_moves[9]{};
    jint clear_counts[3]{};
    for (int index = 0; index < result.count; ++index) {
        flat_moves[index * 3] = result.moves[index].block;
        flat_moves[index * 3 + 1] = result.moves[index].x;
        flat_moves[index * 3 + 2] = result.moves[index].y;
        clear_counts[index] = result.clears[index];
    }
    if (result.count) {
        env->SetIntArrayRegion(moves, 0, result.count * 3, flat_moves);
        env->SetIntArrayRegion(clears, 0, result.count, clear_counts);
    }
    jobject value = env->NewObject(result_class, constructor, moves, clears,
        result.cleared, result.score, static_cast<jboolean>(result.complete),
        static_cast<jboolean>(result.exact), static_cast<jboolean>(result.interrupted),
        static_cast<jlong>(result.nodes), result.first_ms, result.elapsed_ms);
    env->DeleteLocalRef(moves);
    env->DeleteLocalRef(clears);
    env->DeleteLocalRef(result_class);
    return value;
}

blocks::Options make_options(jint mode, jlong budget_micros,
                             const std::string& model_path,
                             const std::atomic_bool* cancel) {
    if (budget_micros < 0) throw std::invalid_argument("negative search budget");
    blocks::Options options;
    options.budget = std::chrono::microseconds(budget_micros);
    options.cancel = cancel;
    switch (mode) {
    case 0: options.greedy_only = true; break;
    case 1: options.beam_width = 128; break;
    case 2: options.beam_width = 512; break;
    case 3: options.exhaustive = true; break;
    case 4: {
        if (model_path.empty()) throw std::invalid_argument("missing pattern model path");
        std::lock_guard lock(model_mutex);
        if (!pattern_model) pattern_model = blocks::PatternValue::load(model_path);
        options.model = &*pattern_model;
        options.beam_width = 128;
        break;
    }
    case 5: {
        if (model_path.empty()) throw std::invalid_argument("missing symmetric model path");
        std::lock_guard lock(model_mutex);
        if (!symmetric_model) symmetric_model = blocks::SymmetricPatternValue::load(model_path);
        options.symmetric_model = &*symmetric_model;
        options.beam_width = 128;
        break;
    }
    default: throw std::invalid_argument("unknown native algorithm");
    }
    return options;
}

blocks::Board make_board(jint width, jint height, const std::vector<jint>& rows) {
    blocks::Board board(width, height);
    if (rows.size() != static_cast<size_t>(height))
        throw std::invalid_argument("board row count differs from height");
    for (int y = 0; y < height; ++y) {
        if (rows[y] < 0 || (rows[y] & ~board.mask()))
            throw std::invalid_argument("board has cells outside its width");
        board.rows[y] = rows[y];
    }
    return board;
}

std::vector<blocks::Shape> make_shapes(const std::vector<jint>& packed) {
    if (packed.size() % 7 || packed.size() > 21)
        throw std::invalid_argument("invalid packed shape count");
    std::vector<blocks::Shape> result;
    for (size_t start = 0; start < packed.size(); start += 7) {
        blocks::Shape shape;
        shape.width = packed[start];
        shape.height = packed[start + 1];
        for (int y = 0; y < 5; ++y) {
            if (packed[start + 2 + y] < 0 || packed[start + 2 + y] > 0xffff)
                throw std::invalid_argument("invalid shape row bits");
            shape.rows[y] = packed[start + 2 + y];
        }
        result.push_back(shape);
    }
    return result;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_flo_blocks_game_NativeSolver_nativeCreateCancellation(JNIEnv*, jobject) {
    const jlong id = next_cancellation_id.fetch_add(1);
    std::lock_guard lock(cancellation_mutex);
    cancellations.emplace(id, std::make_shared<std::atomic_bool>(false));
    return id;
}

extern "C" JNIEXPORT void JNICALL
Java_com_flo_blocks_game_NativeSolver_nativeCancel(JNIEnv*, jobject, jlong id) {
    std::lock_guard lock(cancellation_mutex);
    auto found = cancellations.find(id);
    if (found != cancellations.end()) found->second->store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_flo_blocks_game_NativeSolver_nativeReleaseCancellation(JNIEnv*, jobject, jlong id) {
    std::lock_guard lock(cancellation_mutex);
    cancellations.erase(id);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_flo_blocks_game_NativeSolver_nativeSolve(
    JNIEnv* env, jobject, jint width, jint height, jintArray board_rows,
    jintArray shapes, jint mode, jlong budget_micros, jstring model_path,
    jlong cancellation_id, jobject callback) {
    try {
        if (!callback) throw std::invalid_argument("missing improvement callback");
        auto cancel = cancellation(cancellation_id);
        auto board = make_board(width, height, ints(env, board_rows));
        auto hand = make_shapes(ints(env, shapes));
        auto options = make_options(mode, budget_micros, chars(env, model_path), cancel.get());
        jclass callback_class = env->GetObjectClass(callback);
        jmethodID accept = env->GetMethodID(callback_class, "accept",
            "(Lcom/flo/blocks/game/NativeSolver$NativeResult;)V");
        if (!accept) return nullptr;
        const auto on_improvement = [&](const blocks::Result& improvement) {
            if (env->ExceptionCheck()) return;
            jobject value = make_result(env, improvement);
            if (value && !env->ExceptionCheck()) env->CallVoidMethod(callback, accept, value);
            if (value) env->DeleteLocalRef(value);
            if (env->ExceptionCheck()) cancel->store(true);
        };
        blocks::Result result = blocks::solve(board, hand, options, on_improvement);
        if (env->ExceptionCheck()) return nullptr;
        return make_result(env, result);
    } catch (const std::invalid_argument& error) {
        if (!env->ExceptionCheck()) throw_java(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception& error) {
        if (!env->ExceptionCheck()) throw_java(env, "java/lang/IllegalStateException", error.what());
    }
    return nullptr;
}
