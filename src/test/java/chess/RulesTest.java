package chess;

import java.util.List;
import java.util.Map;

/** Dependency-free regression suite. A failed check throws even without java -ea. */
public final class RulesTest {
    private static int checks;

    private RulesTest() { }

    public static void main(String[] args) {
        long start = System.nanoTime();
        coordinatesAndFen();
        perftReferencePositions();
        immutableAndLegalMoves();
        castling();
        enPassant();
        promotion();
        resultsAndDraws();
        repetitionAndUndo();
        System.out.printf("RulesTest: %d checks passed (%.2f s).%n", checks,
                (System.nanoTime() - start) / 1_000_000_000.0);
    }

    private static void coordinatesAndFen() {
        for (int square = 0; square < 64; square++) {
            equal(square, Position.square(Position.squareName(square)), "square round trip");
        }
        equal(0, Position.square("a1"), "a1 index");
        equal(63, Position.square("h8"), "h8 index");
        equal("e2e4", Move.fromUci(" E2E4 ").uci(), "coordinate move parsing");
        equal("a7a8n", new Move(48, 56, 'N').uci(), "promotion normalization");
        rejects(() -> Move.fromUci("e2-e4"), "malformed move");
        rejects(() -> Move.fromUci("a7a8k"), "king promotion");
        rejects(() -> Position.square("i1"), "bad file");
        rejects(() -> new Move(-1, 64), "bad indices");
        Position initial = Position.initial();
        equal(Position.INITIAL_FEN, initial.toFen(), "initial FEN round trip");
        equal('R', initial.pieceAt(0), "white rook");
        equal('k', initial.pieceAt(60), "black king");
        equal('.', initial.pieceAt(28), "empty square");
        Position e4 = initial.play(Move.fromUci("e2e4"));
        equal("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", e4.toFen(), "double pawn move FEN");
        equal(e4.toFen(), Position.fromFen(e4.toFen()).toFen(), "noninitial FEN round trip");
        Position reply = e4.play(Move.fromUci("g8f6"));
        equal(1, reply.halfmoveClock(), "quiet move clock");
        equal(2, reply.fullmoveNumber(), "fullmove increments after Black");
        check(reply.whiteToMove(), "side to move toggles");
        String[] malformed = {
            "", "8/8/8/8/8/8/8/8 w - - 0 1", // Missing kings.
            "4k3/8/8/8/8/8/8/4KK2 w - - 0 1",
            "4k3/8/8/8/8/8/8/4K3 w - - 0", // Missing field.
            "4k3/8/8/8/8/8/8/4K4 w - - 0 1",
            "4k3/8/8/8/8/8/8/4XK2 w - - 0 1",
            "4k3/8/8/8/8/8/8/4K3 x - - 0 1",
            "4k3/8/8/8/8/8/8/4K3 w KK - 0 1",
            "4k3/8/8/8/8/8/8/4K3 w - e3 0 1",
            "4k3/8/8/8/8/8/8/4K3 b - z3 0 1",
            "4k3/8/8/8/8/8/8/4K3 w - - -1 1",
            "4k3/8/8/8/8/8/8/4K3 w - - 0 0",
            "4k3/8/8/8/8/8/8/4K3 w - - 999999999999999 1"
        };
        for (String fen : malformed) rejects(() -> Position.fromFen(fen), "reject invalid FEN");
    }

