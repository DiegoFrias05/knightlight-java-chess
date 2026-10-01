package chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A small, independent chess engine: iterative deepening, negamax and alpha-beta. */
public final class Engine {
    public static final int MATE_SCORE = 100_000;
    private static final int INFINITY = 1_000_000;
    private static final int MAX_QUIESCENCE_PLY = 8;

    /** Score is in centipawns from the root side's perspective; depth counts full plies. */
    public record SearchResult(Move move, int score, int depth, long nodes, long elapsedMillis) { }

    public SearchResult search(Position position, int maxDepth, long timeLimitMillis) {
        return search(position, maxDepth, timeLimitMillis, Map.of(position.repetitionKey(), 1));
    }

    /** All search state is local, so even a cancelled old search cannot corrupt a new one. */
    public SearchResult search(Position position, int maxDepth, long timeLimitMillis,
                               Map<String, Integer> repetitions) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(repetitions, "repetitions");
        if (maxDepth < 1 || maxDepth > 12) {
            throw new IllegalArgumentException("Search depth must be between 1 and 12.");
        }
        if (timeLimitMillis < 1 || timeLimitMillis > 60_000) {
            throw new IllegalArgumentException("Time budget must be between 1 and 60000 ms.");
        }
        Search search = new Search(timeLimitMillis, repetitions);
        search.counts.putIfAbsent(position.repetitionKey(), 1);
        List<Move> legal = position.legalMoves();
        if (legal.isEmpty()) {
            return search.result(null, position.isInCheck(position.whiteToMove()) ? -MATE_SCORE : 0, 0);
        }
        if (search.isDraw(position)) {
            return search.result(null, 0, 0);
        }

