package chess;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable chess position. Squares run a1=0, b1=1, ..., h8=63.
 * Uppercase pieces are White, lowercase pieces are Black, and '.' is empty.
 *
 * <p>Move generation has two stages: generate moves that follow a piece's
 * movement rules, then remove moves that leave that player's king in check.
 * Creating a new position for each candidate keeps this approach easy to audit.
 */
public final class Position {
    public static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final int WHITE_KING_SIDE = 1;
    private static final int WHITE_QUEEN_SIDE = 2;
    private static final int BLACK_KING_SIDE = 4;
    private static final int BLACK_QUEEN_SIDE = 8;
    private static final int[][] KNIGHT_STEPS = {
        {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };
    private static final int[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private final char[] board;
    private final boolean whiteToMove;
    private final int castlingRights;
    private final int enPassantSquare;
    private final int halfmoveClock;
    private final int fullmoveNumber;

    // The private constructor takes ownership of a fresh array; it never escapes.
    private Position(char[] board, boolean whiteToMove, int castlingRights,
                     int enPassantSquare, int halfmoveClock, int fullmoveNumber) {
        this.board = board;
        this.whiteToMove = whiteToMove;
        this.castlingRights = castlingRights;
        this.enPassantSquare = enPassantSquare;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
    }

    public static Position initial() {
        return fromFen(INITIAL_FEN);
    }

    /** Reads all six FEN fields. Syntax is checked, not a position's entire history. */
    public static Position fromFen(String fen) {
        if (fen == null) {
            throw new IllegalArgumentException("FEN cannot be null.");
        }
        String[] fields = fen.trim().split("\\s+");
        if (fields.length != 6) {
            throw new IllegalArgumentException("FEN needs six fields: board, turn, castling, en passant, and two clocks.");
        }
        String[] ranks = fields[0].split("/", -1);
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN must describe eight ranks.");
        }
        char[] board = new char[64];
        java.util.Arrays.fill(board, '.');
        int whiteKings = 0;
        int blackKings = 0;
        for (int row = 0; row < 8; row++) {
            int file = 0;
            for (char token : ranks[row].toCharArray()) {
                if (token >= '1' && token <= '8') {
                    file += token - '0';
                } else if ("PNBRQKpnbrqk".indexOf(token) >= 0) {
                    if (file >= 8) {
                        throw new IllegalArgumentException("A FEN rank cannot exceed eight squares.");
                    }
                    board[(7 - row) * 8 + file++] = token;
                    if (token == 'K') whiteKings++;
                    if (token == 'k') blackKings++;
                } else {
                    throw new IllegalArgumentException("Unknown piece or empty-square count in FEN.");
                }
                if (file > 8) {
                    throw new IllegalArgumentException("A FEN rank cannot exceed eight squares.");
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("Each FEN rank must contain eight squares.");
            }
        }
        if (whiteKings != 1 || blackKings != 1) {
            throw new IllegalArgumentException("A position needs exactly one king of each color.");
        }
        if (!fields[1].equals("w") && !fields[1].equals("b")) {
            throw new IllegalArgumentException("The FEN turn must be w or b.");
        }
        boolean white = fields[1].equals("w");
        int rights = 0;
        if (!fields[2].equals("-")) {
            if (fields[2].isEmpty() || !fields[2].matches("K?Q?k?q?")) {
                throw new IllegalArgumentException("Castling rights must be KQkq in that order, or -.");
            }
            if (fields[2].contains("K")) rights |= WHITE_KING_SIDE;
            if (fields[2].contains("Q")) rights |= WHITE_QUEEN_SIDE;
            if (fields[2].contains("k")) rights |= BLACK_KING_SIDE;
            if (fields[2].contains("q")) rights |= BLACK_QUEEN_SIDE;
        }
        int enPassant = -1;
        if (!fields[3].equals("-")) {
            if (!fields[3].matches(white ? "[a-h]6" : "[a-h]3")) {
                throw new IllegalArgumentException("The en passant target must be on rank 6 for White's turn or rank 3 for Black's turn.");
            }
            enPassant = square(fields[3]);
            if (board[enPassant] != '.') {
                throw new IllegalArgumentException("The en passant target must be empty.");
            }
        }
        int halfmove = parseClock(fields[4], 0, "Halfmove clock");
        int fullmove = parseClock(fields[5], 1, "Fullmove number");
        return new Position(board, white, rights, enPassant, halfmove, fullmove);
    }

    private static int parseClock(String text, int minimum, String name) {
        try {
            if (!text.matches("[0-9]+")) throw new NumberFormatException();
            int value = Integer.parseInt(text);
            if (value < minimum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " must be an integer at least " + minimum + ".");
        }
    }

    public String toFen() {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                char piece = board[rank * 8 + file];
                if (piece == '.') {
                    empty++;
                } else {
                    if (empty > 0) fen.append(empty);
                    empty = 0;
                    fen.append(piece);
                }
            }
            if (empty > 0) fen.append(empty);
            if (rank > 0) fen.append('/');
        }
        fen.append(whiteToMove ? " w " : " b ");
        if (castlingRights == 0) {
            fen.append('-');
        } else {
            if ((castlingRights & WHITE_KING_SIDE) != 0) fen.append('K');
            if ((castlingRights & WHITE_QUEEN_SIDE) != 0) fen.append('Q');
            if ((castlingRights & BLACK_KING_SIDE) != 0) fen.append('k');
            if ((castlingRights & BLACK_QUEEN_SIDE) != 0) fen.append('q');
        }
        fen.append(' ').append(enPassantSquare < 0 ? "-" : squareName(enPassantSquare));
        return fen.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber).toString();
    }

    public static int square(String name) {
        if (name == null || !name.matches("[a-h][1-8]")) {
            throw new IllegalArgumentException("A square must look like e4.");
        }
        return (name.charAt(1) - '1') * 8 + name.charAt(0) - 'a';
    }

    public static String squareName(int square) {
        checkSquare(square);
        return "" + (char) ('a' + square % 8) + (char) ('1' + square / 8);
    }

    private static void checkSquare(int square) {
        if (square < 0 || square >= 64) {
            throw new IllegalArgumentException("Square must be between 0 and 63.");
        }
    }

    public char pieceAt(int square) {
        checkSquare(square);
        return board[square];
    }

    public boolean whiteToMove() { return whiteToMove; }
    public int halfmoveClock() { return halfmoveClock; }
    public int fullmoveNumber() { return fullmoveNumber; }

    public List<Move> legalMoves() {
        List<Move> legal = new ArrayList<>();
        for (Move move : pseudoLegalMoves()) {
            if (!after(move).isInCheck(whiteToMove)) legal.add(move);
        }
        return List.copyOf(legal);
    }

    private List<Move> pseudoLegalMoves() {
        List<Move> moves = new ArrayList<>(40);
        for (int from = 0; from < 64; from++) {
            char piece = board[from];
            if (piece == '.' || isWhite(piece) != whiteToMove) continue;
            switch (Character.toLowerCase(piece)) {
                case 'p' -> addPawnMoves(from, moves);
                case 'n' -> addLeaperMoves(from, KNIGHT_STEPS, moves);
                case 'b' -> addSlidingMoves(from, 4, 8, moves);
                case 'r' -> addSlidingMoves(from, 0, 4, moves);
                case 'q' -> addSlidingMoves(from, 0, 8, moves);
                case 'k' -> {
                    addLeaperMoves(from, DIRECTIONS, moves);
                    addCastlingMoves(from, moves);
                }
                default -> throw new IllegalStateException("Unknown piece.");
            }
        }
        return moves;
    }

    private void addPawnMoves(int from, List<Move> moves) {
        int direction = whiteToMove ? 1 : -1;
        int rank = from / 8;
        int file = from % 8;
        int nextRank = rank + direction;
        if (nextRank < 0 || nextRank >= 8) return;
        int oneStep = from + direction * 8;
        if (board[oneStep] == '.') {
            addPawnMove(from, oneStep, moves);
            int startRank = whiteToMove ? 1 : 6;
            if (rank == startRank && board[from + direction * 16] == '.') {
                moves.add(new Move(from, from + direction * 16));
            }
        }
        for (int deltaFile : new int[] {-1, 1}) {
            int nextFile = file + deltaFile;
            if (!inside(nextFile, nextRank)) continue;
            int to = nextRank * 8 + nextFile;
            boolean ordinaryCapture = capturable(board[to]);
            boolean enPassantCapture = to == enPassantSquare && board[to] == '.'
                    && board[to - direction * 8] == (whiteToMove ? 'p' : 'P');
            if (ordinaryCapture || enPassantCapture) addPawnMove(from, to, moves);
        }
    }

    private static void addPawnMove(int from, int to, List<Move> moves) {
        if (to / 8 == 0 || to / 8 == 7) {
            for (char promotion : new char[] {'q', 'r', 'b', 'n'}) {
                moves.add(new Move(from, to, promotion));
            }
        } else {
            moves.add(new Move(from, to));
        }
    }

    private void addLeaperMoves(int from, int[][] steps, List<Move> moves) {
        for (int[] step : steps) {
            int file = from % 8 + step[0];
            int rank = from / 8 + step[1];
            if (inside(file, rank)) {
                int to = rank * 8 + file;
                if (board[to] == '.' || capturable(board[to])) moves.add(new Move(from, to));
            }
        }
    }

    private void addSlidingMoves(int from, int firstDirection, int lastDirection, List<Move> moves) {
        for (int i = firstDirection; i < lastDirection; i++) {
            int file = from % 8 + DIRECTIONS[i][0];
            int rank = from / 8 + DIRECTIONS[i][1];
            while (inside(file, rank)) {
                int to = rank * 8 + file;
                if (board[to] == '.') {
                    moves.add(new Move(from, to));
                } else {
                    if (capturable(board[to])) moves.add(new Move(from, to));
                    break;
                }
                file += DIRECTIONS[i][0];
                rank += DIRECTIONS[i][1];
            }
        }
    }

    private boolean capturable(char piece) {
        return piece != '.' && isWhite(piece) != whiteToMove && Character.toLowerCase(piece) != 'k';
    }

    private void addCastlingMoves(int from, List<Move> moves) {
        int home = whiteToMove ? 4 : 60;
        if (from != home || isInCheck(whiteToMove)) return;
        char rook = whiteToMove ? 'R' : 'r';
        int kingRight = whiteToMove ? WHITE_KING_SIDE : BLACK_KING_SIDE;
        int queenRight = whiteToMove ? WHITE_QUEEN_SIDE : BLACK_QUEEN_SIDE;
        if ((castlingRights & kingRight) != 0 && board[home + 3] == rook
                && board[home + 1] == '.' && board[home + 2] == '.'
                && !isAttacked(home + 1, !whiteToMove) && !isAttacked(home + 2, !whiteToMove)) {
            moves.add(new Move(home, home + 2));
        }
        if ((castlingRights & queenRight) != 0 && board[home - 4] == rook
                && board[home - 1] == '.' && board[home - 2] == '.' && board[home - 3] == '.'
                && !isAttacked(home - 1, !whiteToMove) && !isAttacked(home - 2, !whiteToMove)) {
            moves.add(new Move(home, home - 2));
        }
    }

    public boolean isInCheck(boolean white) {
        char king = white ? 'K' : 'k';
        for (int square = 0; square < 64; square++) {
            if (board[square] == king) return isAttacked(square, !white);
        }
        throw new IllegalStateException("Position has no " + (white ? "white" : "black") + " king.");
    }

    /** Attacks are geometric: even a pinned enemy piece restricts king movement. */
    private boolean isAttacked(int target, boolean byWhite) {
        int file = target % 8;
        int rank = target / 8;
        int pawnRank = rank + (byWhite ? -1 : 1);
        for (int pawnFile : new int[] {file - 1, file + 1}) {
            if (inside(pawnFile, pawnRank)
                    && board[pawnRank * 8 + pawnFile] == (byWhite ? 'P' : 'p')) return true;
        }
        for (int[] step : KNIGHT_STEPS) {
            int knightFile = file + step[0];
            int knightRank = rank + step[1];
            if (inside(knightFile, knightRank)
                    && board[knightRank * 8 + knightFile] == (byWhite ? 'N' : 'n')) return true;
        }
        for (int i = 0; i < DIRECTIONS.length; i++) {
            int nextFile = file + DIRECTIONS[i][0];
            int nextRank = rank + DIRECTIONS[i][1];
            int distance = 1;
            while (inside(nextFile, nextRank)) {
                char piece = board[nextRank * 8 + nextFile];
                if (piece != '.') {
                    if (isWhite(piece) == byWhite) {
                        char type = Character.toLowerCase(piece);
                        if (type == 'q' || (i < 4 && type == 'r') || (i >= 4 && type == 'b')
                                || (distance == 1 && type == 'k')) return true;
                    }
                    break;
                }
                nextFile += DIRECTIONS[i][0];
                nextRank += DIRECTIONS[i][1];
                distance++;
            }
        }
        return false;
    }

    private static boolean inside(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    private static boolean isWhite(char piece) {
        return piece >= 'A' && piece <= 'Z';
    }

    /** Validates a player-entered move and leaves this position unchanged. */
    public Position play(Move move) {
        if (!legalMoves().contains(move)) {
            throw new IllegalArgumentException("Illegal move: " + move);
        }
        return after(move);
    }

    /**
     * Fast application for moves already generated by this class. Call play()
     * for untrusted input; after() intentionally skips complete legality checks.
     */
    public Position after(Move move) {
        char moving = board[move.from()];
        if (moving == '.') throw new IllegalArgumentException("There is no piece on the starting square.");
        char captured = board[move.to()];
        char[] next = board.clone();
        next[move.from()] = '.';
        next[move.to()] = moving;
        boolean pawn = Character.toLowerCase(moving) == 'p';
        if (pawn && move.to() == enPassantSquare && captured == '.' && move.from() % 8 != move.to() % 8) {
            int captureSquare = move.to() + (whiteToMove ? -8 : 8);
            captured = next[captureSquare];
            next[captureSquare] = '.';
        }
        if (pawn && move.promotion() != '\0') {
            next[move.to()] = whiteToMove ? Character.toUpperCase(move.promotion()) : move.promotion();
        }
        if (Character.toLowerCase(moving) == 'k' && Math.abs(move.to() - move.from()) == 2) {
            int rookFrom = move.to() > move.from() ? move.from() + 3 : move.from() - 4;
            int rookTo = move.to() > move.from() ? move.from() + 1 : move.from() - 1;
            next[rookTo] = next[rookFrom];
            next[rookFrom] = '.';
        }
        int rights = castlingRights;
        if (moving == 'K') rights &= ~(WHITE_KING_SIDE | WHITE_QUEEN_SIDE);
        if (moving == 'k') rights &= ~(BLACK_KING_SIDE | BLACK_QUEEN_SIDE);
        rights = removeRookRight(rights, move.from());
        rights = removeRookRight(rights, move.to());
        int ep = pawn && Math.abs(move.to() - move.from()) == 16 ? (move.from() + move.to()) / 2 : -1;
        int halfmove = pawn || captured != '.' ? 0 : incrementSafely(halfmoveClock);
        int fullmove = whiteToMove ? fullmoveNumber : incrementSafely(fullmoveNumber);
        return new Position(next, !whiteToMove, rights, ep, halfmove, fullmove);
    }

    private static int incrementSafely(int clock) {
        return clock == Integer.MAX_VALUE ? clock : clock + 1;
    }

    private static int removeRookRight(int rights, int square) {
        return switch (square) {
            case 0 -> rights & ~WHITE_QUEEN_SIDE;
            case 7 -> rights & ~WHITE_KING_SIDE;
            case 56 -> rights & ~BLACK_QUEEN_SIDE;
            case 63 -> rights & ~BLACK_KING_SIDE;
            default -> rights;
        };
    }

    /** Conservative dead-position detection; two knights are not an automatic draw. */
    public boolean isInsufficientMaterial() {
        int minors = 0;
        int knights = 0;
        int bishopColor = -1;
        boolean sameColorBishops = true;
        for (int square = 0; square < 64; square++) {
            char piece = Character.toLowerCase(board[square]);
            if (piece == '.' || piece == 'k') continue;
            if (piece == 'p' || piece == 'r' || piece == 'q') return false;
            minors++;
            if (piece == 'n') {
                knights++;
            } else {
                int color = (square / 8 + square % 8) % 2;
                if (bishopColor < 0) bishopColor = color;
                else if (color != bishopColor) sameColorBishops = false;
            }
        }
        return minors <= 1 || (knights == 0 && sameColorBishops);
    }

    /** Clocks do not affect repetition; an unusable en passant target does not either. */
    public String repetitionKey() {
        String[] fields = toFen().split(" ");
        if (enPassantSquare >= 0) {
            boolean legalEnPassant = false;
            for (Move move : legalMoves()) {
                if (move.to() == enPassantSquare && Character.toLowerCase(board[move.from()]) == 'p'
                        && move.from() % 8 != move.to() % 8) {
                    legalEnPassant = true;
                    break;
                }
            }
            if (!legalEnPassant) fields[3] = "-";
        }
        return fields[0] + " " + fields[1] + " " + fields[2] + " " + fields[3];
    }
}
