#include "solver.h"
#include <algorithm>
#include <bit>
#include <stdexcept>

namespace blocks {
Board::Board(int board_width, int board_height) : width(board_width), height(board_height) {
    if (width < 5 || width > 15 || height < 5 || height > 15)
        throw std::invalid_argument("board dimensions must be between 5 and 15");
}

bool Board::valid() const {
    if (width < 5 || width > 15 || height < 5 || height > 15)
        return false;
    for (int y = 0; y < 15; ++y)
        if (rows[y] & ~(y < height ? mask() : 0))
            return false;
    return true;
}

int Board::occupied() const {
    int occupied_cells = 0;
    for (int y = 0; y < height; ++y)
        occupied_cells += std::popcount(rows[y]);
    return occupied_cells;
}

static Shape rotate(const Shape& shape) {
    Shape rotated;
    rotated.width = shape.height;
    rotated.height = shape.width;
    for (int y = 0; y < shape.height; ++y)
        for (int x = 0; x < shape.width; ++x)
            if (shape.rows[y] & (1 << x))
                rotated.rows[x] |= 1 << (shape.height - 1 - y);
    return rotated;
}

const std::vector<Shape>& shapes() {
    static const auto all = [] {
        std::vector<Shape> result;
        // Preserve catalog order: seeded experiment streams draw by index.
        // Four rotations of each reflection cover every orientation; symmetric
        // shapes are deduplicated so they do not receive extra probability.
        auto add_orientations = [&](Shape shape) {
            for (int flip = 0; flip < 2; ++flip) {
                for (int rotation = 0; rotation < 4; ++rotation) {
                    if (std::find(result.begin(), result.end(), shape) == result.end())
                        result.push_back(shape);
                    shape = rotate(shape);
                }
                std::reverse(shape.rows.begin(), shape.rows.begin() + shape.height);
            }
        };
        for (int size = 1; size <= 5; ++size)
            add_orientations({size, 1, {uint16_t((1 << size) - 1)}});
        for (int size = 2; size <= 3; ++size) {
            Shape shape{size, size, {}};
            std::fill_n(shape.rows.begin(), size, (1 << size) - 1);
            add_orientations(shape);
        }
        // Rows run top to bottom; the rightmost bit is x = 0 (the leftmost cell).
        add_orientations({2, 2, {0b11, 0b01}});          // Small L.
        add_orientations({3, 2, {0b111, 0b001}});        // L tetromino.
        add_orientations({3, 3, {0b111, 0b001, 0b001}}); // Large L.
        add_orientations({3, 2, {0b111, 0b010}});        // T.
        add_orientations({3, 2, {0b011, 0b110}});        // Zigzag.
        return result;
    }();
    return all;
}

bool place(const Board& board, const Shape& shape, int x, int y, Board& result, int& cleared) {
    // Subtract the small shape dimensions first: caller-supplied coordinates
    // may be INT_MAX, so adding a dimension to them would overflow.
    if (x < 0 || y < 0 || x > board.width - shape.width || y > board.height - shape.height)
        return false;
    for (int row_index = 0; row_index < shape.height; ++row_index)
        if (board.rows[y + row_index] & (shape.rows[row_index] << x))
            return false;
    result = board;
    for (int row_index = 0; row_index < shape.height; ++row_index)
        result.rows[y + row_index] |= shape.rows[row_index] << x;
    // Determine full columns before clearing any rows: crossing lines clear
    // simultaneously, and their intersection must count toward both lines.
    uint16_t columns = board.mask();
    for (int row_index = 0; row_index < board.height; ++row_index)
        columns &= result.rows[row_index];
    cleared = std::popcount(columns);
    for (int row_index = 0; row_index < board.height; ++row_index) {
        if (result.rows[row_index] == board.mask()) {
            ++cleared;
            result.rows[row_index] = 0;
        } else
            result.rows[row_index] &= ~columns;
    }
    return true;
}

static bool rectangle_fits(const Board& board, int width, int height) {
    for (int y = 0; y <= board.height - height; ++y) {
        unsigned occupied = 0;
        for (int row_index = 0; row_index < height; ++row_index)
            occupied |= board.rows[y + row_index];
        unsigned free = ~occupied & board.mask();
        // A start bit survives only if all width consecutive cells are free
        // in every row of this rectangle's vertical span.
        unsigned starts = free;
        for (int x = 1; x < width; ++x)
            starts &= free >> x;
        if (starts)
            return true;
    }
    return false;
}

float android_score(const Board& board) {
    static constexpr int empty_cell_weights[] = {3, 2, 1, -2, -21},
                         occupied_cell_weights[] = {0, 0, 0, -1, -5};
    int score = 0;
    const unsigned mask = board.mask();
    for (int y = 0; y < board.height; ++y) {
        unsigned row = board.rows[y];
        // Each mask marks cells whose occupancy differs from that neighbor.
        // Treat the board boundary as occupied, matching the Android evaluator.
        unsigned differs_left = row ^ ((row << 1) | 1u);
        unsigned differs_right = row ^ ((row >> 1) | (1u << (board.width - 1)));
        unsigned differs_above = row ^ (y ? board.rows[y - 1] : mask);
        unsigned differs_below = row ^ (y + 1 < board.height ? board.rows[y + 1] : mask);
        unsigned all_four = differs_left & differs_right & differs_above & differs_below;
        unsigned two_or_three = ~all_four
                                & ((differs_left & (differs_right | differs_above | differs_below))
                                   | (differs_right & (differs_above | differs_below))
                                   | (differs_above & differs_below));
        unsigned odd_count = differs_left ^ differs_right ^ differs_above ^ differs_below;
        // Combine count parity with the pair/four masks to classify 0..4
        // differing neighbors for every cell at once.
        unsigned grades[] = {~all_four & ~two_or_three & ~odd_count, ~two_or_three & odd_count,
                             two_or_three & ~odd_count, two_or_three & odd_count, all_four};
        for (int grade = 0; grade < 5; ++grade)
            score += empty_cell_weights[grade] * std::popcount(grades[grade] & ~row & mask)
                     + occupied_cell_weights[grade] * std::popcount(grades[grade] & row & mask);
    }
    if (rectangle_fits(board, 3, 3))
        score += 20;
    if (rectangle_fits(board, 5, 1))
        score += 10;
    if (rectangle_fits(board, 1, 5))
        score += 10;
    return float(score);
}
} // namespace blocks
