# 自動プレイヤーの追加方法

このフォルダにJavaファイルを1つ追加します。`PlayerStrategy` を実装し、クラスに `@Component` を付けると、サーバー起動時に自動で登録されます。

```java
package connectfour3d.players;

import connectfour3d.Game;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class SamplePlayer implements PlayerStrategy {
    public String id() { return "SAMPLE"; }
    public String label() { return "サンプル"; }

    public Move choose(Game.State state, Random random) {
        BoardAnalysis board = BoardAnalysis.from(state);
        var choices = board.legal();
        int column = choices.get(random.nextInt(choices.size()));
        return BoardAnalysis.move(column);
    }
}
```

- `id()` は他のプレイヤーと重複しない英字・数字・アンダースコアの名前にしてください。
- `state.cells()[z][y][x]` は、空きが0、プレイヤーが1または2です。
- `choose()` は盤面上の `(x, y)` を返します。高さ `z` はゲームが自動で決めます。
- `random` を使えば、同じ乱数の種で対戦を再現できます。
- 新しいプレイヤーを追加したらJavaサーバーを再起動してください。画面のプレイヤー選択欄にも追加されます。
