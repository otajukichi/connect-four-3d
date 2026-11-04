package connectfour3d;

import connectfour3d.players.PlayerStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SimulationMatch {

    public record Result(
        Integer winner,
        int moveCount,
        List<Game.Move> moves
    ) {}

    private SimulationMatch() {}

    public static Result play(
        PlayerStrategy first,
        PlayerStrategy second,
        Random random,
        boolean recordMoves
    ) {
        Game game = new Game();
        Game.State state = game.state();

        List<Game.Move> moves = recordMoves
            ? new ArrayList<>()
            : List.of();

        while (!state.finished()) {
            PlayerStrategy player = state.currentPlayer() == 1
                ? first
                : second;

            PlayerStrategy.Move choice = player.choose(state, random);
            Game.Move move = game.play(choice.x(), choice.y());

            if (recordMoves) {
                moves.add(move);
            }

            state = game.state();
        }

        return new Result(
            state.winner(),
            state.moveCount(),
            recordMoves ? List.copyOf(moves) : List.of()
        );
    }
}
