package chess;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Behavioral search tests; no testing libraries or network downloads required. */
public final class EngineTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Engine engine = new Engine();
        Position start = Position.initial();
        String original = start.toFen();
        Map<String, Integer> counts = new HashMap<>(Map.of(start.repetitionKey(), 1));
        Engine.SearchResult opening = engine.search(start, 2, 5_000, counts);
        check(start.legalMoves().contains(opening.move()), "opening move is legal");
        check(opening.depth() == 2, "completes requested shallow depth");
        check(opening.nodes() > 0, "counts searched positions");
        check(start.toFen().equals(original), "search preserves input position");
        check(counts.equals(Map.of(start.repetitionKey(), 1)), "search preserves repetition history");
        check(Engine.evaluate(start) == 0, "symmetric starting evaluation");

        assertMate(engine, "7k/8/5KQ1/8/8/8/8/8 w - - 0 1");
        assertMate(engine, "8/8/8/8/8/5kq1/8/7K b - - 0 1");
        Position freeQueen = Position.fromFen("4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1");
        check(engine.search(freeQueen, 2, 5_000).move().uci().equals("e4d5"), "captures an undefended queen");
        // The rook threatens a7, so promotion now matters more than improving the king.
        Position promotion = Position.fromFen("7k/Pr6/8/8/8/8/8/7K w - - 0 1");
        check(engine.search(promotion, 2, 5_000).move().promotion() == 'q', "values queen promotion");

        Position mate = Position.fromFen("7k/6Q1/5K2/8/8/8/8/8 b - - 100 1");
        Engine.SearchResult mated = engine.search(mate, 3, 100);
        check(mated.move() == null && mated.score() == -Engine.MATE_SCORE, "mate takes precedence over draw clock");
        Position stalemate = Position.fromFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1");
        check(engine.search(stalemate, 3, 100).move() == null, "no move on stalemate");
        Position bareKings = Position.fromFen("7k/8/8/8/8/8/8/K7 w - - 0 1");
        check(engine.search(bareKings, 3, 100).move() == null, "stops at insufficient material");
        check(engine.search(start, 3, 100, Map.of(start.repetitionKey(), 3)).move() == null,
                "stops at threefold repetition");
        assertDrawChoices(engine);

        Engine.SearchResult tinyBudget = engine.search(start, 12, 1);
        check(start.legalMoves().contains(tinyBudget.move()), "tiny budget retains legal fallback");
        assertCancellation(engine, start, true);
        assertCancellation(engine, start, false);
        Engine.SearchResult afterCancellation = engine.search(start, 2, 5_000);
        check(afterCancellation.depth() == 2 && start.legalMoves().contains(afterCancellation.move()),
                "same engine remains usable after cancelled searches");

        Game game = new Game();
        for (int ply = 0; ply < 24 && game.result() == Game.Result.ONGOING; ply++) {
            Engine.SearchResult reply = engine.search(game.position(), 2, 80, game.repetitionCounts());
            check(reply.move() != null && game.position().legalMoves().contains(reply.move()),
                    "self-play legal move " + ply);
            game.play(reply.move());
        }
        check(game.moves().size() > 4, "self-play progresses beyond opening moves");
        System.out.println("EngineTest: " + checks + " checks passed.");
    }

    private static void assertMate(Engine engine, String fen) {
        Position position = Position.fromFen(fen);
        Engine.SearchResult result = engine.search(position, 2, 5_000);
        check(position.legalMoves().contains(result.move()), "mating move is legal");
        Position child = position.after(result.move());
        check(child.legalMoves().isEmpty() && child.isInCheck(child.whiteToMove()), "finds mate in one");
        check(result.score() == Engine.MATE_SCORE - 1, "mate distance score");
    }

    private static void assertDrawChoices(Engine engine) {
        // White is a queen behind. Reaching a position for the third time is
        // better than continuing, even though the current position is not drawn.
        Position losing = Position.fromFen("7k/8/8/3q4/8/8/P7/K7 w - - 0 1");
        Move repeat = new Move(Position.square("a2"), Position.square("a3"));
        Map<String, Integer> history = Map.of(losing.repetitionKey(), 1,
                losing.after(repeat).repetitionKey(), 2);
        Engine.SearchResult repetition = engine.search(losing, 2, 5_000, history);
        check(repeat.equals(repetition.move()) && repetition.score() == 0,
                "takes an available third repetition instead of a losing continuation");

        // At 99 halfmoves, a king/queen move draws immediately while a pawn
        // move resets the clock. Which choice is good depends on who is ahead.
        Position winning99 = Position.fromFen("7k/8/8/8/8/8/P7/KQ6 w - - 99 1");
        Engine.SearchResult avoidsDraw = engine.search(winning99, 2, 5_000);
        check(avoidsDraw.move() != null && winning99.pieceAt(avoidsDraw.move().from()) == 'P'
                        && avoidsDraw.score() > 0,
                "winning side resets the fifty-move clock with a pawn move");
        Position losing99 = Position.fromFen("7k/8/8/3q4/8/8/P7/K7 w - - 99 1");
        Engine.SearchResult takesDraw = engine.search(losing99, 2, 5_000);
        check(takesDraw.move() != null && losing99.pieceAt(takesDraw.move().from()) == 'K'
                        && takesDraw.score() == 0,
                "losing side accepts a fifty-move draw inside search");
    }

    private static void assertCancellation(Engine engine, Position position,
                                           boolean interruptBeforeSearch) throws InterruptedException {
        String label = interruptBeforeSearch ? "pre-cancelled" : "active";
        AtomicReference<Engine.SearchResult> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            if (interruptBeforeSearch) Thread.currentThread().interrupt();
            started.countDown();
            try {
                result.set(engine.search(position, 12, 60_000));
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "engine-test-" + label);
        // A cancellation regression must fail the test, not keep its JVM alive.
        worker.setDaemon(true);
        worker.start();
        try {
            check(started.await(3, TimeUnit.SECONDS), label + " worker starts");
            if (!interruptBeforeSearch) {
                // Give the deep search time to enter its work before cancelling.
                // The generous join below tolerates ordinary scheduling delays.
                Thread.sleep(50);
                check(worker.isAlive(), "deep search is running before cancellation");
                worker.interrupt();
            }
            worker.join(3_000);
            check(!worker.isAlive(), label + " search stops promptly");
            check(failure.get() == null, label + " search does not throw");
            check(result.get() != null && position.legalMoves().contains(result.get().move()),
                    label + " search returns a legal result");
        } finally {
            // Also clean up when an assertion or waiting operation fails.
            worker.interrupt();
            worker.join(3_000);
        }
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}
