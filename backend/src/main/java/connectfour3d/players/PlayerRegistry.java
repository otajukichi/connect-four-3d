package connectfour3d.players;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class PlayerRegistry {

    private final Map<String, PlayerStrategy> byId;
    private final List<Option> options;

    public PlayerRegistry(List<PlayerStrategy> implementations) {
        Map<String, PlayerStrategy> found = new TreeMap<>();
        for (PlayerStrategy player : implementations) {
            String id = player.id().trim().toUpperCase(Locale.ROOT);
            if (!id.matches("[A-Z][A-Z0-9_]*") || player.label().isBlank()) {
                throw new IllegalStateException("プレイヤーの名前が不正です: " + id);
            }
            if (found.putIfAbsent(id, player) != null) {
                throw new IllegalStateException("プレイヤーの名前が重複しています: " + id);
            }
        }

        byId = Map.copyOf(found);
        options = found.entrySet().stream()
            .map(e -> new Option(e.getKey(), e.getValue().label()))
            .toList();
    }

    public PlayerStrategy get(String name) {
        if (name == null) {
            throw new IllegalArgumentException("プレイヤーを指定してください");
        }
        PlayerStrategy player = byId.get(name.strip().toUpperCase(Locale.ROOT));
        if (player == null) {
            throw new IllegalArgumentException("プレイヤーが見つかりません: " + name);
        }
        return player;
    }

    public List<Option> options() {
        return options;
    }

    public record Option(String value, String label) {}
}
