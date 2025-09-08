package game;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class SnakeGame {

    // Game of snake
    // snake can move up, left, right , dowm
    // snake grow one unit when go by 5 unit - 4 unit and left(1) as it grows

    // snake hits itself it dies
    /*
    *   Snake
    *       body : Deque<Postiion>
            alive : bool
            dir: Direction
            count: int

        World
            width: int
            height: int

        GameState
        * world : World
        * snake : Snake
        * score : int
    * */
    static class Position {
        int x;
        int y;

        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        None
    }

    static class Snake {
        Deque<Position> body;
        boolean isAlive;
        int count;
        Direction direction;

        Snake() {
            this.body = new LinkedList<>();
            this.isAlive = true;
            this.count = 1;
            this.direction = Direction.None;
        }
    }

    static class World {
        int weidth;
        int height;

        World(int weidth, int height) {
            this.weidth = weidth;
            this.height = height;
        }
    }

    static class GameStateManager{
        final World world;
        final Snake snake;
        int score;
        static GameStateManager manager;

        private GameStateManager(){
            this.world = new World(5, 5);
            this.snake = new Snake();
            snake.body.offer(new Position(0, 0 ));
        }

        public static GameStateManager getInstance(){
            if(manager == null) {
                manager = new GameStateManager();
            }
            return manager;
        }

        public void startGame(List<Direction> directions) {
            for(int i = 0; i < directions.size(); i++){
                Direction move = directions.get(i);
                Position pos = snake.body.peekLast();
                pos = utility(move, pos);
                pos = getPos(pos);

                if(isValid(pos)) {
                    snake.body.offer(pos);

                    if(snake.count == 1) {
                        snake.count = 1;
                        continue;
                    }
                    snake.count++;


                    snake.body.pollFirst();

                } else {
                    System.out.println("Game Ended");
                    return;
                }
            }
            System.out.println("Game Not Ended");
        }

        private Position getPos(Position pos ) {
            if(pos.x < 0){
                int x = world.weidth + pos.x;
                return new Position(x, pos.y);
            }
            else if (pos.y < 0) {
                int y = world.height +pos.y;
                return new Position(pos.x, y);
            }
            else if(pos.x >= world.weidth || pos.y >= world.height) {
                int x = pos.x % world.weidth;
                int y = pos.y % world.height;
                return new Position(x, y);
            }

            return pos;
        }

        private boolean isValid(Position pos) {

            Iterator<Position> positions = snake.body.iterator();
            while(positions.hasNext()) {
                Position position = positions.next();
                if(position.x == pos.x && position.y == pos.y) {
                    return false;
                }
            }
            return true;
        }

        private Position utility(Direction dir, Position pos) {
            // {0,-1} {0, 1} {-1, 0} {1, 0}
            int []x = {0 , 0, -1, 1};
            int []y = {-1, 1, 0, 0};

            return switch (dir) {
                case UP -> new Position(pos.x+0 , pos.y+1);
                case DOWN -> new Position(pos.x+0, pos.y-1);
                case LEFT -> new Position(pos.x-1, pos.y+0);
                case RIGHT -> new Position(pos.x+1, pos.y);
                default -> null;
            };
        }

        public Snake getSnake() {
            return this.snake;
        }
    }

    public static void main(String[] args) {
        GameStateManager stateManager = GameStateManager.getInstance();
        List<Direction> directions = List.of(Direction.RIGHT, Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN, Direction.DOWN);
        stateManager.startGame(directions);
        stateManager.getSnake().body.forEach(e -> System.out.println(e.x + " " + e.y + ", "));

    }
    //  * * * * *
    //  * * * * *
    //  * * * * *
    //  * * * # #
    //  # # # # #
}