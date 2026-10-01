package chess;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/** A scalable board drawn entirely with Java2D, including the chess pieces. */
public final class BoardPanel extends JPanel {
    private static final Color LIGHT = new Color(231, 224, 207);
    private static final Color DARK = new Color(105, 126, 142);
    private static final Color AMBER = new Color(239, 185, 89);
    private Position position = Position.initial();
    private boolean flipped;
    private int selected = -1;
    private int keyboardSquare = Position.square("e2");
    private List<Move> choices = List.of();
    private Move lastMove;
    private final IntConsumer onSquare;

    public BoardPanel(IntConsumer onSquare) {
        this.onSquare = onSquare;
        setOpaque(false);
        setPreferredSize(new Dimension(684, 684));
        setMinimumSize(new Dimension(330, 330));
        setFocusable(true);
        setToolTipText("Click a piece, then a highlighted destination. Arrow keys and Enter also work.");
        getAccessibleContext().setAccessibleName("Chess board");
        getAccessibleContext().setAccessibleDescription("Use arrow keys to choose a square, then Enter to select a piece or destination.");
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                int square = squareAt(event.getX(), event.getY());
                if (square >= 0) {
                    keyboardSquare = square;
                    requestFocusInWindow();
                    onSquare.accept(square);
                }
            }
        });
        bind("LEFT", () -> navigate(-1, 0));
        bind("RIGHT", () -> navigate(1, 0));
        bind("UP", () -> navigate(0, -1));
        bind("DOWN", () -> navigate(0, 1));
        bind("ENTER", () -> onSquare.accept(keyboardSquare));
        bind("SPACE", () -> onSquare.accept(keyboardSquare));
        bind("ESCAPE", () -> onSquare.accept(-1));
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) { repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent event) { repaint(); }
        });
    }

    private void bind(String key, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }

    private void navigate(int dx, int dy) {
        int file = keyboardSquare % 8;
        int rank = keyboardSquare / 8;
        file = Math.max(0, Math.min(7, file + (flipped ? -dx : dx)));
        rank = Math.max(0, Math.min(7, rank + (flipped ? dy : -dy)));
        keyboardSquare = rank * 8 + file;
        getAccessibleContext().setAccessibleDescription("Focused square " + Position.squareName(keyboardSquare));
        repaint();
    }

    public void updateBoard(Position newPosition, int newSelected, List<Move> newChoices, Move newLastMove) {
        position = newPosition;
        selected = newSelected;
        choices = List.copyOf(newChoices);
        lastMove = newLastMove;
        repaint();
    }

    public void setFlipped(boolean value) { flipped = value; repaint(); }
    public boolean isFlipped() { return flipped; }

    private int cellSize() { return Math.max(1, Math.min(getWidth() - 50, getHeight() - 48) / 8); }
    private int boardX() { return (getWidth() - cellSize() * 8) / 2; }
    private int boardY() { return (getHeight() - cellSize() * 8) / 2 - 2; }

    private int squareAt(int x, int y) {
        int size = cellSize();
        if (x < boardX() || y < boardY() || x >= boardX() + size * 8 || y >= boardY() + size * 8) return -1;
        int column = (x - boardX()) / size;
        int row = (y - boardY()) / size;
        return (flipped ? row : 7 - row) * 8 + (flipped ? 7 - column : column);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int size = cellSize();
        int x0 = boardX();
        int y0 = boardY();
        g.setColor(new Color(7, 14, 22, 110));
        g.fillRoundRect(x0 - 5, y0 - 3, size * 8 + 10, size * 8 + 12, 8, 8);
        boolean check = position.isInCheck(position.whiteToMove());
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                int square = (flipped ? row : 7 - row) * 8 + (flipped ? 7 - column : column);
                int x = x0 + column * size;
                int y = y0 + row * size;
                char piece = position.pieceAt(square);
                g.setColor((row + column) % 2 == 0 ? LIGHT : DARK);
                g.fillRect(x, y, size, size);
                if (lastMove != null && (lastMove.from() == square || lastMove.to() == square)) {
                    g.setColor(new Color(243, 190, 80, 110));
                    g.fillRect(x, y, size, size);
                }
                if (check && piece == (position.whiteToMove() ? 'K' : 'k')) {
                    g.setColor(new Color(211, 77, 65, 150));
                    g.fillRect(x, y, size, size);
                }
                if (square == selected) {
                    g.setColor(new Color(248, 198, 96, 180));
                    g.fillRect(x, y, size, size);
                    g.setColor(AMBER.brighter());
                    g.setStroke(new BasicStroke(3));
                    g.drawRect(x + 2, y + 2, size - 4, size - 4);
                }
                if (piece != '.') drawPiece(g, piece, x, y, size);
                boolean destination = false;
                for (Move move : choices) if (move.to() == square) { destination = true; break; }
                if (destination) {
                    g.setColor(new Color(22, 46, 53, 95));
                    if (piece == '.') g.fillOval(x + size * 2 / 5, y + size * 2 / 5, size / 5, size / 5);
                    else {
                        g.setStroke(new BasicStroke(Math.max(3, size / 16f)));
                        g.drawOval(x + size / 12, y + size / 12, size * 5 / 6, size * 5 / 6);
                    }
                }
                if (hasFocus() && keyboardSquare == square) {
                    g.setColor(new Color(255, 255, 255, 220));
                    g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
                    g.drawRect(x + 5, y + 5, size - 10, size - 10);
                }
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.setColor(new Color(151, 166, 181));
        FontMetrics metrics = g.getFontMetrics();
        for (int index = 0; index < 8; index++) {
            String file = String.valueOf((char) ('a' + (flipped ? 7 - index : index)));
            String rank = String.valueOf(flipped ? index + 1 : 8 - index);
            g.drawString(file, x0 + index * size + (size - metrics.stringWidth(file)) / 2, y0 + size * 8 + 20);
            g.drawString(rank, x0 - 18, y0 + index * size + (size + metrics.getAscent()) / 2 - 2);
        }
        g.dispose();
    }

    private static Path2D path(double... coordinates) {
        Path2D shape = new Path2D.Double();
        shape.moveTo(coordinates[0], coordinates[1]);
        for (int i = 2; i < coordinates.length; i += 2) shape.lineTo(coordinates[i], coordinates[i + 1]);
        shape.closePath();
        return shape;
    }

    private static void drawPiece(Graphics2D original, char piece, int x, int y, int size) {
        Graphics2D g = (Graphics2D) original.create();
        g.translate(x, y);
        g.scale(size / 100.0, size / 100.0);
        boolean white = Character.isUpperCase(piece);
        Color fill = white ? new Color(255, 251, 235) : new Color(30, 43, 56);
        Color edge = white ? new Color(49, 64, 77) : new Color(230, 221, 201);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(15, 25, 36, 30));
        g.fill(new Ellipse2D.Double(23, 79, 57, 11));
        char type = Character.toLowerCase(piece);
        switch (type) {
            case 'p' -> {
                fillAndStroke(g, path(41, 43, 59, 43, 58, 59, 66, 72, 34, 72, 42, 59), fill, edge);
                fillAndStroke(g, new Ellipse2D.Double(37, 22, 26, 26), fill, edge);
            }
            case 'r' -> {
                fillAndStroke(g, path(31, 23, 40, 23, 40, 32, 46, 32, 46, 23, 54, 23, 54, 32, 60, 32, 60, 23, 69, 23, 69, 43, 62, 49, 63, 69, 69, 75, 31, 75, 37, 69, 38, 49, 31, 43), fill, edge);
                g.setColor(edge); g.drawLine(38, 47, 62, 47);
            }
            case 'n' -> {
                Path2D horse = new Path2D.Double();
                horse.moveTo(29, 75); horse.curveTo(32, 64, 45, 60, 49, 47);
                horse.lineTo(33, 54); horse.lineTo(23, 47); horse.lineTo(32, 32);
                horse.lineTo(43, 27); horse.lineTo(42, 17); horse.lineTo(52, 24);
                horse.curveTo(72, 25, 77, 45, 69, 73); horse.lineTo(71, 77); horse.closePath();
                fillAndStroke(g, horse, fill, edge);
                g.setColor(edge); g.fillOval(39, 34, 4, 4);
                g.drawLine(26, 46, 34, 46);
                g.draw(new java.awt.geom.CubicCurve2D.Double(55, 32, 66, 39, 65, 52, 57, 61));
            }
            case 'b' -> {
                fillAndStroke(g, path(43, 48, 57, 48, 57, 58, 65, 74, 35, 74, 43, 58), fill, edge);
                Path2D head = new Path2D.Double();
                head.moveTo(50, 21); head.curveTo(29, 37, 31, 48, 50, 51);
                head.curveTo(69, 48, 71, 37, 50, 21); head.closePath();
                fillAndStroke(g, head, fill, edge);
                g.setColor(edge); g.drawLine(53, 28, 45, 39);
                fillAndStroke(g, new Ellipse2D.Double(46, 16, 8, 8), fill, edge);
            }
            case 'q' -> {
                fillAndStroke(g, path(29, 34, 41, 43, 50, 27, 59, 43, 71, 34, 63, 60, 66, 73, 34, 73, 37, 60), fill, edge);
                fillAndStroke(g, new Ellipse2D.Double(24, 25, 10, 10), fill, edge);
                fillAndStroke(g, new Ellipse2D.Double(45, 17, 10, 10), fill, edge);
                fillAndStroke(g, new Ellipse2D.Double(66, 25, 10, 10), fill, edge);
                g.setColor(edge); g.drawLine(39, 59, 61, 59);
            }
            case 'k' -> {
                fillAndStroke(g, path(46, 15, 54, 15, 54, 22, 61, 22, 61, 29, 54, 29, 54, 37, 46, 37, 46, 29, 39, 29, 39, 22, 46, 22), fill, edge);
                Path2D crown = new Path2D.Double();
                crown.moveTo(37, 59); crown.curveTo(21, 42, 35, 31, 50, 41);
                crown.curveTo(65, 31, 79, 42, 63, 59);
                crown.lineTo(63, 68); crown.lineTo(67, 75); crown.lineTo(33, 75);
                crown.lineTo(37, 68); crown.closePath();
                fillAndStroke(g, crown, fill, edge);
                g.setColor(edge); g.drawLine(38, 59, 62, 59);
            }
            default -> { }
        }
        fillAndStroke(g, new RoundRectangle2D.Double(30, 73, 40, 7, 3, 3), fill, edge);
        fillAndStroke(g, new RoundRectangle2D.Double(25, 80, 50, 7, 3, 3), fill, edge);
        g.dispose();
    }

    private static void fillAndStroke(Graphics2D g, java.awt.Shape shape, Color fill, Color edge) {
        g.setColor(fill); g.fill(shape); g.setColor(edge); g.draw(shape);
    }
}
