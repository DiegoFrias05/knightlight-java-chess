package chess;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

/** Headless EDT tests for real user actions and delayed, cancelled search results. */
public final class UiTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        ControlledSearch search = new ControlledSearch();
        ChessWindow window = edt(() -> new ChessWindow(search));
        String initial = Position.initial().toFen();
        try {
            edt(() -> {
                check(window.currentPosition().toFen().equals(initial), "initial board");
                check(!window.isThinking(), "White waits for human");
                window.selectSquare(Position.square("e7"));
                check(window.selectedSquare() == -1, "cannot select opponent piece");
                window.selectSquare(Position.square("e2"));
                check(window.selectedSquare() == Position.square("e2"), "piece selection");
                window.selectSquare(Position.square("e5"));
                check(window.currentPosition().toFen().equals(initial), "illegal destination does not move");
                play(window, "e2", "e4");
                check(window.moveHistory().get(0).uci().equals("e2e4"), "click pair plays a legal move");
                check(window.isThinking(), "human move starts asynchronous search");
                play(window, "d2", "d4");
                check(window.moveHistory().size() == 1, "input blocked during computer turn");
                return null;
            });
            Request stale = search.next();
            check(stale.repetitions.size() == 2, "search receives game repetition snapshot");
            edt(() -> { window.newGame(); play(window, "d2", "d4"); return null; });
            Request replacement = search.next();
            stale.finish();
            flushLateResult(stale);
            edt(() -> {
                check(window.moveHistory().size() == 1 && window.moveHistory().get(0).uci().equals("d2d4"),
                        "old result cannot modify replacement game");
                check(window.isThinking(), "old callback cannot clear replacement worker");
                window.newGame();
                return null;
            });
            replacement.finish();
            flushLateResult(replacement);
            edt(() -> {
                check(window.currentPosition().toFen().equals(initial), "new game rejects cancelled stale reply");
                check(!window.isThinking(), "new game clears busy state");
                play(window, "e2", "e4");
                return null;
            });

            Request undoPending = search.next();
            edt(() -> { window.undoTurn(); return null; });
            undoPending.finish();
            flushLateResult(undoPending);
            edt(() -> {
                check(window.currentPosition().toFen().equals(initial), "undo cancels pending reply and human move");
                play(window, "d2", "d4");
                return null;
            });

            search.next().finish();
            await(() -> !readThinking(window), "computer reply completes");
            edt(() -> {
                check(window.moveHistory().size() == 2, "AI reply reaches board");
                check(window.currentPosition().whiteToMove(), "human turn follows AI reply");
                window.undoTurn();
                check(window.currentPosition().toFen().equals(initial), "undo removes complete human/computer turn");
                window.chooseSide(false);
                check(window.isBoardFlipped(), "playing Black orients board");
                check(window.isThinking(), "playing Black automatically starts White");
                return null;
            });

            search.next().finish();
            await(() -> !readThinking(window), "White opening completes");
            String blackOpening = edt(() -> window.currentPosition().toFen());
            edt(() -> {
                check(!window.currentPosition().whiteToMove(), "Black gets the turn after AI opening");
                window.undoTurn();
                check(window.moveHistory().size() == 1, "Black cannot undo the computer's opening alone");
                play(window, "e7", "e5");
                check(window.moveHistory().size() == 2, "Black can make a legal move");
                return null;
            });
            Request blackUndo = search.next();
            edt(() -> { window.undoTurn(); return null; });
            blackUndo.finish();
            flushLateResult(blackUndo);
            edt(() -> {
                check(window.currentPosition().toFen().equals(blackOpening), "Black undo restores its turn");
                play(window, "e7", "e5");
                return null;
            });
            search.next().finish();
            await(() -> !readThinking(window), "White reply completes");
            edt(() -> {
                check(window.moveHistory().size() == 3, "Black turn receives AI reply");
                window.undoTurn();
                check(window.currentPosition().toFen().equals(blackOpening), "completed Black undo removes full turn");
                window.chooseSide(true);
                check(window.currentPosition().toFen().equals(initial), "changing color starts fresh game");
                check(!window.isBoardFlipped(), "White orientation resets");
                window.flipBoard();
                check(window.isBoardFlipped(), "manual board flip");
                check(window.currentPosition().toFen().equals(initial), "board flip preserves position");
                play(window, "e2", "e4");
                return null;
            });
            Request afterClose = search.next();
            edt(() -> { window.close(); return null; });
            afterClose.finish();
            flushLateResult(afterClose);
            edt(() -> {
                check(!window.isThinking(), "closing cancels work");
                check(window.moveHistory().size() == 1, "closed window rejects search result");
                return null;
            });
        } finally {
            edt(() -> { window.close(); return null; });
            search.releaseAll();
        }
        System.out.println("UiTest: " + checks + " checks passed.");
    }

    private static void play(ChessWindow window, String from, String to) {
        window.selectSquare(Position.square(from));
        window.selectSquare(Position.square(to));
    }

    private static boolean readThinking(ChessWindow window) {
        try { return edt(window::isThinking); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(15);
        check(condition.getAsBoolean(), message);
    }

    private static void flushLateResult(Request request) throws Exception {
        check(request.finished.await(5, TimeUnit.SECONDS), "delayed search terminates");
        // SwingWorker batches done events on a short timer; let a cancelled task report late.
        Thread.sleep(100);
        edt(() -> null);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Request {
        final Position position;
        final Map<String, Integer> repetitions;
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        Request(Position position, Map<String, Integer> repetitions) {
            this.position = position;
            this.repetitions = repetitions;
        }
        void finish() { release.countDown(); }
    }

    private static final class ControlledSearch implements ChessWindow.MoveSearcher {
        final BlockingQueue<Request> pending = new LinkedBlockingQueue<>();
        final java.util.List<Request> all = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override public Engine.SearchResult search(Position position, int depth, long millis, Map<String, Integer> repetitions) {
            if (SwingUtilities.isEventDispatchThread()) throw new AssertionError("Search must not block the EDT");
            Request request = new Request(position, repetitions);
            all.add(request);
            pending.add(request);
            boolean released = false;
            while (!released) {
                try { request.release.await(); released = true; }
                catch (InterruptedException ignored) {
                    // Deliberately finish late to exercise stale-result protection.
                }
            }
            request.finished.countDown();
            return new Engine.SearchResult(position.legalMoves().get(0), 0, depth, 1, 1);
        }

        Request next() throws Exception {
            Request request = pending.poll(5, TimeUnit.SECONDS);
            check(request != null, "background search started");
            return request;
        }

        void releaseAll() { for (Request request : all) request.finish(); }
    }
}