        // Always keep a legal fallback if the budget expires before depth one finishes.
        Move best = legal.get(0);
        int score = evaluate(position);
        int completedDepth = 0;
        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                search.checkBudget();
                List<Move> ordered = ordered(position, legal, best);
                int alpha = -INFINITY;
                Move iterationBest = best;
                for (Move move : ordered) {
                    search.checkBudget();
                    Position child = position.after(move);
                    search.push(child);
                    int value;
                    try {
                        value = -search.negamax(child, depth - 1, -INFINITY, -alpha, 1);
                    } finally {
                        search.pop(child);
                    }
                    if (value > alpha) {
                        alpha = value;
                        iterationBest = move;
                    }
                }
                // Never replace the result with a partially completed iteration.
                best = iterationBest;
                score = alpha;
                completedDepth = depth;
                if (Math.abs(score) >= MATE_SCORE - 128) break;
            } catch (SearchStopped ignored) {
                break;
            }
        }
        return search.result(best, score, completedDepth);
    }

    /** Material plus simple, explainable positional bonuses. No trained model or opening book. */
    public static int evaluate(Position position) {
        int nonPawnMaterial = 0;
        for (int square = 0; square < 64; square++) {
            char piece = Character.toLowerCase(position.pieceAt(square));
            if (piece != 'p' && piece != 'k') nonPawnMaterial += value(piece);
        }
        boolean endgame = nonPawnMaterial <= 2600;
        int whiteScore = 0;
        int whiteBishops = 0;
        int blackBishops = 0;
        for (int square = 0; square < 64; square++) {
            char piece = position.pieceAt(square);
            if (piece == '.') continue;
            boolean white = Character.isUpperCase(piece);
            int file = square % 8;
            int rank = white ? square / 8 : 7 - square / 8;
            int center = 7 - (Math.abs(2 * file - 7) + Math.abs(2 * rank - 7)) / 2;
            char type = Character.toLowerCase(piece);
            int bonus = switch (type) {
                case 'p' -> rank * 9 + center * 3;
                case 'n' -> center * 12 - (rank == 0 ? 18 : 0);
                case 'b' -> center * 7 + (rank > 0 ? 10 : 0);
                case 'r' -> rank == 6 ? 22 : 0;
                case 'q' -> center * 3;
                case 'k' -> endgame ? center * 10 : -rank * 12 +
                        (rank == 0 && (file == 6 || file == 2) ? 35 : 0);
                default -> 0;
            };
            if (type == 'b') {
                if (white) whiteBishops++; else blackBishops++;
            }
            whiteScore += (white ? 1 : -1) * (value(type) + bonus);
        }
        if (whiteBishops >= 2) whiteScore += 25;
        if (blackBishops >= 2) whiteScore -= 25;
        return position.whiteToMove() ? whiteScore : -whiteScore;
    }

    private static int value(char piece) {
        return switch (Character.toLowerCase(piece)) {
            case 'p' -> 100;
            case 'n' -> 320;
            case 'b' -> 330;
            case 'r' -> 500;
            case 'q' -> 900;
            default -> 0;
        };
    }

    private static boolean isCapture(Position position, Move move) {
        return position.pieceAt(move.to()) != '.' ||
                (Character.toLowerCase(position.pieceAt(move.from())) == 'p'
                        && move.from() % 8 != move.to() % 8);
    }

    private static List<Move> ordered(Position position, List<Move> moves, Move preferred) {
        List<Move> sorted = new ArrayList<>(moves);
        sorted.sort(Comparator.comparingInt((Move move) -> {
            if (move.equals(preferred)) return 1_000_000;
            int score = move.promotion() == '\0' ? 0 : 20_000 + value(move.promotion());
            if (isCapture(position, move)) {
                int victim = value(position.pieceAt(move.to()));
                if (victim == 0) victim = 100; // en passant
                score += 10_000 + 10 * victim - value(position.pieceAt(move.from()));
            }
            return score;
        }).reversed());
        return sorted;
    }

    private static final class Search {
        private final long started = System.nanoTime();
        private final long deadline;
        private final Map<String, Integer> counts;
        private long nodes;

        Search(long timeLimitMillis, Map<String, Integer> repetitions) {
            deadline = started + timeLimitMillis * 1_000_000;
            counts = new HashMap<>(repetitions);
        }

        SearchResult result(Move move, int score, int depth) {
            return new SearchResult(move, score, depth, nodes,
                    (System.nanoTime() - started) / 1_000_000);
        }

        void checkBudget() {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) {
                throw SearchStopped.INSTANCE;
            }
        }

        void visit() {
            nodes++;
            // Check at every node: responsiveness matters more here than peak playing strength.
            checkBudget();
        }

        void push(Position position) {
            counts.merge(position.repetitionKey(), 1, Integer::sum);
        }

        void pop(Position position) {
            String key = position.repetitionKey();
            int remaining = counts.get(key) - 1;
            if (remaining == 0) counts.remove(key); else counts.put(key, remaining);
        }

        boolean isDraw(Position position) {
            return position.halfmoveClock() >= 100 || position.isInsufficientMaterial()
                    || counts.getOrDefault(position.repetitionKey(), 0) >= 3;
        }

        int negamax(Position position, int depth, int alpha, int beta, int ply) {
            if (depth == 0) return quiescence(position, alpha, beta, ply, 0);
            visit();
            List<Move> moves = position.legalMoves();
            if (moves.isEmpty()) {
                return position.isInCheck(position.whiteToMove()) ? -MATE_SCORE + ply : 0;
            }
            if (isDraw(position)) return 0;
            for (Move move : ordered(position, moves, null)) {
                Position child = position.after(move);
                push(child);
                int score;
                try {
                    score = -negamax(child, depth - 1, -beta, -alpha, ply + 1);
                } finally {
                    pop(child);
                }
                if (score >= beta) return score;
                alpha = Math.max(alpha, score);
            }
            return alpha;
        }

        /** Extend captures/promotions; in check, search every legal evasion instead of standing pat. */
        int quiescence(Position position, int alpha, int beta, int ply, int extension) {
            visit();
            List<Move> moves = position.legalMoves();
            boolean inCheck = position.isInCheck(position.whiteToMove());
            if (moves.isEmpty()) return inCheck ? -MATE_SCORE + ply : 0;
            if (isDraw(position)) return 0;
            int standPat = evaluate(position);
            // A finite cap also bounds long checking sequences in this educational engine.
            if (extension >= MAX_QUIESCENCE_PLY) return standPat;
            if (!inCheck) {
                if (standPat >= beta) return standPat;
                alpha = Math.max(alpha, standPat);
            }
            for (Move move : ordered(position, moves, null)) {
                if (!inCheck && !isCapture(position, move) && move.promotion() == '\0') continue;
                Position child = position.after(move);
                push(child);
                int score;
                try {
                    score = -quiescence(child, -beta, -alpha, ply + 1, extension + 1);
                } finally {
                    pop(child);
                }
                if (score >= beta) return score;
                alpha = Math.max(alpha, score);
            }
            return alpha;
        }
    }

    /** One stackless internal signal; catching it keeps the last fully searched result. */
    private static final class SearchStopped extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private static final SearchStopped INSTANCE = new SearchStopped();
        private SearchStopped() { super(null, null, false, false); }
    }
}
