package connectfour3d.players;

import connectfour3d.Game;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Component;

/** 5×5×5・四目・下から積むルール用。状態は choose の呼び出し内で完結する。 */
@Component
public final class Smart2Player implements PlayerStrategy {
    private static final int SIZE = 5;
    private static final int COLUMNS = 25;
    private static final int[][] LINES = buildLines();
    private static final int[][] TOUCHING = buildTouching();
    private static final int[] LINE_VALUE = {0, 5, 48, 420, 100_000};
    private static final int PROOF_ROUNDS = 3;
    private static final int ATTACK_BUDGET = 1_600;
    private static final int DEFENCE_BUDGET = 1_200;
    private static final int LOST = 1_000_000;

    @Override
    public String id() {
        return "SMART2";
    }

    @Override
    public String label() {
        return "Smart2";
    }

    @Override
    public Move choose(Game.State state, Random random) {
        if (state.finished()) {
            throw new IllegalStateException("対局は終了しています");
        }
        Position board = new Position(state);
        int me = state.currentPlayer();
        int enemy = 3 - me;
        int wins = board.winningMoves(me);
        if (wins != 0) return move(pick(wins, random));
        if (board.legal == 0) {
            throw new IllegalStateException("置ける場所がありません");
        }

        int safe = board.safeMoves(me);
        if (Integer.bitCount(safe) == 1) {
            return move(Integer.numberOfTrailingZeros(safe));
        }
        if (safe == 0) {
            // 防ぎ切れない局面でも合法手を返す。勝利保証とは区別する。
            return move(board.ordered(board.legal, me)[0]);
        }

        int[] candidates = board.ordered(safe, me);
        // 攻めの形が一つに決まっていなくても、全ての応手で勝てるか確認する。
        for (int rounds = 0; rounds < PROOF_ROUNDS; rounds++) {
            for (int column : candidates) {
                board.put(column, me);
                boolean quietAvailable = board.winningMoves(me) != 0;
                boolean proven = allDefencesFail(
                    board, me, rounds, quietAvailable, new Budget(ATTACK_BUDGET));
                board.undo(column);
                if (proven) return move(column);
            }
        }

        long bestScore = Long.MIN_VALUE;
        int best = candidates[0];
        int ties = 0;
        for (int column : candidates) {
            int orderScore = board.priority(column, me);
            board.put(column, me);
            boolean losing = canForceWin(
                board, enemy, PROOF_ROUNDS, true, new Budget(DEFENCE_BUDGET));

            // 静かな局面は盤面全体と相手の最善応手で評価する。
            // 勝ちの証明と評価値は混ぜない。証明済みの負け手は必ず後回し。
            int positionScore = board.evaluate(me);
            int replyScore = worstReply(board, me);
            long score = 3L * replyScore + positionScore + orderScore / 4;
            if (losing) score -= 100_000_000L;
            board.undo(column);

            if (score > bestScore) {
                bestScore = score;
                best = column;
                ties = 1;
            } else if (score == bestScore && random.nextInt(++ties) == 0) {
                best = column;
            }
        }
        return move(best);
    }

    // true だけが証明済み。false は「未証明」であり、引き分け・安全の証明ではない。
    private static boolean canForceWin(
        Position board, int attacker, int rounds, boolean allowPreparation, Budget budget
    ) {
        if (board.winningMoves(attacker) != 0) return true;
        if (board.legal == 0 || rounds == 0 || budget.left <= 0) return false;
        int safe = board.safeMoves(attacker);
        if (safe == 0) return false;
        if (!allowPreparation) safe &= board.attackCandidates(attacker);
        int defender = 3 - attacker;

        for (int column : board.ordered(safe, attacker)) {
            if (!budget.take()) return false;
            boolean advancingStack = board.advancesStack(column, attacker);
            board.put(column, attacker);
            boolean forcing = board.winningMoves(attacker) != 0;
            boolean proven = false;
            if (board.winningMoves(defender) == 0 && board.legal != 0) {
                if (board.safeMoves(defender) == 0) {
                    proven = true;
                } else if (forcing || allowPreparation || advancingStack) {
                    proven = allDefencesFail(
                        board, attacker, rounds - 1, allowPreparation && forcing, budget);
                }
            }
            board.undo(column);
            if (proven) return true;
        }
        return false;
    }

