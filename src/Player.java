import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Stream;

//TODO detect locks (~>30 turns of inactivity)
//TODO in lock case capture and develop unproducing factories
//TODO in case of lock send cyborgs to closest factory to enemy
//TODO dont send all cyborgs on first turn if factory prduction is 0
//TODO multiple actions for factory in one turn
class Player {
    static Function<Factory, Integer> countAvailableCyborgs = f -> f.numberOfCyborgs - f.incomingEnemyCount;

    private static Function<Factory, Integer> distanceFrom(Factory sf) {
        return f -> f.dists.get(sf);
    }

    private static Function<Factory, Integer> totalNumberOfEnemiesWithArrivingFrom(Factory sf) {
    	return f -> {
    		int producedEnemies = f.owner == -1 ? (f.production * (distanceFrom(sf).apply(f) + 1)) : 0;
    		return f.numberOfCyborgs + f.incomingEnemyCount + producedEnemies;
		};
    }

    private static BinaryOperator<Factory> bestTarget(Factory sourceFactory) {
        return (f1, f2) -> {
            if (f1.production == f2.production) {
                int f1Dist = distanceFrom(f1).apply(sourceFactory);
                int f2Dist = distanceFrom(f2).apply(sourceFactory);
                if (f1Dist == f2Dist) {
                    return f1.owner == -1 ? f1 :f2;
                } else return f1Dist > f2Dist ? f2 : f1;
            } else return f1.production > f2.production ? f1 : f2;

        };
    }

    private static GameMap map;
    private static int bombCount = 2;

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        map = new GameMap();
        int factoryCount = in.nextInt();
        int linkCount = in.nextInt();
        for (int i = 0; i < linkCount; i++) {
            int factory1 = in.nextInt();
            int factory2 = in.nextInt();
            int distance = in.nextInt();
            map.addLink(factory1, factory2, distance);
        }

        //noinspection InfiniteLoopStatement
        while (true) {
            map.resetIncoming();
            int entityCount = in.nextInt();
            for (int i = 0; i < entityCount; i++) {
                int entityId = in.nextInt();
                String entityType = in.next();
                int arg1 = in.nextInt();
                int arg2 = in.nextInt();
                int arg3 = in.nextInt();
                int arg4 = in.nextInt();
                int arg5 = in.nextInt();
                if ("FACTORY".equals(entityType)) {
                    map.addFactoryParams(entityId, arg1,arg2, arg3);
                } else if ("TROOP".equals(entityType)) {
                    map.addIncomingEnemies(arg3, arg4, arg1);
                }
            }
            map.calculateStatistics();

            StringBuilder command = new StringBuilder("");

            if (bombCount > 0) {
                appendCommand(command, bomb());
            }

            map.myFactories().filter(f -> f.availableDrones > 0).forEach(sourceFactory -> {
                int availableCyborgs = sourceFactory.availableDrones;

                if (availableCyborgs > 10 && sourceFactory.turnNumWithAvailableDrones > 5 && sourceFactory.production < 3) {
                    appendCommand(command, "INC " + sourceFactory.id);
                    sourceFactory.turnNumWithAvailableDrones = 0;
                } else {
                    Optional<Factory> target = seekTarget(sourceFactory, availableCyborgs);

                    if (target.isPresent()) {
                        appendCommand(command, attack(sourceFactory, target.get(), availableCyborgs));
                    } else if (availableCyborgs > 0) {
                        Optional<Factory> inDangerFactory = findIndangerFactory(sourceFactory);
                        inDangerFactory.ifPresent(factory -> appendCommand(command, support(sourceFactory, factory, availableCyborgs)));
                    }
                }
            });
            System.out.println(command.length() == 0 ? "WAIT" : command.toString());
        }
    }

    private static void appendCommand(StringBuilder command, String postfix) {
        if (command.length() > 0) {
            command.append(";");
        }
        command.append(postfix);
    }

    private static Optional<Factory> seekTarget(Factory sourceFactory, int availableCyborgs) {
        return map.unCapturedFactories().
                filter(f -> {
                    int enemies = totalNumberOfEnemiesWithArrivingFrom(sourceFactory).apply(f);
                    int friends = f.incomingFriendsCount;
                    return  enemies < availableCyborgs && enemies > friends;
                }).reduce(bestTarget(sourceFactory));
    }

    private static String attack(Factory source, Factory target, int availableCyborgs) {
        int enemies = totalNumberOfEnemiesWithArrivingFrom(source).apply(target);
        int send = (enemies + 1 > availableCyborgs) ? availableCyborgs : enemies + 1;
        target.incomingFriendsCount += send;
        return "MOVE " + source.id + " " + target.id + " " + send;
    }

    private static Optional<Factory> findIndangerFactory(Factory source) {
        return map.myFactories().
                filter(f -> f.production > 0 && (f.incomingEnemyCount - f.incomingFriendsCount) > f.numberOfCyborgs).
                reduce((f1, f2) -> {
                    int dist1 = distanceFrom(source).apply(f1);
                    int dist2 = distanceFrom(source).apply(f2);
                    if (dist1 > dist2) {
                        return f2;
                    } else if (dist1 < dist2) {
                        return f1;
                    } else {
                        return f1.production > f2.production ? f1 : f2;
                    }
                });
    }

    private static String support(Factory source, Factory target, int availableCyborgs) {
        int enemies = target.incomingEnemyCount - target.incomingFriendsCount;
        int send = (enemies - 1) > availableCyborgs ? availableCyborgs : (enemies - 1);
        target.incomingFriendsCount += send;
        return "MOVE " + source.id + " " + target.id + " " + send;
    }

    private static String bomb() {
        StringBuilder result = new StringBuilder("");
        map.unCapturedFactories().
                filter(f -> f.owner == -1 && f.myBombIn == 0 && f.production > 0 && f.incomingFriendsCount < 5 && (f.numberOfCyborgs + f.incomingEnemyCount) > 25).
                reduce((f1, f2) -> (f1.numberOfCyborgs + f1.incomingEnemyCount) > (f2.numberOfCyborgs + f2.incomingEnemyCount) ? f1 : f2).
                ifPresent(target -> target.dists.entrySet().stream().filter(e -> e.getKey().isConquered()).
                        reduce((e1, e2) -> e1.getValue() > e2.getValue() ? e2 : e1).
                        ifPresent(source -> {
                            result.append("BOMB ");
                            result.append(source.getKey().id);
                            result.append(" ");
                            result.append(target.id);
                            target.myBombIn = source.getValue();
                            bombCount--;
                        }));
        return result.toString();
    }
}

