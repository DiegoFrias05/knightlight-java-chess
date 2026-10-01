package chess;

import java.util.Locale;

/** A move in coordinate notation: e2e4, or e7e8q for a promotion. */
public record Move(int from, int to, char promotion) {
    public Move {
        if (from < 0 || from >= 64 || to < 0 || to >= 64) {
            throw new IllegalArgumentException("Move squares must be between 0 and 63.");
        }
        promotion = Character.toLowerCase(promotion);
        if (promotion != '\0' && "qrbn".indexOf(promotion) < 0) {
            throw new IllegalArgumentException("Promotion must be q, r, b, or n.");
        }
    }

    public Move(int from, int to) {
        this(from, to, '\0');
    }

    public String uci() {
        return Position.squareName(from) + Position.squareName(to)
                + (promotion == '\0' ? "" : String.valueOf(promotion));
    }

    public static Move fromUci(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Enter a move such as e2e4.");
        }
        String move = text.trim().toLowerCase(Locale.ROOT);
        if (!move.matches("[a-h][1-8][a-h][1-8][qrbn]?")) {
            throw new IllegalArgumentException("Use a move such as e2e4 or e7e8q.");
        }
        return new Move(Position.square(move.substring(0, 2)),
                Position.square(move.substring(2, 4)), move.length() == 5 ? move.charAt(4) : '\0');
    }

    @Override
    public String toString() {
        return uci();
    }
}