    private static void perftReferencePositions() {
        // Public reference counts, not counts calculated by this implementation:
        // https://www.chessprogramming.org/Perft_Results
        perftChecks("initial", Position.INITIAL_FEN, 20, 400, 8902, 197281);
        perftChecks("Kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                48, 2039, 97862);
        perftChecks("rook/pawn endgame", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
                14, 191, 2812, 43238);
        perftChecks("promotion and castling", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
                6, 264, 9467);
        perftChecks("promotion and castling mirrored", "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
                6, 264, 9467);
        perftChecks("checks and underpromotion", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
                44, 1486, 62379);
        perftChecks("middlegame", "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
                46, 2079, 89890);
    }

    private static void perftChecks(String name, String fen, long... counts) {
        Position position = Position.fromFen(fen);
        for (int depth = 1; depth <= counts.length; depth++) {
            equal(counts[depth - 1], perft(position, depth), name + " perft " + depth);
        }
    }

    public static long perft(Position position, int depth) {
        if (depth == 0) return 1;
        List<Move> moves = position.legalMoves();
        if (depth == 1) return moves.size();
        long count = 0;
        for (Move move : moves) count += perft(position.after(move), depth - 1);
        return count;
    }

    private static void immutableAndLegalMoves() {
        Position start = Position.initial();
        start.play(Move.fromUci("e2e4"));
        equal(Position.INITIAL_FEN, start.toFen(), "playing does not mutate original");
        rejects(() -> start.play(Move.fromUci("e2e5")), "pawn cannot jump three squares");
        rejects(() -> start.play(Move.fromUci("e7e5")), "cannot move other side");
        rejects(() -> start.play(Move.fromUci("a1a3")), "rook cannot jump pawn");
        rejects(() -> start.play(Move.fromUci("e2e4q")), "promotion elsewhere is illegal");
        expectUnsupported(() -> start.legalMoves().clear(), "legal move list immutable");
        Position pin = Position.fromFen("k3r3/8/8/8/8/8/4R3/4K3 w - - 0 1");
        check(!contains(pin, "e2f2"), "pinned rook cannot expose king");
        check(contains(pin, "e2e8"), "pinned rook can capture pinner");
        Position adjacent = Position.fromFen("8/8/8/8/4k3/8/4K3/8 w - - 0 1");
        check(!contains(adjacent, "e2e3"), "kings cannot become adjacent");
        Position checked = Position.fromFen("k3r3/8/8/8/8/8/R7/4K3 w - - 0 1");
        check(checked.isInCheck(true), "detect rook check");
        check(!contains(checked, "a2a3"), "must answer check");
        check(contains(checked, "a2e2"), "can block check");
    }

    private static void castling() {
        Position position = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        check(contains(position, "e1g1"), "White king-side castle generated");
        check(contains(position, "e1c1"), "White queen-side castle generated");
        Position castled = position.play(Move.fromUci("e1g1"));
        equal('K', castled.pieceAt(Position.square("g1")), "castled king destination");
        equal('R', castled.pieceAt(Position.square("f1")), "castled rook destination");
        equal('.', castled.pieceAt(Position.square("h1")), "castled rook origin cleared");
        equal("kq", castled.toFen().split(" ")[2], "king movement removes both rights");
        Position black = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1");
        check(contains(black, "e8g8"), "Black king-side castle generated");
        Position queenside = black.play(Move.fromUci("e8c8"));
        equal('k', queenside.pieceAt(Position.square("c8")), "Black queen-side king");
        equal('r', queenside.pieceAt(Position.square("d8")), "Black queen-side rook");
        Position attackedTransit = Position.fromFen("k4r2/8/8/8/8/8/8/4K2R w K - 0 1");
        check(!contains(attackedTransit, "e1g1"), "cannot castle through check");
        Position attackedStart = Position.fromFen("k3r3/8/8/8/8/8/8/4K2R w K - 0 1");
        check(!contains(attackedStart, "e1g1"), "cannot castle out of check");
        Position attackedEnd = Position.fromFen("k5r1/8/8/8/8/8/8/4K2R w K - 0 1");
        check(!contains(attackedEnd, "e1g1"), "cannot castle into check");
        Position attackedRook = Position.fromFen("k6r/8/8/8/8/8/8/4K2R w K - 0 1");
        check(contains(attackedRook, "e1g1"), "attacked rook does not prohibit castling");
        Position missingRook = Position.fromFen("k7/8/8/8/8/8/8/4K3 w K - 0 1");
        check(!contains(missingRook, "e1g1"), "rights alone cannot create rook");
        Position blocked = Position.fromFen("k7/8/8/8/8/8/8/RN2K3 w Q - 0 1");
        check(!contains(blocked, "e1c1"), "queen-side b-file must be empty");
        Position rookCapture = position.play(Move.fromUci("a1a8"));
        equal("Kk", rookCapture.toFen().split(" ")[2], "moving and captured rooks lose rights");
        Position movedBack = position.play(Move.fromUci("h1h2")).play(Move.fromUci("h8h7"))
                .play(Move.fromUci("h2h1"));
        equal("Qq", movedBack.toFen().split(" ")[2], "returning rooks do not restore rights");
    }

    private static void enPassant() {
        Position position = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        check(contains(position, "e5d6"), "en passant generated");
        Position captured = position.play(Move.fromUci("e5d6"));
        equal('.', captured.pieceAt(Position.square("d5")), "en passant removes passed pawn");
        equal('P', captured.pieceAt(Position.square("d6")), "en passant pawn destination");
        equal(0, captured.halfmoveClock(), "en passant resets clock");
        equal("-", captured.toFen().split(" ")[3], "en passant expires");
        Position expired = position.play(Move.fromUci("e1f1")).play(Move.fromUci("e8f8"));
        check(!contains(expired, "e5d6"), "cannot capture en passant a turn later");
        Position black = Position.fromFen("4k3/8/8/8/3pP3/8/8/4K3 b - e3 0 1");
        equal('p', black.play(Move.fromUci("d4e3")).pieceAt(Position.square("e3")), "Black en passant");
        Position pinned = Position.fromFen("k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        check(!contains(pinned, "e5d6"), "en passant cannot expose file check");
        Position horizontalPin = Position.fromFen("7k/8/8/r4pPK/8/8/8/8 w - f6 0 1");
        check(!contains(horizontalPin, "g5f6"), "en passant cannot expose rank check");
        Position evasion = Position.fromFen("4k3/8/8/3pP3/4K3/8/8/8 w - d6 0 1");
        check(evasion.isInCheck(true), "pawn is checking king");
        check(contains(evasion, "e5d6"), "en passant may remove checking pawn");
        Position phantom = Position.fromFen("4k3/8/8/4P3/8/8/8/4K3 w - d6 0 1");
        check(!contains(phantom, "e5d6"), "target alone cannot create capture");
    }

    private static void promotion() {
        Position position = Position.fromFen("1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        for (char piece : new char[] {'q', 'r', 'b', 'n'}) {
            String quiet = "a7a8" + piece;
            String capture = "a7b8" + piece;
            check(contains(position, quiet), "quiet promotion to " + piece);
            check(contains(position, capture), "capture promotion to " + piece);
            equal(Character.toUpperCase(piece), position.play(Move.fromUci(capture)).pieceAt(57), "promoted piece type");
        }
        check(!contains(position, "a7a8"), "promotion type is mandatory");
        Position black = Position.fromFen("4k3/8/8/8/8/8/p7/1R2K3 b - - 0 1");
        for (char piece : new char[] {'q', 'r', 'b', 'n'}) {
            equal(piece, black.play(Move.fromUci("a2b1" + piece)).pieceAt(1), "Black promotion type");
        }
    }

    private static void resultsAndDraws() {
        Game game = new Game();
        play(game, "f2f3", "e7e5", "g2g4", "d8h4");
        equal(Game.Result.BLACK_WINS, game.result(), "Fool's Mate");
        check(game.statusText().contains("Black wins"), "mate status names winner");
        expectIllegalState(() -> game.play(Move.fromUci("a2a3")), "terminal games refuse moves");
        check(game.undo(), "can undo checkmate");
        equal(Game.Result.ONGOING, game.result(), "undo reopens game");
        equal(Game.Result.WHITE_WINS, new Game(Position.fromFen("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1")).result(), "White checkmate");
        equal(Game.Result.STALEMATE, new Game(Position.fromFen("k7/2Q5/2K5/8/8/8/8/8 b - - 0 1")).result(), "stalemate");
        String[] insufficient = {
            "4k3/8/8/8/8/8/8/4K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/2B1K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/2N1K3 w - - 0 1",
            "4kb2/8/8/8/8/8/8/2B1K3 w - - 0 1"
        };
        for (String fen : insufficient) {
            check(Position.fromFen(fen).isInsufficientMaterial(), "known insufficient material");
            equal(Game.Result.INSUFFICIENT_MATERIAL, new Game(Position.fromFen(fen)).result(), "material draw result");
        }
        String[] enough = {
            "4k3/8/8/8/8/8/8/1NN1K3 w - - 0 1", // Two knights can mate with cooperation.
            "4k1b1/8/8/8/8/8/8/2B1K3 w - - 0 1", // Bishops of opposite square colors.
            "4k3/8/8/8/8/8/P7/4K3 w - - 0 1",
            "4k3/8/8/8/8/8/8/R3K3 w - - 0 1"
        };
        for (String fen : enough) check(!Position.fromFen(fen).isInsufficientMaterial(), "do not falsely declare material draw");
        Game fifty = new Game(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 99 1"));
        equal(Game.Result.ONGOING, fifty.result(), "99 halfmoves is not draw");
        fifty.play(Move.fromUci("a1a2"));
        equal(Game.Result.FIFTY_MOVE_RULE, fifty.result(), "100 halfmoves automatic draw");
        fifty.undo();
        equal(99, fifty.position().halfmoveClock(), "undo restores halfmove clock");
        Game matePriority = new Game(Position.fromFen("7k/8/5KQ1/8/8/8/8/8 w - - 99 1"));
        matePriority.play(Move.fromUci("g6g7"));
        equal(Game.Result.WHITE_WINS, matePriority.result(), "checkmate takes priority over move clock");
        Position pawnClock = Position.fromFen("4k3/8/8/8/8/8/P7/R3K3 w - - 99 1").play(Move.fromUci("a2a3"));
        equal(0, pawnClock.halfmoveClock(), "pawn move resets fifty-move clock");
        Position captureClock = Position.fromFen("r3k3/8/8/8/8/8/8/R3K3 w - - 99 1").play(Move.fromUci("a1a8"));
        equal(0, captureClock.halfmoveClock(), "capture resets fifty-move clock");
    }

    private static void repetitionAndUndo() {
        Game game = new Game();
        check(!game.undo(), "initial undo is a no-op");
        List<Move> oldMoves = game.moves();
        Map<String, Integer> oldCounts = game.repetitionCounts();
        play(game, "g1f3", "g8f6", "f3g1", "f6g8");
        equal(Game.Result.ONGOING, game.result(), "two occurrences are not threefold");
        equal(2, game.repetitionCounts().get(Position.initial().repetitionKey()), "count initial occurrence");
        play(game, "g1f3", "g8f6", "f3g1", "f6g8");
        equal(Game.Result.THREEFOLD_REPETITION, game.result(), "threefold draw");
        equal(8, game.moves().size(), "move history size");
        equal(0, oldMoves.size(), "move history accessor returns snapshot");
        equal(1, oldCounts.get(Position.initial().repetitionKey()), "repetition accessor returns snapshot");
        expectUnsupported(() -> game.moves().clear(), "history is immutable");
        expectUnsupported(() -> game.repetitionCounts().clear(), "repetition map is immutable");
        game.undo();
        equal(Game.Result.ONGOING, game.result(), "undo clears repetition draw");
        equal(2, game.repetitionCounts().get(Position.initial().repetitionKey()), "undo decrements repetition");
        game.play(Move.fromUci("f6h5"));
        equal(Game.Result.ONGOING, game.result(), "branch after undo has correct history");
        while (game.undo()) { /* Return to the initial position. */ }
        equal(Position.INITIAL_FEN, game.position().toFen(), "complete undo restores exact FEN");
        equal(Map.of(Position.initial().repetitionKey(), 1), game.repetitionCounts(), "undo removes all abandoned repetition entries");
        String afterE4 = Position.initial().play(Move.fromUci("e2e4")).toFen();
        equal(Position.fromFen(afterE4.replace(" e3 ", " - ")).repetitionKey(),
                Position.fromFen(afterE4).repetitionKey(), "uncapturable en passant ignored in repetition");
        Position legalEp = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        check(!legalEp.repetitionKey().equals(Position.fromFen(legalEp.toFen().replace(" d6 ", " - ")).repetitionKey()), "legal en passant changes repetition identity");
        Position pinnedEp = Position.fromFen("k3r3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        equal(Position.fromFen(pinnedEp.toFen().replace(" d6 ", " - ")).repetitionKey(), pinnedEp.repetitionKey(), "illegal pinned en passant ignored in repetition");
        Position rights = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        check(!rights.repetitionKey().equals(Position.fromFen(rights.toFen().replace("KQkq", "-")).repetitionKey()), "castling rights affect repetition");
        Game special = new Game(legalEp);
        special.play(Move.fromUci("e5d6"));
        special.undo();
        equal(legalEp.toFen(), special.position().toFen(), "undo restores en passant target and pawn");
    }

    private static void play(Game game, String... moves) {
        for (String move : moves) game.play(Move.fromUci(move));
    }

    private static boolean contains(Position position, String move) {
        return position.legalMoves().contains(Move.fromUci(move));
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static void equal(Object expected, Object actual, String label) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void rejects(Runnable action, String label) {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + ": expected IllegalArgumentException");
    }

    private static void expectIllegalState(Runnable action, String label) {
        checks++;
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(label + ": expected IllegalStateException");
    }

    private static void expectUnsupported(Runnable action, String label) {
        checks++;
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError(label + ": expected UnsupportedOperationException");
    }
}
