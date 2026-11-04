package connectfour3d;

import connectfour3d.players.PlayerRegistry;
import connectfour3d.players.PlayerStrategy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final Map<String, Game> games = new ConcurrentHashMap<>();
    private final PlayerRegistry playerRegistry;

    public GameController(PlayerRegistry playerRegistry) {
        this.playerRegistry = playerRegistry;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedGame create() {

        String id = UUID.randomUUID().toString();
        Game game = new Game();

        games.put(id, game);

        return new CreatedGame(id, game.state());
    }

    @GetMapping("/{id}")
    public Game.State get(@PathVariable String id) {
        return findGame(id).state();
    }

    @PostMapping("/{id}/moves")
    public Game.State move(
        @PathVariable String id,
        @RequestBody MoveRequest request
    ) {

        if (request == null || request.x() == null || request.y() == null) {
            throw new IllegalArgumentException("xとyを指定してください");
        }

        Game game = findGame(id);
        game.play(request.x(), request.y());

        return game.state();
    }

    @PostMapping("/{id}/auto-move")
    public Game.State autoMove(
        @PathVariable String id,
        @RequestBody AutoMoveRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("プレイヤーの種類を指定してください");
        }

        PlayerStrategy player = playerRegistry.get(request.strategy());
        Game game = findGame(id);

        synchronized (game) {
            Game.State state = game.state();
            PlayerStrategy.Move choice = player.choose(state, new java.util.Random());
            game.play(choice.x(), choice.y());
            return game.state();
        }
    }

    @PostMapping("/{id}/reset")
    public Game.State reset(@PathVariable String id) {

        findGame(id);

        Game game = new Game();
        games.put(id, game);

        return game.state();
    }

    private Game findGame(String id) {

        Game game = games.get(id);

        if (game == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "対局が見つかりません"
            );
        }

        return game;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidMove(
        IllegalArgumentException exception
    ) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleInvalidState(
        IllegalStateException exception
    ) {
        return Map.of("error", exception.getMessage());
    }

    public record MoveRequest(Integer x, Integer y) {}
    public record AutoMoveRequest(String strategy) {}

    public record CreatedGame(String id, Game.State state) {}
}
