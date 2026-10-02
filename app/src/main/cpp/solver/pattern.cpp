#include "solver.h"
#include <cmath>
#include <fstream>
#include <iomanip>
#include <limits>
#include <stdexcept>

namespace blocks {
PatternValue::PatternValue(int width, int height) : width_(width), height_(height) { Board validate(width, height); }
std::array<int, 16> PatternValue::indices(const Board& board) const {
    if (board.width != width_ || board.height != height_) throw std::invalid_argument("model dimensions differ from board");
    std::array<int, 16> pattern_indices{};
    // Sample a 4x4 grid of 3x3 windows, including the board edges. Each
    // window's nine occupancy bits select one of its own 512 learned weights.
    for (int grid_y = 0; grid_y < 4; ++grid_y) {
        for (int grid_x = 0; grid_x < 4; ++grid_x) {
            int x = grid_x * (width_ - 3) / 3, y = grid_y * (height_ - 3) / 3;
            int pattern = grid_y * 4 + grid_x;
            pattern_indices[pattern] = pattern * 512 + ((board.rows[y] >> x) & 7)
                   + (((board.rows[y + 1] >> x) & 7) << 3)
                   + (((board.rows[y + 2] >> x) & 7) << 6);
        }
    }
    return pattern_indices;
}
float PatternValue::value(const Board& board) const {
    float estimate = android_score(board) / 20.f;
    for (int index : indices(board)) estimate += weights_[index];
    return estimate;
}
void PatternValue::update(const Board& board, float target, float learning_rate) {
    if (!std::isfinite(target) || !std::isfinite(learning_rate) || learning_rate <= 0 || learning_rate > 1)
        throw std::invalid_argument("invalid training target or learning rate");
    // Sixteen active weights contribute to the value, so share the TD error
    // equally: their combined update is learning_rate * (target - estimate).
    float delta = learning_rate * (target - value(board)) / 16.f;
    for (int index : indices(board)) weights_[index] += delta;
}
void PatternValue::save(const std::string& path) const {
    std::ofstream output(path);
    output << "BLOCKS_PATTERN 1 " << width_ << ' ' << height_ << '\n';
    output << std::setprecision(std::numeric_limits<float>::max_digits10) << discount << '\n';
    for (float weight : weights_) output << weight << '\n';
    output.flush();
    if (!output) throw std::runtime_error("could not save model: " + path);
}
PatternValue PatternValue::load(const std::string& path) {
    std::ifstream input(path);
    std::string magic; int version = 0, width = 0, height = 0;
    if (!(input >> magic >> version >> width >> height) || magic != "BLOCKS_PATTERN" || version != 1)
        throw std::runtime_error("invalid model header: " + path);
    PatternValue model(width, height);
    if (!(input >> model.discount) || !std::isfinite(model.discount) || model.discount < 0 || model.discount >= 1)
        throw std::runtime_error("invalid model discount");
    for (auto& weight : model.weights_)
        if (!(input >> weight) || !std::isfinite(weight)) throw std::runtime_error("invalid model weights");
    std::string extra;
    if (input >> extra) throw std::runtime_error("unexpected model data");
    return model;
}
} // namespace blocks
