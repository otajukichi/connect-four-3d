package connectfour3d.players;

import connectfour3d.Game;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class SmartPlayer implements PlayerStrategy {

    @Override
    public String id() {
        return "SMART";
    }

    @Override
    public String label() {
        return "Smart";
    }

    @Override
    public Move choose(Game.State state, Random random) {
        BoardAnalysis board = BoardAnalysis.from(state);
        int me = state.currentPlayer();
        int opponent = 3 - me;

        List<Integer> wins = board.winningColumns(me);
        if (!wins.isEmpty()) {
            return BoardAnalysis.move(wins.get(random.nextInt(wins.size())));
        }

        List<Integer> blocks = board.winningColumns(opponent);
        if (blocks.size() == 1) {
            return BoardAnalysis.move(blocks.get(0));
        }

        List<BoardAnalysis.ScoredMove> candidates = board.scoredMoves(me, 2400);
        int bestColumn = -1;
        long bestScore = Long.MIN_VALUE;
        int ties = 0;

        for (BoardAnalysis.ScoredMove candidate : candidates) {
            int column = candidate.column();
            long score = candidate.score();

            if (candidate.theirThreats() > 0) {
                // この手の直後に相手が四目を作れる。
                score -= 3_000_000L;
            } else if (candidate.myThreats().size() >= 2) {
                // 相手が一度に両方を止められない勝ち筋。
                score += 1_000_000L;
            } else {
                board.put(column, me);

                if (candidate.myThreats().size() == 1 &&
                    board.forcedForkAfterBlock(
                        me, opponent, candidate.myThreats().get(0))) {
                    score += 350_000L;
                }

                score -= opponentPressure(
                    board, me, opponent, candidate.myThreats());
                score += futureForkPotential(board, me, opponent);

                board.undo(column);
            }

            if (score > bestScore) {
                bestScore = score;
                bestColumn = column;
                ties = 1;
            } else if (score == bestScore && random.nextInt(++ties) == 0) {
                bestColumn = column;
            }
        }

        if (bestColumn < 0) {
            throw new IllegalStateException("置ける場所がありません");
        }
        return BoardAnalysis.move(bestColumn);
    }

    // 相手が次に置ける位置だけを見る。
    private long opponentPressure(
        BoardAnalysis board,
        int me,
        int opponent,
        List<Integer> myThreats
    ) {
        List<Integer> replies = myThreats.size() == 1
            ? myThreats
            : board.legal();

        int forks = 0;
        int singleThreats = 0;
        int forcedForks = 0;
        int bestPosition = 0;

        for (int column : replies) {
            if (board.winningAt(column, opponent)) {
                return 3_000_000L;
            }

            int played = board.put(column, opponent);
            int threats = board.winningCount(opponent);

            if (threats >= 2 && board.winningCount(me) == 0) {
                forks++;
            } else if (threats == 1) {
                singleThreats++;
                if (board.winningCount(me) == 0) {
                    int block = board.winningColumns(opponent).get(0);
                    if (board.forcedForkAfterBlock(opponent, me, block)) {
                        forcedForks++;
                    }
                }
            }

            bestPosition = Math.max(
                bestPosition, board.positionalScore(played, opponent));
            board.undo(column);
        }

        return 230_000L * forks
            + 130_000L * forcedForks
            + 260L * singleThreats
            + bestPosition / 3;
    }

    // 次の自分の一手で二重の勝ち筋を作れる位置を評価する。
    private long futureForkPotential(BoardAnalysis board, int me, int opponent) {
        int potential = 0;
        for (int column : board.legal()) {
            board.put(column, me);
            if (board.winningCount(me) >= 2 &&
                board.winningCount(opponent) == 0) {
                potential++;
            }
            board.undo(column);
        }
        return 260L * potential;
    }
}
