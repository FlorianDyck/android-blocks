#include "solver.h"
#include <algorithm>
#include <array>
#include <cmath>
#include <fstream>
#include <iomanip>
#include <limits>
#include <stdexcept>

namespace blocks {
namespace {
struct Point { int x, y; };

Point transform(Point point, int width, int height, int symmetry) {
    switch (symmetry) {
    case 0: return point;
    case 1: return {width - 1 - point.x, point.y};
    case 2: return {point.x, height - 1 - point.y};
    case 3: return {width - 1 - point.x, height - 1 - point.y};
    case 4: return {point.y, point.x};
    case 5: return {height - 1 - point.y, point.x};
    case 6: return {point.y, width - 1 - point.x};
    default: return {height - 1 - point.y, width - 1 - point.x};
    }
}

int transformed_key(int x, int y, int bits, int width, int height, int symmetry) {
    std::array<Point, 9> cells{};
    int left = width, top = height;
    for (int offset = 0; offset < 9; ++offset) {
        cells[offset] = transform({x + offset % 3, y + offset / 3}, width, height, symmetry);
        left = std::min(left, cells[offset].x);
        top = std::min(top, cells[offset].y);
    }
    int transformed_bits = 0;
    for (int offset = 0; offset < 9; ++offset)
        if (bits & (1 << offset))
            transformed_bits |= 1 << ((cells[offset].y - top) * 3 + cells[offset].x - left);
    return ((top * (width - 2) + left) * 512) + transformed_bits;
}

int canonical_key(int x, int y, int bits, int width, int height) {
    const int symmetry_count = width == height ? 8 : 4;
    int best = std::numeric_limits<int>::max();
    for (int symmetry = 0; symmetry < symmetry_count; ++symmetry)
        best = std::min(best, transformed_key(x, y, bits, width, height, symmetry));
    return best;
}
} // namespace

SymmetricPatternValue::SymmetricPatternValue(int width, int height)
    : width_(width), height_(height) {
    Board validate(width, height);
    const int features = window_count() * 512;
    std::vector<int> canonical(features);
    std::vector<int> compact(features, -1);
    for (int window = 0; window < window_count(); ++window) {
        const int x = window % (width_ - 2), y = window / (width_ - 2);
        for (int bits = 0; bits < 512; ++bits)
            canonical[window * 512 + bits] = canonical_key(x, y, bits, width_, height_);
    }
    // Canonical feature keys are sorted by their natural position/bit order.
    // Number only representatives, making serialization independent of traversal.
    int orbit_count = 0;
    for (int key = 0; key < features; ++key)
        if (canonical[key] == key)
            compact[key] = orbit_count++;
    weights_ = std::vector<float>(orbit_count, 0.f);
    mapping_ = std::vector<uint16_t>(features);
    for (int position = 0; position < features; ++position) {
        const int key = canonical[position];
        if (compact[key] < 0 || compact[key] > std::numeric_limits<uint16_t>::max())
            throw std::logic_error("invalid symmetric pattern orbit map");
        mapping_[position] = uint16_t(compact[key]);
    }
}

void SymmetricPatternValue::validate_board(const Board& board) const {
    if (board.width != width_ || board.height != height_ || !board.valid())
        throw std::invalid_argument("model dimensions differ from board or board invalid");
}

int SymmetricPatternValue::pattern(const Board& board, int window) const {
    const int x = window % (width_ - 2), y = window / (width_ - 2);
    return ((board.rows[y] >> x) & 7)
        | (((board.rows[y + 1] >> x) & 7) << 3)
        | (((board.rows[y + 2] >> x) & 7) << 6);
}

float SymmetricPatternValue::value(const Board& board) const {
    validate_board(board);
    float estimate = android_score(board) / 20.f;
    for (int window = 0; window < window_count(); ++window)
        estimate += weights_[mapping_[window * 512 + pattern(board, window)]];
    return estimate;
}

void SymmetricPatternValue::update(const Board& board, float target, float learning_rate) {
    if (!std::isfinite(target) || !std::isfinite(learning_rate)
        || learning_rate <= 0 || learning_rate > 1)
        throw std::invalid_argument("invalid training target or learning rate");
    validate_board(board);
    std::array<uint16_t, 169> active{};
    for (int window = 0; window < window_count(); ++window)
        active[window] = mapping_[window * 512 + pattern(board, window)];
    std::sort(active.begin(), active.begin() + window_count());
    int squared_count = 0;
    for (int start = 0; start < window_count();) {
        int end = start + 1;
        while (end < window_count() && active[end] == active[start]) ++end;
        const int count = end - start;
        squared_count += count * count;
        start = end;
    }
    const float error = target - value(board);
    const float scale = learning_rate * error / squared_count;
    for (int start = 0; start < window_count();) {
        int end = start + 1;
        while (end < window_count() && active[end] == active[start]) ++end;
        weights_[active[start]] += scale * (end - start);
        start = end;
    }
}

void SymmetricPatternValue::save(const std::string& path) const {
    std::ofstream output(path);
    output << "BLOCKS_SYMMETRIC_PATTERN 1 " << width_ << ' ' << height_ << ' '
           << weights_.size() << '\n';
    output << std::setprecision(std::numeric_limits<float>::max_digits10) << discount << '\n';
    for (float weight : weights_) output << weight << '\n';
    output.flush();
    if (!output) throw std::runtime_error("could not save symmetric model: " + path);
}

SymmetricPatternValue SymmetricPatternValue::load(const std::string& path) {
    std::ifstream input(path);
    std::string magic;
    int version = 0, width = 0, height = 0;
    size_t count = 0;
    if (!(input >> magic >> version >> width >> height >> count)
        || magic != "BLOCKS_SYMMETRIC_PATTERN" || version != 1)
        throw std::runtime_error("invalid symmetric model header: " + path);
    SymmetricPatternValue model(width, height);
    if (count != model.weight_count()) throw std::runtime_error("invalid symmetric model weight count");
    if (!(input >> model.discount) || !std::isfinite(model.discount)
        || model.discount < 0 || model.discount >= 1)
        throw std::runtime_error("invalid symmetric model discount");
    for (float& weight : model.weights_)
        if (!(input >> weight) || !std::isfinite(weight))
            throw std::runtime_error("invalid symmetric model weights");
    std::string extra;
    if (input >> extra) throw std::runtime_error("unexpected symmetric model data");
    return model;
}
} // namespace blocks