class GameMap {
    private Map<Integer, Factory> factoryMap = new HashMap<Integer, Factory>();
    private int unCapturedProducingCount;

    void addLink(int f1, int f2, int dist) {
        Factory factory1 = getOrCreate(f1);
        Factory factory2 = getOrCreate(f2);
        factory1.dists.putIfAbsent(factory2, dist);
        factory2.dists.putIfAbsent(factory1, dist);
    }

    void addFactoryParams(int to, int owner, int numberOfCyborgs, int factoryProduction) {
        Factory factory = factoryMap.get(to);
        factory.owner = owner;
        factory.numberOfCyborgs = numberOfCyborgs;
        factory.production = factoryProduction;
    }

    void addIncomingEnemies(int factoryId, int num, int team) {
        Factory factory = factoryMap.get(factoryId);
        if (team == -1) {
            factory.incomingEnemyCount += num;
        } else if (team == 1) {
            factory.incomingFriendsCount += num;
        }
    }

    void resetIncoming() {
        factoryMap.values().forEach(f -> {
                    f.incomingEnemyCount = 0;
                    f.incomingFriendsCount = 0;
                    f.availableDrones = 0;
                    if (f.myBombIn > 0) {
                        f.myBombIn--;
                    }
                }
        );
    }

    Stream<Factory> unCapturedFactories() {
        if (unCapturedProducingCount > 0) {
            return factoryMap.values().stream().filter(f -> !f.isConquered() && f.production > 0);
        } else {
            return factoryMap.values().stream().filter(f -> !f.isConquered());
        }
    }

    Stream<Factory> myFactories() {
        return factoryMap.values().stream().filter(Factory::isConquered);
    }

    void calculateStatistics() {
        unCapturedProducingCount = 0;
        factoryMap.values().forEach(f -> {
            if (!f.isConquered()) {
                f.turnNumWithAvailableDrones = 0;
                if (f.production > 0) {
                    unCapturedProducingCount++;
                }
            } else {
                f.availableDrones = Player.countAvailableCyborgs.apply(f);
                if (f.availableDrones > 5) {
                    f.turnNumWithAvailableDrones++;
                } else {
                    f.turnNumWithAvailableDrones = 0;
                }
            }
        });
    }
    
    private Factory getOrCreate(int factoryId) {
        if (factoryMap.containsKey(factoryId)) {
            return factoryMap.get(factoryId);
        } else {
            Factory factory = new Factory();
            factory.id = factoryId;
            factoryMap.put(factoryId, factory);
            return factory;
        }
    }
}

class Factory {
    int id;
    int production;
    int numberOfCyborgs;
    int incomingEnemyCount = 0;
    int incomingFriendsCount = 0;
    int owner;
    int availableDrones;
    int turnNumWithAvailableDrones = 0;
    int myBombIn = 0;

    boolean isConquered() {
        return owner == 1;
    };
    Map<Factory, Integer> dists = new HashMap<Factory, Integer>();
}