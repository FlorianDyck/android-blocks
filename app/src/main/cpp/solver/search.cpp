#include "solver.h"
#include <algorithm>
#include <set>
#include <stdexcept>
#include <unordered_map>

namespace blocks {
namespace {
using Clock = std::chrono::steady_clock;
struct SearchNode {
    Result result;
    unsigned remaining = 0;
    float intermediate_score = 0;
};

// Prefer more placed blocks, then the objective, early clears, intermediate
// board quality, and finally a stable order of block indices and coordinates.
bool better(const SearchNode& candidate, const SearchNode& incumbent) {
    const auto& left = candidate.result;
    const auto& right = incumbent.result;
    if (left.count != right.count) return left.count > right.count;
    if (left.score != right.score) return left.score > right.score;
    if (left.clears != right.clears) return left.clears > right.clears;
    if (candidate.intermediate_score != incumbent.intermediate_score)
        return candidate.intermediate_score > incumbent.intermediate_score;
    for (int index = 0; index < left.count; ++index) {
        const auto& left_move = left.moves[index];
        const auto& right_move = right.moves[index];
        if (left_move.block != right_move.block) return left_move.block < right_move.block;
        if (left_move.y != right_move.y) return left_move.y < right_move.y;
        if (left_move.x != right_move.x) return left_move.x < right_move.x;
    }
    return false;
}

struct StateKey {
    std::array<uint16_t, 15> rows;
    unsigned remaining;
    bool operator==(const StateKey&) const = default;
};
struct StateHash {
    size_t operator()(const StateKey& state) const {
        size_t hash = state.remaining;
        for (auto row : state.rows) hash = (hash ^ row) * size_t(1099511628211ULL);
        return hash;
    }
};
StateKey state_key(const SearchNode& node) { return {node.result.board.rows, node.remaining}; }
struct BestFirst {
    bool operator()(const SearchNode& left, const SearchNode& right) const { return better(left, right); }
};

// Paths with the same board and unused blocks have the same possible futures.
// Keep only their best prefix, then limit storage to the best beam_width states.
class Frontier {
    std::set<SearchNode, BestFirst> nodes_;
    std::unordered_map<StateKey, decltype(nodes_)::iterator, StateHash> lookup_;
    size_t width_;
public:
    explicit Frontier(size_t width) : width_(width) { lookup_.reserve(width * 2); }
    void offer(const SearchNode& node) {
        auto state = state_key(node);
        auto existing = lookup_.find(state);
        if (existing != lookup_.end()) {
            if (!better(node, *existing->second)) return;
            nodes_.erase(existing->second);
            lookup_.erase(existing);
        } else if (nodes_.size() == width_ && !better(node, *nodes_.rbegin())) {
            return;
        }
        auto [position, inserted] = nodes_.insert(node);
        if (!inserted) return;
        lookup_.emplace(state, position);
        if (nodes_.size() > width_) {
            auto worst = std::prev(nodes_.end());
            lookup_.erase(state_key(*worst));
            nodes_.erase(worst);
        }
    }
    std::vector<SearchNode> ordered_nodes() const { return {nodes_.begin(), nodes_.end()}; }
};

void validate_shape(const Shape& shape) {
    if (shape.width < 1 || shape.width > 5 || shape.height < 1 || shape.height > 5)
        throw std::invalid_argument("invalid shape size");
    unsigned occupied = 0;
    for (int y = 0; y < 5; ++y) {
        const unsigned allowed = y < shape.height ? ((1u << shape.width) - 1) : 0;
        if (shape.rows[y] & ~allowed) throw std::invalid_argument("invalid shape bits");
        occupied |= shape.rows[y];
    }
    if (!occupied) throw std::invalid_argument("empty shape");
}

void validate_search(const Board& board, const std::vector<Shape>& hand, const Options& options) {
    if (!board.valid() || hand.size() > 3 || options.beam_width < 1 || options.beam_width > 65536
        || options.budget.count() < 0) throw std::invalid_argument("invalid search input");
    for (const auto& shape : hand) validate_shape(shape);
    if (options.model && options.symmetric_model)
        throw std::invalid_argument("choose one learned model");
    if (options.model && (options.model->width() != board.width || options.model->height() != board.height))
        throw std::invalid_argument("model dimensions differ from board");
    if (options.symmetric_model && (options.symmetric_model->width() != board.width
                                    || options.symmetric_model->height() != board.height))
        throw std::invalid_argument("model dimensions differ from board");
}

class Search {
public:
    Search(const Board& initial, const std::vector<Shape>& hand, const Options& options,
           const std::function<void(const Result&)>& callback)
        : hand_(hand), options_(options), callback_(callback), start_(Clock::now()) {
        root_.result.board = initial;
        root_.remaining = (1u << hand.size()) - 1;
        root_.result.complete = hand.empty();
        root_.result.score = android_score(initial);
        best_ = root_;
    }

    Result run() {
        run_greedy();
        bool exact = hand_.empty();
        if (!options_.greedy_only && !should_stop()) {
            if (options_.exhaustive) {
                run_exhaustive(root_);
                exact = !stopped_;
            } else {
                run_widening();
            }
        }
        auto result = report();
        result.exact = exact;
        result.interrupted = stopped_;
        return result;
    }

private:
    const std::vector<Shape>& hand_;
    const Options& options_;
    const std::function<void(const Result&)>& callback_;
    Clock::time_point start_;
    SearchNode root_, best_;
    uint64_t visited_ = 0;
    bool stopped_ = false;
    double first_ms_ = -1;

