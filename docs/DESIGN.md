# Design notes

Knightlight separates the rules of chess, the record of a game, the computer's decisions, and the desktop interface. This lets the engine explore hypothetical moves without changing the game the player sees.

## Main classes

- **`Move`** is an immutable value describing a source square, destination square, and optional promotion piece. Coordinate notation such as `e2e4` is a simple way to represent a move without implementing a full chess notation parser.
- **`Position`** is an immutable board snapshot. It owns the side to move, castling rights, en passant information, move counters, and rule calculations. Applying a move produces another position. It can import and export FEN, a standard position description useful for focused tests.
- **`Game`** owns the actual game's sequence of positions and moves. It tracks repeated positions, determines the result, and supports undo. A search branch never appends moves to this history.
- **`Engine`** evaluates positions and searches possible continuations. Its result gives the interface a move and search statistics.
- **`BoardPanel`** draws the board and handles square selection and legal-move highlights.
- **`ChessWindow`** connects the controls, game state, board, and engine. It runs search in the background so the window remains responsive, and discards cancelled searches when starting over or undoing.
- **`ChessApp`** starts the desktop application.

The immutable position is the most important boundary. Its extra copying costs time and memory, but makes recursive search easier to reason about: returning from a branch cannot accidentally leave a piece moved on another branch's board.

## Legal moves

Move generation first considers how each piece can move. It then excludes moves that leave the moving side's king attacked. This distinction matters for pinned pieces and for kings moving into check.

Some rules need more information than piece placement:

- Castling depends on historical rights, clear squares, and whether the king crosses an attacked square.
- En passant depends on the immediately preceding pawn advance; removing the captured pawn can reveal an attack on the king.
- Promotion creates four distinct choices at the last rank.

An attack test is separate from full legal move generation. For example, a pawn attacks diagonally even when those squares are empty. Using legal moves to compute attacks would also introduce a circular dependency when checking king safety.

## How the engine chooses a move

The engine uses **negamax**: a position that is good for the player to move is bad for the opponent. This allows one recursive search function to handle both colors by negating the score returned for the next player.

Conceptually:

```text
for each legal move:
    child = position after the move
    score = -search(child, depth - 1)
choose the move with the highest score
```

The real search adds several improvements:

1. **Alpha-beta pruning** keeps bounds on scores that could still affect the decision. A branch can stop once it cannot improve the choice at an earlier node.
2. **Iterative deepening** completes depth one, then depth two, and so on. A time budget bounds the work, and the last completed iteration supplies a usable choice.
3. **Move ordering** tries promising moves early so alpha-beta can discard more branches.
4. **Quiescence search** extends tactical positions with captures and promotions near the depth limit, and considers legal escapes when in check. Without it, the engine might think winning a piece is good immediately before that piece is recaptured. An eight-ply extension cap bounds this extra search, even in long checking sequences.

The evaluation combines material values with positional preferences such as central minor pieces, advanced pawns, a bishop pair, and different king placement in the endgame. A pawn is worth 100 internal score units, called centipawns; a knight is 320, bishop 330, rook 500, and queen 900. The evaluation is a heuristic, not a proof that a position is winning. Checkmate and draw outcomes require explicit handling rather than an ordinary material score.

Each additional search ply explores another individual move. Chess has many legal moves at each position, so naive search grows roughly exponentially with depth. Alpha-beta makes many positions cheaper to search, but does not remove that fundamental limit. A time limit can also cause the completed depth to vary between computers and positions.

## Game endings and deliberate limits

No legal moves while in check means checkmate; no legal moves without check means stalemate.

For repetition, the relevant state includes piece placement, side to move, castling rights, and legal en passant availability. Move counters do not make an otherwise identical position different for this purpose.

The interface automatically draws after three occurrences of the same position, or 100 individual moves without a capture or pawn move. This is a deliberate usability simplification of the claim-based tournament rules.

Insufficient-material detection is conservative. It recognizes king versus king, king plus one bishop or knight versus king, and bishops-only positions where every bishop is on the same square color. It does not assume that two knights versus a king is automatically drawn, and it does not attempt a general proof that no possible move sequence could reach checkmate.

This is a bounded educational search. It can miss a tactic just beyond its horizon, overvalue a positional feature, or fail to understand a long endgame plan. It provides neither Stockfish strength nor tournament-rule completeness. The application also omits network play, chess clocks, opening books, endgame tablebases, and a UCI engine protocol.

## How to check correctness

Rule tests use both focused positions and **perft**: count every legal continuation to a small fixed depth, then compare the total against a known value. Perft exercises combinations of moves and king-safety checks; focused tests explain individual edge cases more clearly.

Engine tests check whether returned moves are legal and whether small tactical or terminal positions are handled sensibly. A move can be legal without being strong, so playing the program is useful in addition to automated checks. UI checks cover the interaction layer, while a person should still inspect the window on their own screen.

## Connections to Software II

- **Encapsulation:** rule state stays inside `Position`; the UI requests legal moves instead of implementing chess rules again.
- **Objects and immutable values:** a move describes an action, and a position describes state at a moment in time.
- **Collections:** lists store legal moves and history; repetition counts summarize previously seen positions.
- **Recursion:** search explores a tree of positions with explicit stopping conditions.
- **Algorithms and tradeoffs:** pruning reduces work, move ordering affects efficiency, and simpler state handling trades some speed for clarity.
- **Event-driven programming:** buttons and mouse clicks update the game through the window rather than through a console input loop.

Good questions to be able to answer in a demonstration:

1. Why can a piece have a geometrically valid move that is not legal?
2. Why does negamax negate the child's score?
3. What lets alpha-beta skip a branch without changing the result of that bounded search?
4. Why does a position need castling and en passant information in addition to the board?
5. What bug could occur if search mutated the actual game's history?
6. What would you improve first, and how would you measure whether it helped?
