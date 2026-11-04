package connectfour3d;

import connectfour3d.players.PlayerRegistry;
import connectfour3d.players.PlayerStrategy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("terminal")
public final class TerminalGame implements CommandLineRunner {

    private final PlayerRegistry registry;
    private final BufferedReader input = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8)
    );
    private final Random random = new Random();

    public TerminalGame(PlayerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\nコネクトフォー3D");
        System.out.println("x: 左から0〜4 / y: 上から0〜4 / z: 下から0〜4");
        System.out.println("入力中に q を入力するとメニューへ戻ります。\n");

        while (true) {
            System.out.println("1. 人 vs 人");
            System.out.println("2. 人 vs CPU");
            System.out.println("3. CPU vs CPU");
            System.out.println("4. 終了");
            Integer mode = askNumber("選択 > ", 1, 4);
            if (mode == null || mode == 4) {
                return;
            }

            try {
                switch (mode) {
                    case 1 -> play(
                        new PlayerStrategy[] {null, null},
                        new String[] {"ユーザー１", "ユーザー２"}
                    );
                    case 2 -> humanVsCpu();
                    case 3 -> cpuVsCpu();
                    default -> { }
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("対局エラー: " + e.getMessage());
            }
            System.out.println();
        }
    }

    private void humanVsCpu() throws IOException {
        PlayerStrategy cpu = choosePlayer("対戦するCPU");
        if (cpu == null) return;

        System.out.println("1. 人が先手");
        System.out.println("2. 人が後手");
        Integer side = askNumber("選択 > ", 1, 2);
        if (side == null) return;

        if (side == 1) {
            play(
                new PlayerStrategy[] {null, cpu},
                new String[] {"ユーザー", name(cpu)}
            );
        } else {
            play(
                new PlayerStrategy[] {cpu, null},
                new String[] {name(cpu), "ユーザー"}
            );
        }
    }

    private void play(PlayerStrategy[] players, String[] names) throws IOException {
        Game game = new Game();
        Game.State state = game.state();

        System.out.println("\nX: " + names[0] + " / O: " + names[1]);
        printBoard(state.cells());

        while (!state.finished()) {
            int turn = state.currentPlayer() - 1;
            PlayerStrategy cpu = players[turn];
            int x;
            int y;

            if (cpu == null) {
                System.out.println("\n" + names[turn] + "の手番");
                Integer enteredX = askNumber("x > ", 0, 4);
                if (enteredX == null) return;
                Integer enteredY = askNumber("y > ", 0, 4);
                if (enteredY == null) return;
                x = enteredX;
                y = enteredY;
            } else {
                PlayerStrategy.Move move = cpu.choose(state, random);
                x = move.x();
                y = move.y();
            }

            try {
                Game.Move move = game.play(x, y);
                System.out.printf("\n%s: (%d, %d, %d)%n", names[turn], x, y, move.z());
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println(e.getMessage());
                if (cpu != null) return;
                continue;
            }

            state = game.state();
            printBoard(state.cells());
        }

        System.out.println(state.winner() == null
            ? "\n引き分け"
            : "\n" + names[state.winner() - 1] + "の勝利");
        System.out.println("手数: " + state.moveCount());
    }

    private void cpuVsCpu() throws IOException, InterruptedException {
        PlayerStrategy cpu1 = choosePlayer("CPU1");
        if (cpu1 == null) return;
        PlayerStrategy cpu2 = choosePlayer("CPU2");
        if (cpu2 == null) return;

        System.out.println("1. CPU1を先手に固定");
        System.out.println("2. CPU2を先手に固定");
        System.out.println("3. ランダム（試合ごと）");
        Integer order = askNumber("先手 > ", 1, 3);
        if (order == null) return;

        Integer count = askNumber("試合数 (1〜100000) > ", 1, 100_000);
        if (count == null) return;

        System.out.println("1. 盤面を表示しない（高速）");
        System.out.println("2. 毎手表示（高速）");
        System.out.println("3. 毎手表示（通常）");
        System.out.println("4. 最終盤面だけ表示");
        System.out.println("5. 一手ずつ（Enterで進む）");
        Integer display = askNumber("表示方法 > ", 1, 5);
        if (display == null) return;

        long seed = System.nanoTime();
        Random matchRandom = new Random(seed);
        int cpu1Wins = 0;
        int cpu2Wins = 0;
        int draws = 0;
        int finished = 0;
        long totalMoves = 0;
        long started = System.nanoTime();

        for (int i = 0; i < count; i++) {
            boolean cpu1First = order == 1 || (order == 3 && matchRandom.nextBoolean());
            PlayerStrategy first = cpu1First ? cpu1 : cpu2;
            PlayerStrategy second = cpu1First ? cpu2 : cpu1;

            SimulationMatch.Result result = SimulationMatch.play(
                first, second, matchRandom, display != 1
            );

            if (display != 1) {
                String[] names = cpu1First
                    ? new String[] {"CPU1（" + name(cpu1) + "）", "CPU2（" + name(cpu2) + "）"}
                    : new String[] {"CPU2（" + name(cpu2) + "）", "CPU1（" + name(cpu1) + "）"};
                if (!showMatch(i + 1, result, names, display)) break;
            }

            if (result.winner() == null) {
                draws++;
            } else if ((result.winner() == 1) == cpu1First) {
                cpu1Wins++;
            } else {
                cpu2Wins++;
            }
            totalMoves += result.moveCount();
            finished++;
        }

        if (finished == 0) {
            System.out.println("対戦を中断しました");
            return;
        }

        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf(Locale.ROOT, "%n対戦結果（%d試合）%n", finished);
        System.out.printf(Locale.ROOT, "CPU1（%s）: %d勝 %.1f%%%n",
            name(cpu1), cpu1Wins, cpu1Wins * 100.0 / finished);
        System.out.printf(Locale.ROOT, "CPU2（%s）: %d勝 %.1f%%%n",
            name(cpu2), cpu2Wins, cpu2Wins * 100.0 / finished);
        System.out.println("引き分け: " + draws);
        System.out.printf(Locale.ROOT, "平均手数: %.1f%n", totalMoves / (double) finished);
        System.out.printf(Locale.ROOT, "実行時間: %.2f秒%n", seconds);
        System.out.println("乱数シード: " + seed);
    }

    private boolean showMatch(
        int number, SimulationMatch.Result result, String[] names, int display
    ) throws IOException, InterruptedException {
        System.out.println("\n第" + number + "試合  X: " + names[0] + " / O: " + names[1]);

        Game replay = new Game();
        if (display != 4) printBoard(replay.state().cells());

        for (Game.Move move : result.moves()) {
            if (display == 5) {
                System.out.print("Enterで次の一手（q: 中断） > ");
                System.out.flush();
                String command = input.readLine();
                if (command == null || command.trim().equalsIgnoreCase("q")) return false;
            }
            replay.play(move.x(), move.y());
            if (display == 4) continue;

            System.out.printf("%s: (%d, %d, %d)%n",
                names[move.player() - 1], move.x(), move.y(), move.z());
            printBoard(replay.state().cells());
            if (display != 5) Thread.sleep(display == 2 ? 20 : 150);
        }

        if (display == 4) printBoard(replay.state().cells());
        System.out.println(result.winner() == null
            ? "引き分け"
            : names[result.winner() - 1] + "の勝利");
        return true;
    }

    private void printBoard(int[][][] cells) {
        System.out.println();
        StringBuilder heading = new StringBuilder();
        for (int z = 4; z >= 0; z--) {
            if (z < 4) heading.append("         ");
            heading.append("高さ ").append(z);
        }
        System.out.println(heading);

        for (int y = 0; y < 5; y++) {
            StringBuilder row = new StringBuilder();
            for (int z = 4; z >= 0; z--) {
                if (z < 4) row.append("         ");
                for (int x = 0; x < 5; x++) {
                    row.append(switch (cells[z][y][x]) {
                        case 1 -> 'X';
                        case 2 -> 'O';
                        default -> '.';
                    });
                }
            }
            System.out.println(row);
        }
        System.out.println();
    }

    private PlayerStrategy choosePlayer(String title) throws IOException {
        List<PlayerRegistry.Option> options = registry.options();
        if (options.isEmpty()) {
            System.out.println("CPUが登録されていません");
            return null;
        }

        System.out.println("\n" + title);
        for (int i = 0; i < options.size(); i++) {
            System.out.printf("%d. %s%n", i + 1, options.get(i).label());
        }
        Integer choice = askNumber("選択 > ", 1, options.size());
        return choice == null ? null : registry.get(options.get(choice - 1).value());
    }

    private Integer askNumber(String prompt, int min, int max) throws IOException {
        while (true) {
            System.out.print(prompt);
            System.out.flush();
            String line = input.readLine();
            if (line == null || line.trim().equalsIgnoreCase("q")) return null;

            try {
                int value = Integer.parseInt(line.trim());
                if (value >= min && value <= max) return value;
            } catch (NumberFormatException ignored) {
                // 数字以外は再入力を求める。
            }
            System.out.printf("%d〜%dの数字で入力してください%n", min, max);
        }
    }

    private static String name(PlayerStrategy player) {
        String label = player.label();
        int bracket = label.indexOf('（');
        if (bracket < 0) bracket = label.indexOf('(');
        return bracket < 0 ? label : label.substring(0, bracket).trim();
    }
}