    double elapsed_ms() const {
        return std::chrono::duration<double, std::milli>(Clock::now() - start_).count();
    }
    bool should_stop() {
        if (!stopped_) {
            // Compare elapsed durations rather than adding an arbitrary budget
            // to a time point, which could overflow for very large budgets.
            stopped_ = (options_.cancel && options_.cancel->load(std::memory_order_relaxed))
                || std::chrono::duration_cast<std::chrono::microseconds>(Clock::now() - start_) >= options_.budget;
        }
        return stopped_;
    }
    Result report() const {
        auto result = best_.result;
        result.first_ms = first_ms_;
        result.elapsed_ms = elapsed_ms();
        result.nodes = visited_;
        return result;
    }
    void publish(const SearchNode& candidate) {
        if (!better(candidate, best_)) return;
        best_ = candidate;
        if (first_ms_ < 0) first_ms_ = elapsed_ms();
        if (callback_) callback_(report());
    }
    bool duplicates_unused_shape(const SearchNode& parent, size_t block) const {
        // Identical blocks are interchangeable; use the lowest unused index so
        // deterministic move ordering is preserved without searching permutations.
        for (size_t previous = 0; previous < block; ++previous)
            if ((parent.remaining & (1u << previous)) && hand_[previous] == hand_[block]) return true;
        return false;
    }
    void record_move(SearchNode& child, int block, int x, int y, int cleared) const {
        const int depth = child.result.count++;
        child.result.moves[depth] = {block, x, y};
        child.result.clears[depth] = cleared;
        child.result.cleared += cleared;
        child.remaining &= ~(1u << block);
        child.result.complete = child.remaining == 0;
        const float heuristic = android_score(child.result.board);
        child.intermediate_score += heuristic;
        // The learned value describes a hand boundary. At intermediate depths,
        // use the cheaper heuristic in the same approximate reward units.
        if (options_.model || options_.symmetric_model) {
            const float discount = options_.model ? options_.model->discount
                                                  : options_.symmetric_model->discount;
            if (child.result.complete) {
                const float value = options_.model ? options_.model->value(child.result.board)
                                                   : options_.symmetric_model->value(child.result.board);
                child.result.score = child.result.cleared + discount * value;
            } else {
                child.result.score = child.result.cleared + heuristic / 20.f;
            }
        } else {
            child.result.score = heuristic;
        }
    }
    template<class Accept>
    void visit_placements(const SearchNode& parent, size_t block, Accept&& accept) {
        const auto& shape = hand_[block];
        const auto& board = parent.result.board;
        for (int y = 0; y <= board.height - shape.height; ++y) {
            for (int x = 0; x <= board.width - shape.width; ++x) {
                // Count illegal attempts too, keeping crowded boards cancellable.
                if (stopped_ || ((visited_++ & 31) == 0 && should_stop())) return;
                SearchNode child = parent;
                int cleared = 0;
                if (!place(board, shape, x, y, child.result.board, cleared)) continue;
                record_move(child, int(block), x, y, cleared);
                accept(child);
            }
        }
    }
    template<class Accept>
    void visit_children(const SearchNode& parent, Accept&& accept) {
        for (size_t block = 0; block < hand_.size() && !stopped_; ++block) {
            if (!(parent.remaining & (1u << block)) || duplicates_unused_shape(parent, block)) continue;
            visit_placements(parent, block, accept);
        }
    }
    void run_greedy() {
        // Publish a quick provisional hint, then complete it greedily. Later
        // searches restart at the root and are not constrained by this prefix.
        auto greedy = root_;
        for (size_t depth = 0; depth < hand_.size() && !should_stop(); ++depth) {
            auto next = greedy;
            visit_children(greedy, [&](const SearchNode& child) { if (better(child, next)) next = child; });
            if (next.result.count == greedy.result.count) break;
            greedy = next;
            publish(greedy);
        }
    }
    void run_exhaustive(const SearchNode& node) {
        visit_children(node, [&](const SearchNode& child) {
            publish(child);
            if (child.remaining && !should_stop()) run_exhaustive(child);
        });
    }
    void run_beam(int width) {
        std::vector<SearchNode> current{root_};
        for (size_t depth = 0; depth < hand_.size() && !should_stop(); ++depth) {
            Frontier next(width);
            for (const auto& node : current) {
                visit_children(node, [&](const SearchNode& child) {
                    publish(child);
                    if (child.remaining) next.offer(child);
                });
                if (stopped_) break;
            }
            current = next.ordered_nodes();
            if (current.empty()) break;
        }
    }
    void run_widening() {
        int width = options_.widening ? std::min(8, options_.beam_width) : options_.beam_width;
        while (!should_stop()) {
            run_beam(width);
            if (width == options_.beam_width) break;
            width = std::min(width * 4, options_.beam_width);
        }
    }
};
} // namespace

Result solve(const Board& initial, const std::vector<Shape>& hand, const Options& options,
             const std::function<void(const Result&)>& callback) {
    validate_search(initial, hand, options);
    return Search(initial, hand, options, callback).run();
}
} // namespace blocks
