package connectfour3d;

import connectfour3d.players.PlayerRegistry;
import connectfour3d.players.PlayerStrategy;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerRegistry playerRegistry;

    public PlayerController(PlayerRegistry playerRegistry) {
        this.playerRegistry = playerRegistry;
    }

    @GetMapping("/strategies")
    public List<PlayerRegistry.Option> strategies() {
        return playerRegistry.options();
    }

    @PostMapping("/match")
    public SimulationMatch.Result match(@RequestBody MatchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("プレイヤーの種類を指定してください");
        }

        PlayerStrategy first = playerRegistry.get(request.first());
        PlayerStrategy second = playerRegistry.get(request.second());

        long seed = request.seed() != null
            ? request.seed()
            : System.nanoTime();

        return SimulationMatch.play(
            first,
            second,
            new Random(seed),
            true
        );
    }

    @PostMapping("/simulate")
    public Statistics simulate(@RequestBody SimulationRequest request) {
        if (
            request == null ||
            request.games() == null ||
            request.games() < 1 ||
            request.games() > 100_000
        ) {
            throw new IllegalArgumentException(
                "試合数は1〜100000で指定してください"
            );
        }

        PlayerStrategy a = playerRegistry.get(request.a());
        PlayerStrategy b = playerRegistry.get(request.b());

        long seed = request.seed() != null
            ? request.seed()
            : System.nanoTime();

        String order = request.order() != null ? request.order() : "alternate";
        if (!List.of("a", "b", "random", "alternate").contains(order)) {
            throw new IllegalArgumentException("先手の設定が不正です");
        }

        int aWins = 0;
        int bWins = 0;
        int draws = 0;
        long totalMoves = 0;

        long start = System.nanoTime();

        for (int i = 0; i < request.games(); i++) {
            boolean aFirst = startsFirst(order, seed, i);

            SimulationMatch.Result result = SimulationMatch.play(
                aFirst ? a : b,
                aFirst ? b : a,
                new Random(seed + i),
                false
            );

            if (result.winner() == null) {
                draws++;
            } else if ((result.winner() == 1) == aFirst) {
                aWins++;
            } else {
                bWins++;
            }

            totalMoves += result.moveCount();
        }

        long elapsedMillis =
            (System.nanoTime() - start) / 1_000_000;

        return new Statistics(
            request.games(),
            aWins,
            bWins,
            draws,
            (double) totalMoves / request.games(),
            elapsedMillis,
            seed
        );
    }

    // フロント側でも同じ整数演算で先後を決める。
    private static boolean startsFirst(String order, long seed, int index) {
        if ("a".equals(order)) return true;
        if ("b".equals(order)) return false;
        if ("alternate".equals(order)) return index % 2 == 0;

        int n = (int) seed ^ index;
        n = (n ^ (n >>> 16)) * 0x45d9f3b;
        n = (n ^ (n >>> 16)) * 0x45d9f3b;
        n ^= n >>> 16;
        return (n & 1) == 0;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalid(
        IllegalArgumentException exception
    ) {
        return Map.of("error", exception.getMessage());
    }

    public record MatchRequest(
        String first,
        String second,
        Long seed
    ) {}

    public record SimulationRequest(
        String a,
        String b,
        Integer games,
        Long seed,
        String order
    ) {}

    public record Statistics(
        int games,
        int aWins,
        int bWins,
        int draws,
        double averageMoves,
        long elapsedMillis,
        long seed
    ) {}
}
