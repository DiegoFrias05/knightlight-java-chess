package chess;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

/** Coordinates the view and game on Swing's event thread; search uses a worker. */
public final class ChessWindow extends JPanel {
    private static final Color BACKGROUND = new Color(16, 25, 37);
    private static final Color CARD = new Color(24, 36, 51);
    private static final Color FIELD = new Color(33, 47, 63);
    private static final Color TEXT = new Color(239, 241, 239);
    private static final Color MUTED = new Color(153, 170, 187);
    private static final Color AMBER = new Color(239, 185, 89);
    private Game game = new Game();
    private final BoardPanel board = new BoardPanel(this::selectSquare);
    private final JComboBox<String> colorChoice = new JComboBox<>(new String[]{"White", "Black"});
    private final JComboBox<Level> levelChoice = new JComboBox<>(Level.values());
    private final JLabel status = label("Your move", 24, Font.BOLD, TEXT);
    private final JLabel detail = label("White to move · Move 1", 12, Font.PLAIN, MUTED);
    private final JLabel perspective = label("YOU · WHITE", 11, Font.BOLD, MUTED);
    private final JLabel searchStats = label("Ready for your first move", 12, Font.PLAIN, MUTED);
    private final JLabel evaluation = label("A fresh position. Your opening.", 12, Font.PLAIN, MUTED);
    private final JLabel selectionHint = label("Select a piece to see its legal moves.", 12, Font.PLAIN, MUTED);
    private final JTextArea history = new JTextArea();
    private final JButton undoButton = button("Undo turn", false);
    private SwingWorker<Engine.SearchResult, Void> searchWorker;
    private long generation;
    private int selectedSquare = -1;
    private Engine.SearchResult previousSearch;
    private boolean previousSearchWhite;
    private boolean searchFailed;
    private boolean closed;
    private final MoveSearcher moveSearcher;

    @FunctionalInterface
    interface MoveSearcher {
        Engine.SearchResult search(Position position, int depth, long millis, Map<String, Integer> repetitions);
    }

    private enum Level {
        BEGINNER("Beginner", 2, 350), CLUB("Club", 3, 1_000), CHALLENGER("Challenger", 4, 2_500);
        private final String label;
        private final int depth;
        private final long millis;
        Level(String label, int depth, long millis) { this.label = label; this.depth = depth; this.millis = millis; }
        @Override public String toString() { return label; }
    }

    public ChessWindow() {
        this((position, depth, millis, repetitions) -> new Engine().search(position, depth, millis, repetitions));
    }

