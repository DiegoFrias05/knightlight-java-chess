package chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Game history and automatic draw adjudication, kept separate from board rules. */
public final class Game {
    public enum Result {
        ONGOING, WHITE_WINS, BLACK_WINS, STALEMATE,
        THREEFOLD_REPETITION, FIFTY_MOVE_RULE, INSUFFICIENT_MATERIAL
    }

    private final List<Position> positions = new ArrayList<>();
    private final List<Move> moves = new ArrayList<>();
    private final Map<String, Integer> repetitions = new HashMap<>();

    public Game() {
        this(Position.initial());
    }

    /** A loaded position begins a new history; FEN does not encode earlier repetitions. */
    public Game(Position initial) {
        positions.add(Objects.requireNonNull(initial, "Initial position cannot be null."));
        repetitions.put(initial.repetitionKey(), 1);
    }

    public Position position() {
        return positions.get(positions.size() - 1);
    }

    public List<Move> moves() {
        return List.copyOf(moves);
    }

    public Map<String, Integer> repetitionCounts() {
        return Map.copyOf(repetitions);
    }

    public void play(Move move) {
        if (result() != Result.ONGOING) {
            throw new IllegalStateException("This game is over. Undo a move or start a new game.");
        }
        Position next = position().play(move);
        moves.add(move);
        positions.add(next);
        repetitions.merge(next.repetitionKey(), 1, Integer::sum);
    }

    /** Removes one ply and its repetition entry, including after a terminal result. */
    public boolean undo() {
        if (moves.isEmpty()) return false;
        String key = position().repetitionKey();
        repetitions.computeIfPresent(key, (ignored, count) -> count == 1 ? null : count - 1);
        positions.remove(positions.size() - 1);
        moves.remove(moves.size() - 1);
        return true;
    }

    public Result result() {
        Position current = position();
        if (current.legalMoves().isEmpty()) {
            if (!current.isInCheck(current.whiteToMove())) return Result.STALEMATE;
            return current.whiteToMove() ? Result.BLACK_WINS : Result.WHITE_WINS;
        }
        if (current.isInsufficientMaterial()) return Result.INSUFFICIENT_MATERIAL;
        if (current.halfmoveClock() >= 100) return Result.FIFTY_MOVE_RULE;
        if (repetitions.getOrDefault(current.repetitionKey(), 0) >= 3) return Result.THREEFOLD_REPETITION;
        return Result.ONGOING;
    }

    public String statusText() {
        return switch (result()) {
            case ONGOING -> (position().whiteToMove() ? "White" : "Black") + " to move"
                    + (position().isInCheck(position().whiteToMove()) ? " — check!" : ".");
            case WHITE_WINS -> "Checkmate — White wins!";
            case BLACK_WINS -> "Checkmate — Black wins!";
            case STALEMATE -> "Draw by stalemate.";
            case THREEFOLD_REPETITION -> "Draw by threefold repetition.";
            case FIFTY_MOVE_RULE -> "Draw by the fifty-move rule.";
            case INSUFFICIENT_MATERIAL -> "Draw by insufficient material.";
        };
    }
}
