package connectfour3d;

public final class Game {

    private static final int MAX_MOVES = 5 * 5 * 5;

    private final Board board = new Board();

    private int currentPlayer = 1;
    private Integer winner = null;
    private boolean finished = false;
    private int moveCount = 0;
    private Move lastMove = null;

    public synchronized Move play(int x, int y) {

        if (finished) {
            throw new IllegalStateException("この対局は終了しています");
        }

        int player = currentPlayer;
        int z = board.drop(x, y, player);

        Move move = new Move(x, y, z, player);

        lastMove = move;
        moveCount++;

        if (board.hasFourFrom(x, y, z)) {
            winner = player;
            finished = true;
        } else if (moveCount == MAX_MOVES) {
            finished = true;
        } else {
            currentPlayer = (player == 1) ? 2 : 1;
        }

        return move;
    }

    public synchronized State state() {
        return new State(
            board.snapshot(),
            currentPlayer,
            winner,
            finished,
            moveCount,
            lastMove
        );
    }

    public record Move(
        int x,
        int y,
        int z,
        int player
    ) {}

    public record State(
        int[][][] cells,
        int currentPlayer,
        Integer winner,
        boolean finished,
        int moveCount,
        Move lastMove
    ) {}
}