    private static boolean allDefencesFail(
        Position board, int attacker, int rounds, boolean quietAvailable, Budget budget
    ) {
        int defender = 3 - attacker;
        // こちらの二重脅威より、手番側の即勝利が先。
        if (board.winningMoves(defender) != 0 || board.legal == 0) return false;
        int replies = board.safeMoves(defender);
        if (replies == 0) return true;
        if (rounds == 0 || budget.left <= 0) return false;

        // 即負けする応手以外を全て調べる。一つでも未証明なら勝ちと断定しない。
        for (int column : board.ordered(replies, defender)) {
            if (!budget.take()) return false;
            board.put(column, defender);
            boolean proven = canForceWin(board, attacker, rounds, quietAvailable, budget);
            board.undo(column);
            if (!proven) return false;
        }
        return true;
    }

    private static int worstReply(Position board, int me) {
        int enemy = 3 - me;
        if (board.legal == 0) return 0;
        int replies = board.safeMoves(enemy);
        if (replies == 0) return LOST;
        int worst = Integer.MAX_VALUE;
        while (replies != 0) {
            int column = Integer.numberOfTrailingZeros(replies);
            replies &= replies - 1;
            board.put(column, enemy);
            int score = settledValue(board, me, me, 4);
            board.undo(column);
            worst = Math.min(worst, score);
        }
        return worst;
    }

    // 強制された防御だけを延長し、止めた直後の足場・反撃も評価に含める。
    private static int settledValue(Position board, int me, int turn, int remaining) {
        if (board.winningMoves(turn) != 0) return turn == me ? LOST : -LOST;
        if (board.legal == 0) return 0;
        int safe = board.safeMoves(turn);
        if (safe == 0) return turn == me ? -LOST : LOST;
        if (remaining > 0 && Integer.bitCount(safe) == 1) {
            int column = Integer.numberOfTrailingZeros(safe);
            board.put(column, turn);
            int value = settledValue(board, me, 3 - turn, remaining - 1);
            board.undo(column);
            return value;
        }
        int nextPlan = 0;
        int choices = safe;
        while (choices != 0) {
            int column = Integer.numberOfTrailingZeros(choices);
            choices &= choices - 1;
            nextPlan = Math.max(nextPlan, board.priority(column, turn));
        }
        return board.evaluate(me) + (turn == me ? nextPlan : -nextPlan);
    }

    private static Move move(int column) {
        return new Move(column % SIZE, column / SIZE);
    }

    private static int pick(int mask, Random random) {
        int skip = random.nextInt(Integer.bitCount(mask));
        while (skip-- > 0) mask &= mask - 1;
        return Integer.numberOfTrailingZeros(mask);
    }

    private static final class Budget {
        int left;
        Budget(int left) { this.left = left; }
        boolean take() { return left-- > 0; }
    }

    private static final class Position {
        final int[] cells = new int[125];
        final int[] heights = new int[COLUMNS];
        final int[][] count = new int[3][LINES.length];
        final int[] emptyXor = new int[LINES.length];
        final int[][] threats = new int[3][125];
        final int[][] routes = new int[3][125];
        final int[] values = new int[3];
        int legal = (1 << COLUMNS) - 1;

        Position(Game.State state) {
            for (int line = 0; line < LINES.length; line++) {
                for (int id : LINES[line]) emptyXor[line] ^= id;
            }
            for (int column = 0; column < COLUMNS; column++) {
                boolean gap = false;
                for (int z = 0; z < SIZE; z++) {
                    int player = state.cells()[z][column / SIZE][column % SIZE];
                    if (player == 0) {
                        gap = true;
                    } else {
                        if (gap || (player != 1 && player != 2)) {
                            throw new IllegalArgumentException("盤面が積み上げルールと一致しません");
                        }
                        put(column, player);
                    }
                }
            }
        }

        int put(int column, int player) {
            int id = heights[column] * COLUMNS + column;
            change(id, player, 1);
            cells[id] = player;
            if (++heights[column] == SIZE) legal &= ~(1 << column);
            return id;
        }