    // Search injection keeps the asynchronous lifecycle independently testable.
    ChessWindow(MoveSearcher moveSearcher) {
        super(new BorderLayout(24, 16));
        this.moveSearcher = moveSearcher;
        setBackground(BACKGROUND);
        setBorder(new EmptyBorder(26, 26, 18, 26));
        add(createHeader(), BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(22, 0));
        center.setOpaque(false);
        center.add(createBoardArea(), BorderLayout.CENTER);
        center.add(createSidebar(), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
        JLabel footer = label("A chess engine built in Java  •  No accounts. No internet. Just the next move.", 11, Font.PLAIN, MUTED);
        add(footer, BorderLayout.SOUTH);
        colorChoice.addActionListener(event -> newGame());
        levelChoice.addActionListener(event -> {
            if (searchWorker != null) {
                cancelSearch();
                startComputerTurn();
            }
        });
        undoButton.addActionListener(event -> undoTurn());
        bind("control N", "newGame", this::newGame);
        bind("control Z", "undo", this::undoTurn);
        bind("control F", "flip", () -> board.setFlipped(!board.isFlipped()));
        refresh();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel eyebrow = label("MEET YOUR NEXT MOVE", 10, Font.BOLD, AMBER);
        titles.add(eyebrow);
        titles.add(Box.createVerticalStrut(5));
        titles.add(label("Knightlight", 36, Font.BOLD, TEXT));
        titles.add(Box.createVerticalStrut(4));
        titles.add(label("A small engine. A whole world of chess.", 13, Font.PLAIN, MUTED));
        header.add(titles, BorderLayout.WEST);
        JLabel badge = label("JAVA CHESS  /  01", 11, Font.BOLD, AMBER);
        badge.setVerticalAlignment(SwingConstants.TOP);
        badge.setBorder(new EmptyBorder(10, 0, 0, 0));
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    private JPanel createBoardArea() {
        JPanel area = new JPanel(new BorderLayout(0, 3));
        area.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(5, 26, 0, 25));
        heading.add(label("THE BOARD", 11, Font.BOLD, MUTED), BorderLayout.WEST);
        heading.add(perspective, BorderLayout.EAST);
        area.add(heading, BorderLayout.NORTH);
        area.add(board, BorderLayout.CENTER);
        JPanel hints = new JPanel(new BorderLayout());
        hints.setOpaque(false);
        hints.setBorder(new EmptyBorder(0, 26, 0, 10));
        hints.add(selectionHint, BorderLayout.NORTH);
        JLabel keys = label("Click to move  ·  Arrow keys + Enter  ·  Esc to deselect", 11, Font.PLAIN, MUTED);
        keys.setBorder(new EmptyBorder(5, 0, 0, 0));
        hints.add(keys, BorderLayout.SOUTH);
        area.add(hints, BorderLayout.SOUTH);
        return area;
    }

    private JPanel createSidebar() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(CARD);
        panel.setBorder(new EmptyBorder(20, 20, 18, 20));
        panel.setPreferredSize(new Dimension(304, 630));
        addLeft(panel, label("YOUR GAME", 10, Font.BOLD, AMBER));
        panel.add(Box.createVerticalStrut(10));
        addLeft(panel, status);
        panel.add(Box.createVerticalStrut(5));
        addLeft(panel, detail);
        panel.add(Box.createVerticalStrut(23));

        JPanel labels = row(label("PLAY AS", 10, Font.BOLD, MUTED), label("ENGINE LEVEL", 10, Font.BOLD, MUTED));
        addLeft(panel, labels);
        panel.add(Box.createVerticalStrut(7));
        styleChoice(colorChoice, "Choose your side. Changing sides starts a new game.");
        styleChoice(levelChoice, "Beginner: depth 2; Club: depth 3; Challenger: depth 4. Search time is limited.");
        colorChoice.getAccessibleContext().setAccessibleName("Play as");
        levelChoice.getAccessibleContext().setAccessibleName("Engine level");
        levelChoice.setSelectedItem(Level.CLUB);
        JPanel choices = row(colorChoice, levelChoice);
        choices.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        choices.setPreferredSize(new Dimension(264, 36));
        addLeft(panel, choices);
        panel.add(Box.createVerticalStrut(12));

        JButton newButton = button("New game", true);
        newButton.setMnemonic('N');
        newButton.setToolTipText("Start over (Ctrl+N)");
        newButton.addActionListener(event -> newGame());
        newButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 37));
        addLeft(panel, newButton);
        panel.add(Box.createVerticalStrut(9));
        JButton flipButton = button("Flip board", false);
        flipButton.setMnemonic('F');
        flipButton.setToolTipText("Switch the board's perspective (Ctrl+F)");
        flipButton.addActionListener(event -> board.setFlipped(!board.isFlipped()));
        undoButton.setMnemonic('U');
        undoButton.setToolTipText("Take back your move and the computer's reply (Ctrl+Z)");
        JPanel controls = row(undoButton, flipButton);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        controls.setPreferredSize(new Dimension(264, 34));
        addLeft(panel, controls);
        panel.add(Box.createVerticalStrut(22));

        addLeft(panel, label("MOVE BOOK", 10, Font.BOLD, AMBER));
        panel.add(Box.createVerticalStrut(9));
        JLabel columns = label("  #   WHITE     BLACK", 11, Font.BOLD, MUTED);
        columns.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        addLeft(panel, columns);
        panel.add(Box.createVerticalStrut(5));
        history.setEditable(false);
        history.setFocusable(true);
        history.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        history.setForeground(TEXT);
        history.setBackground(FIELD);
        history.setCaretColor(AMBER);
        history.setBorder(new EmptyBorder(10, 10, 10, 10));
        history.setLineWrap(false);
        history.getAccessibleContext().setAccessibleName("Move history, coordinate notation");
        JScrollPane scroll = new JScrollPane(history);
        scroll.setBorder(BorderFactory.createLineBorder(FIELD));
        scroll.setPreferredSize(new Dimension(264, 180));
        scroll.setMinimumSize(new Dimension(200, 90));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        addLeft(panel, scroll);
        panel.add(Box.createVerticalStrut(17));
        addLeft(panel, label("ENGINE NOTES", 10, Font.BOLD, AMBER));
        panel.add(Box.createVerticalStrut(8));
        addLeft(panel, searchStats);
        panel.add(Box.createVerticalStrut(5));
        addLeft(panel, evaluation);
        panel.add(Box.createVerticalStrut(13));
        JLabel note = label("<html>Coordinate moves: e2e4 = e2 → e4.<br>Amber shows the previous move.</html>", 11, Font.PLAIN, MUTED);
        addLeft(panel, note);
        return panel;
    }

    private static void addLeft(JPanel panel, Component component) {
        if (component instanceof javax.swing.JComponent item) item.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(component);
    }

    private static JPanel row(Component first, Component second) {
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.add(first); row.add(second);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return row;
    }

    private static JLabel label(String text, int size, int weight, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, weight, size));
        return label;
    }

    private static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setBackground(primary ? AMBER : FIELD);
        button.setForeground(primary ? BACKGROUND : TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? AMBER : new Color(53, 70, 86)),
                new EmptyBorder(8, 8, 8, 8)));
        button.setFocusPainted(true);
        button.setOpaque(true);
        return button;
    }

    private static void styleChoice(JComboBox<?> choice, String tooltip) {
        choice.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        choice.setBackground(FIELD);
        choice.setForeground(TEXT);
        choice.setToolTipText(tooltip);
    }

    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private boolean humanWhite() { return colorChoice.getSelectedIndex() == 0; }
    private boolean humanTurn() { return game.position().whiteToMove() == humanWhite(); }

    void selectSquare(int square) {
        if (square < 0) { selectedSquare = -1; refresh(); return; }
        if (searchWorker != null || !humanTurn() || game.result() != Game.Result.ONGOING) return;
        Position position = game.position();
        if (selectedSquare >= 0) {
            List<Move> matching = new ArrayList<>();
            for (Move move : position.legalMoves()) {
                if (move.from() == selectedSquare && move.to() == square) matching.add(move);
            }
            if (!matching.isEmpty()) {
                Move move = matching.size() == 1 ? matching.get(0) : choosePromotion(matching);
                if (move != null) {
                    game.play(move);
                    selectedSquare = -1;
                    searchFailed = false;
                    refresh();
                    startComputerTurn();
                }
                return;
            }
        }
        char piece = position.pieceAt(square);
        selectedSquare = piece != '.' && Character.isUpperCase(piece) == humanWhite() && square != selectedSquare ? square : -1;
        refresh();
    }

    private Move choosePromotion(List<Move> candidates) {
        String[] options = {"Queen", "Rook", "Bishop", "Knight"};
        int answer = JOptionPane.showOptionDialog(this, "Choose a piece for your pawn.", "Pawn promotion",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (answer < 0) return null;
        char promotion = "qrbn".charAt(answer);
        for (Move move : candidates) if (move.promotion() == promotion) return move;
        throw new IllegalStateException("Missing legal promotion");
    }

    void newGame() {
        cancelSearch();
        game = new Game();
        selectedSquare = -1;
        previousSearch = null;
        searchFailed = false;
        board.setFlipped(!humanWhite());
        refresh();
        startComputerTurn();
    }

    private boolean canUndo() {
        int count = game.moves().size();
        return humanWhite() ? count > 0 : count > 1;
    }

    void undoTurn() {
        if (!canUndo()) return;
        cancelSearch();
        game.undo();
        if (!humanTurn() && !game.moves().isEmpty()) game.undo();
        selectedSquare = -1;
        previousSearch = null;
        searchFailed = false;
        refresh();
        startComputerTurn();
    }

    private void cancelSearch() {
        generation++;
        if (searchWorker != null) searchWorker.cancel(true);
        searchWorker = null;
    }

    private void startComputerTurn() {
        if (closed || humanTurn() || game.result() != Game.Result.ONGOING || searchWorker != null) return;
        Position snapshot = game.position();
        Map<String, Integer> repetitions = Map.copyOf(game.repetitionCounts());
        Level level = (Level) levelChoice.getSelectedItem();
        long requestGeneration = ++generation;
        searchWorker = new SwingWorker<>() {
            @Override protected Engine.SearchResult doInBackground() {
                return moveSearcher.search(snapshot, level.depth, level.millis, repetitions);
            }

            @Override protected void done() {
                if (requestGeneration != generation || closed || isCancelled()) return;
                searchWorker = null;
                try {
                    Engine.SearchResult result = get();
                    if (result.move() == null) {
                        if (game.result() == Game.Result.ONGOING) throw new IllegalStateException("The engine returned no move.");
                    } else {
                        game.play(result.move());
                        previousSearch = result;
                        previousSearchWhite = snapshot.whiteToMove();
                    }
                } catch (CancellationException ignored) {
                    return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    searchFailed = true;
                } catch (ExecutionException | RuntimeException failure) {
                    searchFailed = true;
                    Throwable cause = failure instanceof ExecutionException ? failure.getCause() : failure;
                    if (!GraphicsEnvironment.isHeadless()) {
                        JOptionPane.showMessageDialog(ChessWindow.this,
                                "The engine could not finish this turn. Use Undo turn or New game to continue.\n"
                                        + cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                                "Engine paused", JOptionPane.ERROR_MESSAGE);
                    }
                    cause.printStackTrace(System.err);
                }
                refresh();
            }
        };
        refresh();
        searchWorker.execute();
    }

    private void refresh() {
        Position position = game.position();
        Game.Result result = game.result();
        boolean thinking = searchWorker != null;
        String side = position.whiteToMove() ? "White" : "Black";
        if (result != Game.Result.ONGOING) {
            status.setText(switch (result) {
                case WHITE_WINS -> "White wins";
                case BLACK_WINS -> "Black wins";
                default -> "Draw";
            });
            detail.setText(switch (result) {
                case WHITE_WINS, BLACK_WINS -> "Checkmate · Start a new game to play again";
                case STALEMATE -> "Stalemate · No legal move";
                case THREEFOLD_REPETITION -> "Draw by threefold repetition";
                case FIFTY_MOVE_RULE -> "Draw by the fifty-move rule";
                case INSUFFICIENT_MATERIAL -> "Draw by insufficient material";
                default -> game.statusText();
            });
        } else if (searchFailed) {
            status.setText("Engine paused");
            detail.setText("Undo your turn or start a new game");
        } else {
            status.setText(thinking ? "Finding a move…" : position.isInCheck(position.whiteToMove()) ? "You're in check" : humanTurn() ? "Your move" : "Computer's turn");
            detail.setText(side + " to move · Move " + position.fullmoveNumber() + (thinking ? " · Thinking" : ""));
        }
        perspective.setText("YOU · " + (humanWhite() ? "WHITE" : "BLACK"));
        List<Move> moves = game.moves();
        List<Move> selectedMoves = new ArrayList<>();
        if (selectedSquare >= 0) {
            for (Move move : position.legalMoves()) if (move.from() == selectedSquare) selectedMoves.add(move);
        }
        board.updateBoard(position, selectedSquare, selectedMoves, moves.isEmpty() ? null : moves.get(moves.size() - 1));
        selectionHint.setText(selectedSquare >= 0 ? "Selected " + Position.squareName(selectedSquare) + " · Choose a highlighted destination."
                : result != Game.Result.ONGOING ? "Game complete. New game starts a fresh board."
                : thinking ? "The engine is thinking. You can still undo or start again."
                : "Select a piece to see its legal moves.");
        undoButton.setEnabled(canUndo());
        if (moves.isEmpty()) {
            history.setText("The story starts here.\n\nMake your first move.");
        } else {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < moves.size(); i += 2) {
                text.append(String.format(Locale.ROOT, "%2d  %-8s %s%n", i / 2 + 1, moves.get(i).uci(),
                        i + 1 < moves.size() ? moves.get(i + 1).uci() : ""));
            }
            history.setText(text.toString());
            history.setCaretPosition(history.getDocument().getLength());
        }
        if (thinking) {
            Level level = (Level) levelChoice.getSelectedItem();
            searchStats.setText(level + " · Looking up to " + level.depth + " plies ahead");
            evaluation.setText("Searching for the strongest reply…");
        } else if (previousSearch != null) {
            searchStats.setText(String.format(Locale.ROOT, "Depth %d · %,d nodes · %.2f s", previousSearch.depth(), previousSearch.nodes(), previousSearch.elapsedMillis() / 1000.0));
            int whiteScore = previousSearchWhite ? previousSearch.score() : -previousSearch.score();
            String score = Math.abs(whiteScore) >= Engine.MATE_SCORE - 128 ? (whiteScore > 0 ? "White has a mating attack" : "Black has a mating attack")
                    : String.format(Locale.ROOT, "Evaluation: %+.2f for White", whiteScore / 100.0);
            evaluation.setText(score);
            evaluation.setToolTipText("The last search's estimate, in pawns. Positive favors White; negative favors Black.");
        } else {
            searchStats.setText("Ready for your next move");
            evaluation.setText("A fresh position. Your opening.");
        }
    }

    /** Stop background work when the window is disposed. */
    public void close() { closed = true; cancelSearch(); }

    // Read-only state and normal user actions for the dependency-free UI tests.
    Position currentPosition() { return game.position(); }
    List<Move> moveHistory() { return List.copyOf(game.moves()); }
    boolean isThinking() { return searchWorker != null; }
    int selectedSquare() { return selectedSquare; }
    boolean isBoardFlipped() { return board.isFlipped(); }
    void chooseSide(boolean white) { colorChoice.setSelectedIndex(white ? 0 : 1); }
    void flipBoard() { board.setFlipped(!board.isFlipped()); }
}
