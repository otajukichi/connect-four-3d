package connectfour3d.players;

import connectfour3d.Game;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class RandomPlayer implements PlayerStrategy {
    public String id() { return "RANDOM"; }
    public String label() { return "Random（ランダム）"; }

    public Move choose(Game.State state, Random random) {
        List<Integer> options = BoardAnalysis.from(state).legal();
        return BoardAnalysis.move(options.get(random.nextInt(options.size())));
    }
}