        void undo(int column) {
            int id = --heights[column] * COLUMNS + column;
            change(id, cells[id], -1);
            cells[id] = 0;
            legal |= 1 << column;
        }

        private void change(int id, int player, int delta) {
            for (int line : TOUCHING[id]) {
                adjustThreat(line, -1);
                count[player][line] += delta;
                emptyXor[line] ^= id;
                adjustThreat(line, 1);
            }
        }

        private void adjustThreat(int line, int delta) {
            if (count[1][line] == 3 && count[2][line] == 0) {
                threats[1][emptyXor[line]] += delta;
            } else if (count[2][line] == 3 && count[1][line] == 0) {
                threats[2][emptyXor[line]] += delta;
            }
        }

        int winningMoves(int player) {
            int result = 0;
            int columns = legal;
            while (columns != 0) {
                int column = Integer.numberOfTrailingZeros(columns);
                columns &= columns - 1;
                int id = heights[column] * COLUMNS + column;
                if (threats[player][id] != 0) result |= 1 << column;
            }
            return result;
        }

        // 即勝利があればその手。なければ、直後の敗北を避ける全ての手。
        int safeMoves(int player) {
            int wins = winningMoves(player);
            if (wins != 0) return wins;
            int enemy = 3 - player;
            int enemyWins = winningMoves(enemy);
            if ((enemyWins & (enemyWins - 1)) != 0) return 0;
            int candidates = enemyWins != 0 ? enemyWins : legal;
            int result = candidates;
            while (candidates != 0) {
                int column = Integer.numberOfTrailingZeros(candidates);
                candidates &= candidates - 1;
                int next = (heights[column] + 1) * COLUMNS + column;
                // 今置く駒が、相手の四目の完成位置への足場にならないか。
                if (next < cells.length && threats[enemy][next] != 0) {
                    result &= ~(1 << column);
                }
            }
            return result;
        }

        int[] ordered(int mask, int player) {
            int[] result = new int[Integer.bitCount(mask)];
            int[] scores = new int[result.length];
            int length = 0;
            while (mask != 0) {
                int column = Integer.numberOfTrailingZeros(mask);
                mask &= mask - 1;
                int score = priority(column, player);
                int at = length++;
                while (at > 0 && scores[at - 1] < score) {
                    result[at] = result[at - 1];
                    scores[at] = scores[at - 1];
                    at--;
                }
                result[at] = column;
                scores[at] = score;
            }
            return result;
        }

        int priority(int column, int player) {
            int id = heights[column] * COLUMNS + column;
            int score = TOUCHING[id].length;
            int enemy = 3 - player;
            for (int line : TOUCHING[id]) {
                if (count[enemy][line] == 0) {
                    score += LINE_VALUE[count[player][line] + 1] / (1 + support(line));
                }
                if (count[player][line] == 0) {
                    score += LINE_VALUE[count[enemy][line]] / 2;
                }
            }
            return score;
        }

        int support(int line) {
            // 同じ列を共有する線は垂直線だけ。線の空きマス同士で支え合える。
            if (LINES[line][0] % COLUMNS == LINES[line][1] % COLUMNS) {
                for (int id : LINES[line]) {
                    if (cells[id] == 0) return Math.max(0, id / COLUMNS - heights[id % COLUMNS]);
                }
                return 0;
            }
            int missing = 0;
            for (int id : LINES[line]) {
                if (cells[id] == 0) {
                    missing += Math.max(0, id / COLUMNS - heights[id % COLUMNS]);
                }
            }
            return missing;
        }

        boolean advancesStack(int column, int player) {
            for (int z = heights[column] + 1; z + 1 < SIZE; z++) {
                int id = z * COLUMNS + column;
                if (threats[player][id] != 0 && threats[player][id + COLUMNS] != 0) return true;
            }
            return false;
        }

