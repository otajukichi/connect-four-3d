package connectfour3d.players;

import connectfour3d.Game;
import java.util.Random;

public interface PlayerStrategy {
    String id();

    String label();

    Move choose(Game.State state, Random random);

    record Move(int x, int y) {}
}
