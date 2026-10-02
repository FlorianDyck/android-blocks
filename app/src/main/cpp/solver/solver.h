#pragma once

#include <array>
#include <atomic>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace blocks {
// Rows are at most 15 bits. No CPU-specific instructions or inference runtime.
struct Board {
    // Keep int dimensions so invalid inputs cannot wrap before validation.
    int width = 8;
    int height = 8;
    std::array<uint16_t, 15> rows{};
    Board(int width = 8, int height = 8);
    bool operator==(const Board&) const = default;

    uint16_t mask() const { return (1u << width) - 1; }

    int occupied() const;
    bool valid() const;
};

struct Shape {
    // Match Board and coordinate arithmetic without narrowing at construction.
    int width = 0;
    int height = 0;
    std::array<uint16_t, 5> rows{};
    bool operator==(const Shape&) const = default;
};

// Uniform over distinct oriented shapes, matching Android's BRICKS generator.
const std::vector<Shape>& shapes();

struct Move {
    int block = -1, x = 0, y = 0;
    bool operator==(const Move&) const = default;
};

// Requires valid board/shape dimensions and bits (solve validates its inputs).
// Out-of-board coordinates or overlaps return false; crossing lines clear together.
bool place(const Board& board, const Shape& shape, int x, int y, Board& result, int& cleared);
float android_score(const Board&);

// Residual n-tuple value model: 16 spatial 3x3 patterns, 8192 floats = 32 KiB.
// Models are tied to board dimensions. Text serialization is architecture independent.
class PatternValue {
  public:
    PatternValue(int width = 8, int height = 8);
    float value(const Board&) const;
    void update(const Board&, float target, float learning_rate);
    void save(const std::string& path) const;
    static PatternValue load(const std::string& path);

    int width() const { return width_; }

    int height() const { return height_; }

    float discount = 0.99f;

  private:
    int width_, height_;
    std::array<float, 16 * 512> weights_{};
    std::array<int, 16> indices(const Board&) const;
};

// Full-coverage 3x3 residual model. Each weight represents a spatial pattern
// orbit under dimension-preserving board symmetries.
class SymmetricPatternValue {
  public:
    SymmetricPatternValue(int width = 8, int height = 8);
    float value(const Board&) const;
    void update(const Board&, float target, float learning_rate);
    void save(const std::string& path) const;
    static SymmetricPatternValue load(const std::string& path);

    int width() const { return width_; }
    int height() const { return height_; }
    size_t weight_count() const { return weights_.size(); }
    size_t weights_bytes() const { return weights_.size() * sizeof(float); }
    size_t mapping_bytes() const { return mapping_.size() * sizeof(uint16_t); }
    size_t allocated_bytes() const {
        return sizeof(*this) + weights_.capacity() * sizeof(float)
               + mapping_.capacity() * sizeof(uint16_t);
    }
    float discount = 0.99f;

  private:
    int width_, height_;
    std::vector<uint16_t> mapping_;
    std::vector<float> weights_;
    int window_count() const { return (width_ - 2) * (height_ - 2); }
    int pattern(const Board&, int window) const;
    void validate_board(const Board&) const;
};

struct Options {
    int beam_width = 512; // Maximum width; widening uses 8, 32, ... then this width.
    bool widening = true;
    bool greedy_only = false;
    bool exhaustive = false; // Small-case oracle, still subject to deadline.
    std::chrono::microseconds budget{100000};
    const std::atomic_bool* cancel = nullptr;
    const PatternValue* model = nullptr;
    const SymmetricPatternValue* symmetric_model = nullptr;
};

struct Result {
    std::array<Move, 3> moves{};
    std::array<int, 3> clears{};
    int count = 0, cleared = 0;
    Board board;
    float score = 0;
    bool complete = false;
    bool exact = false; // All sequences searched, including proof of no full sequence.
    bool interrupted = false;
    uint64_t nodes = 0;
    double first_ms = -1, elapsed_ms = 0;
};

// Callback first receives the very greedy provisional move, then improvements.
// Runs synchronously on the caller's thread; callback time counts toward budget.
Result solve(const Board&, const std::vector<Shape>&, const Options& = {},
             const std::function<void(const Result&)>& on_improvement = {});
} // namespace blocks