        int attackCandidates(int player) {
            int result = 0;
            // 新しい完成位置は、二目の線に三つ目を置くことで生じる。
            for (int line = 0; line < LINES.length; line++) {
                if (count[player][line] != 2 || count[3 - player][line] != 0) continue;
                for (int id : LINES[line]) {
                    int column = id % COLUMNS;
                    if (cells[id] == 0 && id / COLUMNS == heights[column]) result |= 1 << column;
                }
            }
            int available = legal;
            while (available != 0) {
                int column = Integer.numberOfTrailingZeros(available);
                available &= available - 1;
                int above = (heights[column] + 1) * COLUMNS + column;
                // 足場で既存の完成位置を開く手と、二段の脅威へ近づく手。
                // 二段上の完成位置や最上段への着手で、最後の安全な列を消す手も残す。
                if (heights[column] == SIZE - 1 || threats[player][above] != 0
                    || (above + COLUMNS < cells.length && threats[player][above + COLUMNS] != 0)
                    || advancesStack(column, player)) {
                    result |= 1 << column;
                }
            }
            return result;
        }

        int evaluate(int me) {
            Arrays.fill(routes[1], 0);
            Arrays.fill(routes[2], 0);
            Arrays.fill(values, 0);
            for (int line = 0; line < LINES.length; line++) {
                int a = count[1][line], b = count[2][line];
                // 相手の駒を含む線は、その側の勝ち筋にならない。
                if ((a != 0 && b != 0) || a + b == 0) continue;
                int player = a != 0 ? 1 : 2;
                int stones = a + b;
                values[player] += LINE_VALUE[stones] / (1 + support(line));
                if (stones == 2) {
                    for (int id : LINES[line]) {
                        if (cells[id] == 0) routes[player][id]++;
                    }
                }
            }
            for (int column = 0; column < COLUMNS; column++) {
                for (int z = heights[column]; z < SIZE; z++) {
                    int id = z * COLUMNS + column;
                    int distance = z - heights[column];
                    for (int player = 1; player <= 2; player++) {
                        // 複数方向に育つ二目。完成位置を共有するだけの三目は水増ししない。
                        int n = routes[player][id];
                        values[player] += 9 * n * (n - 1) / (1 + distance);
                        if (threats[player][id] == 0) continue;
                        values[player] += 100 / (1 + distance);
                        if (z + 1 < SIZE && threats[player][id + COLUMNS] != 0) {
                            values[player] += 300 / (1 + distance);
                        }
                        // 同じ列の下にある相手の完成位置は、到達の障害として扱う。
                        for (int lower = heights[column]; lower < z; lower++) {
                            if (threats[3 - player][lower * COLUMNS + column] != 0) {
                                values[player] -= 100 / (1 + distance);
                                break;
                            }
                        }
                    }
                }
            }
            // 単なる合法手ではなく、即敗北を避けられる手の余裕。
            values[1] += 18 * Integer.bitCount(safeMoves(1));
            values[2] += 18 * Integer.bitCount(safeMoves(2));
            return values[me] - values[3 - me];
        }
    }

    private static int[][] buildLines() {
        int[][] directions = {
            {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
            {1, 1, 0}, {1, -1, 0}, {1, 0, 1}, {1, 0, -1},
            {0, 1, 1}, {0, 1, -1},
            {1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1}
        };
        List<int[]> result = new ArrayList<>();
        for (int[] d : directions) {
            for (int z = 0; z < SIZE; z++) for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int ex = x + 3 * d[0], ey = y + 3 * d[1], ez = z + 3 * d[2];
                    if (ex < 0 || ex >= SIZE || ey < 0 || ey >= SIZE || ez < 0 || ez >= SIZE) continue;
                    int[] line = new int[4];
                    for (int n = 0; n < 4; n++) {
                        line[n] = (z + n * d[2]) * COLUMNS + (y + n * d[1]) * SIZE + x + n * d[0];
                    }
                    result.add(line);
                }
            }
        }
        return result.toArray(int[][]::new);
    }

    private static int[][] buildTouching() {
        int[] sizes = new int[125];
        for (int[] line : LINES) for (int id : line) sizes[id]++;
        int[][] result = new int[125][];
        for (int id = 0; id < 125; id++) result[id] = new int[sizes[id]];
        Arrays.fill(sizes, 0);
        for (int line = 0; line < LINES.length; line++) {
            for (int id : LINES[line]) result[id][sizes[id]++] = line;
        }
        return result;
    }
}
