package connectfour3d.players;

import connectfour3d.Game;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;

// Shared board calculations. Each player strategy decides how to use them.
final class BoardAnalysis {

    private static final int[][] DIRECTIONS = {
        {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
        {1, 1, 0}, {1, -1, 0},
        {1, 0, 1}, {1, 0, -1},
        {0, 1, 1}, {0, 1, -1},
        {1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1}
    };

    private static final List<List<int[]>> LINES = buildLines();

    private final int[] cells = new int[125];
    private final int[] heights = new int[25];

    BoardAnalysis(int[][][] source) {
        for (int z = 0; z < 5; z++) {
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 5; x++) {
                    int column = y * 5 + x;
                    int id = z * 25 + column;
                    cells[id] = source[z][y][x];
                    if (cells[id] != 0) heights[column]++;
                }
            }
        }
    }

    static BoardAnalysis from(Game.State state) {
        if (state.finished()) throw new IllegalStateException("対局は終了しています");
        return new BoardAnalysis(state.cells());
    }

    static PlayerStrategy.Move move(int column) {
        return new PlayerStrategy.Move(column % 5, column / 5);
    }

    List<Integer> legal() {
        List<Integer> result = new ArrayList<>();
        for (int c = 0; c < 25; c++) {
            if (heights[c] < 5) result.add(c);
        }
        return result;
    }

    static int best(List<Integer> columns, Random random, IntUnaryOperator scorer) {
        if (columns.isEmpty()) throw new IllegalStateException("置ける場所がありません");
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        int ties = 0;
        for (int column : columns) {
            int score = scorer.applyAsInt(column);
            if (score > bestScore) {
                best = column;
                bestScore = score;
                ties = 1;
            } else if (score == bestScore && random.nextInt(++ties) == 0) {
                best = column;
            }
        }
        return best;
    }

    static int bestScored(List<ScoredMove> moves, Random random) {
        if (moves.isEmpty()) throw new IllegalStateException("置ける場所がありません");
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        int ties = 0;
        for (ScoredMove move : moves) {
            if (move.score() > bestScore) {
                best = move.column();
                bestScore = move.score();
                ties = 1;
            } else if (move.score() == bestScore && random.nextInt(++ties) == 0) {
                best = move.column();
            }
        }
        return best;
    }

    int centerDistance(int column) {
        int x = column % 5 - 2;
        int y = column / 5 - 2;
        return x * x + y * y;
    }

    int heuristicScore(int column, int player) {
        int score = 20 - 3 * centerDistance(column);
        int id = heights[column] * 25 + column;
        for (int[] line : LINES.get(id)) {
            int own = 0;
            int other = 0;
            for (int position : line) {
                int occupant = cells[position];
                if (occupant == player) own++;
                else if (occupant != 0) other++;
            }
            if (other == 0) {
                score += switch (own) {
                    case 3 -> 80;
                    case 2 -> 16;
                    case 1 -> 4;
                    default -> 1;
                };
            }
            if (own == 0) {
                score += switch (other) {
                    case 3 -> 55;
                    case 2 -> 10;
                    case 1 -> 2;
                    default -> 0;
                };
            }
        }
        return score;
    }

    int put(int column, int player) {
        int id = heights[column] * 25 + column;
        cells[id] = player;
        heights[column]++;
        return id;
    }

    void undo(int column) {
        heights[column]--;
        cells[heights[column] * 25 + column] = 0;
    }

    boolean winningAt(int column, int player) {
        if (heights[column] == 5) return false;
        int id = heights[column] * 25 + column;
        for (int[] line : LINES.get(id)) {
            boolean wins = true;
            for (int other : line) {
                if (other != id && cells[other] != player) {
                    wins = false;
                    break;
                }
            }
            if (wins) return true;
        }
        return false;
    }

    int firstWin(int player) {
        for (int column = 0; column < 25; column++) {
            if (winningAt(column, player)) return column;
        }
        return -1;
    }

    int winOrBlock(int player) {
        int mine = firstWin(player);
        return mine >= 0 ? mine : firstWin(3 - player);
    }

    List<Integer> winningColumns(int player) {
        List<Integer> wins = new ArrayList<>();
        for (int column = 0; column < 25; column++) {
            if (winningAt(column, player)) wins.add(column);
        }
        return wins;
    }

    int winningCount(int player) {
        int count = 0;
        for (int column = 0; column < 25; column++) {
            if (winningAt(column, player)) count++;
        }
        return count;
    }

    List<ScoredMove> scoredMoves(int player, int singleThreatBonus) {
        int enemy = 3 - player;
        List<ScoredMove> moves = new ArrayList<>();

        for (int column : legal()) {
            int id = put(column, player);
            List<Integer> myThreats = winningColumns(player);
            int theirThreats = winningCount(enemy);
            int score = positionalScore(id, player);

            if (myThreats.size() >= 2 && theirThreats == 0) score += 300_000;
            else if (myThreats.size() == 1) score += singleThreatBonus;

            if (theirThreats > 0) score -= 1_000_000 * theirThreats;

            moves.add(new ScoredMove(column, score, myThreats, theirThreats));
            undo(column);
        }
        return moves;
    }

    int positionalScore(int playedId, int player) {
        int score = 24 - 5 * centerDistance(playedId % 25);
        for (int[] line : LINES.get(playedId)) {
            int own = 0;
            int other = 0;
            int support = 0;
            for (int id : line) {
                int occupant = cells[id];
                if (occupant == player) own++;
                else if (occupant != 0) other++;
                else support += Math.max(0, id / 25 - heights[id % 25]);
            }
            if (other == 0) {
                int value = switch (own) {
                    case 4 -> 200_000;
                    case 3 -> 200;
                    case 2 -> 32;
                    default -> 5;
                };
                score += value / (1 + support);
            } else if (own == 1) {
                score += switch (other) {
                    case 3 -> 95;
                    case 2 -> 16;
                    case 1 -> 4;
                    default -> 0;
                };
            }
        }
        return score;
    }

    int opponentForkReplies(int opponent, List<Integer> ownThreats) {
        List<Integer> replies = ownThreats.size() == 1 ? ownThreats : legal();
        int forks = 0;
        for (int column : replies) {
            if (heights[column] >= 5) continue;
            put(column, opponent);
            int threats = winningCount(opponent);
            undo(column);
            if (threats >= 2 && ++forks == 2) break;
        }
        return forks;
    }

    boolean forcedForkAfterBlock(int me, int opponent, int block) {
        if (heights[block] == 5) return false;
        put(block, opponent);
        if (winningCount(me) > 0) {
            undo(block);
            return true;
        }

        boolean success = false;
        for (int column : legal()) {
            if (winningAt(column, me)) {
                success = true;
                break;
            }
            put(column, me);
            if (winningCount(me) >= 2 && winningCount(opponent) == 0) {
                success = true;
                undo(column);
                break;
            }
            undo(column);
        }
        undo(block);
        return success;
    }

    record ScoredMove(int column, int score, List<Integer> myThreats, int theirThreats) {}

    private static List<List<int[]>> buildLines() {
        List<List<int[]>> result = new ArrayList<>();
        for (int id = 0; id < 125; id++) result.add(new ArrayList<>());
        for (int[] direction : DIRECTIONS) {
            for (int z = 0; z < 5; z++) {
                for (int y = 0; y < 5; y++) {
                    for (int x = 0; x < 5; x++) {
                        int endX = x + direction[0] * 3;
                        int endY = y + direction[1] * 3;
                        int endZ = z + direction[2] * 3;
                        if (!inside(endX, endY, endZ)) continue;
                        int[] line = new int[4];
                        for (int step = 0; step < 4; step++) {
                            line[step] = (z + direction[2] * step) * 25
                                + (y + direction[1] * step) * 5
                                + x + direction[0] * step;
                        }
                        for (int id : line) result.get(id).add(line);
                    }
                }
            }
        }
        return result;
    }

    private static boolean inside(int x, int y, int z) {
        return x >= 0 && x < 5 && y >= 0 && y < 5 && z >= 0 && z < 5;
    }
}
