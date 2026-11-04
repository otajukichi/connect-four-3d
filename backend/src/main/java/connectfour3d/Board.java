package connectfour3d;

public class Board {

    private final int[][][] cells = new int[5][5][5];

    public int drop(int x, int y, int player) {

        if (x < 0 || x >= 5 || y < 0 || y >= 5) {
            throw new IllegalArgumentException("盤面の範囲外です");
        }

        if (player != 1 && player != 2) {
            throw new IllegalArgumentException("プレイヤーが不正です");
        }

        for (int z = 0; z < 5; z++) {

            if (cells[z][y][x] == 0) {
                cells[z][y][x] = player;
                return z;
            }
        }

        throw new IllegalStateException("この列は満杯です");
    }

    private boolean isInside(int x, int y, int z) {
        return x >= 0 && x < 5
            && y >= 0 && y < 5
            && z >= 0 && z < 5;
    }

    private int countDirection(int x, int y, int z,
                               int dx, int dy, int dz, int player) {
        int count = 0;

        x += dx;
        y += dy;
        z += dz;

        while (isInside(x, y, z) && cells[z][y][x] == player) {
            count++;
            x += dx;
            y += dy;
            z += dz;
        }

        return count;
    }

    public boolean hasFourFrom(int x, int y, int z) {
        if (!isInside(x, y, z) || cells[z][y][x] == 0) {
            return false;
        }

        int player = cells[z][y][x];
        int[][] directions = {
            {1, 0, 0}, {0, 1, 0}, {0, 0, 1},
            {1, 1, 0}, {1, -1, 0},
            {1, 0, 1}, {1, 0, -1},
            {0, 1, 1}, {0, 1, -1},
            {1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1}
        };

        for (int[] direction : directions) {
            int dx = direction[0];
            int dy = direction[1];
            int dz = direction[2];

            int count = 1
                + countDirection(x, y, z, dx, dy, dz, player)
                + countDirection(x, y, z, -dx, -dy, -dz, player);

            if (count >= 4) {
                return true;
            }
        }

        return false;
    }

    public int[][][] snapshot() {
        int[][][] copy = new int[5][5][5];

        for (int z = 0; z < 5; z++) {
            for (int y = 0; y < 5; y++) {
                System.arraycopy(cells[z][y], 0, copy[z][y], 0, 5);
            }
        }

        return copy;
    }

    public void printBoard() {

        for (int z = 4; z >= 0; z--) {

            System.out.println("高さ z=" + z);

            for (int y = 0; y < 5; y++) {

                for (int x = 0; x < 5; x++) {
                    System.out.print(cells[z][y][x] + " ");
                }

                System.out.println();
            }

            System.out.println();
        }
    }

    public static void main(String[] args) {

        Board board = new Board();

        System.out.println("範囲内: " + board.isInside(2, 3, 1));
        System.out.println("範囲外: " + board.isInside(5, 3, 1));

        System.out.println("1個目の高さ: " + board.drop(2, 3, 1));
        System.out.println("2個目の高さ: " + board.drop(2, 3, 2));

        System.out.println("上方向の2の連続数: " + board.countDirection(2, 3, 0, 0, 0, 1, 2));
        System.out.println("上方向の1の連続数: " + board.countDirection(2, 3, 0, 0, 0, 1, 1));

        board.printBoard();
    }
}
