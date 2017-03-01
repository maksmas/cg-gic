import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//TODO send bombs
//TODO production...
//TODO don't send all available
class Player {
    private static Function<Factory, Integer> countAvailableCyborgs = f -> f.numberOfCyborgs - f.incomingEnemyCount;

    private static Function<Factory, Integer> distanceFrom(Factory sf) {
        return f -> f.dists.get(sf);
    }

    private static Function<Factory, Integer> totalNumberOfEnemiesWithArrivingFrom(Factory sf) {
        return f -> f.numberOfCyborgs + f.incomingEnemyCount +
                (f.owner == -1 ? (f.production * (distanceFrom(sf).apply(f))) : 0);
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

            Optional<Factory> sourceFactory = map.getBestCandidateForSource();
            if (sourceFactory.isPresent()) {
                int availableCyborgs = sourceFactory.map(countAvailableCyborgs).get() - 1;

                Optional<Factory> target = map.unCapturedFactories().
                        filter(f -> {
                            int enemies = totalNumberOfEnemiesWithArrivingFrom(sourceFactory.get()).apply(f);
                            int friends = f.incomingFriendsCount;
                            return  enemies < availableCyborgs && enemies > friends;
                        }).reduce(bestTarget(sourceFactory.get()));
                if (target.isPresent()) {
                    System.out.println("MOVE " + sourceFactory.get().id + " " + target.get().id + " " + availableCyborgs);
                    continue;
                }
            }
            System.out.println("WAIT");
        }
    }
}

class GameMap {
    private Map<Integer, Factory> factoryMap = new HashMap<Integer, Factory>();
    int unCapturedCount;
    int unCapturedProducingCount;


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

    private Stream<Factory> myFactories() {
        return factoryMap.values().stream().filter(Factory::isConquered);
    }

    Optional<Factory> getBestCandidateForSource() {
        return myFactories().reduce((f1,f2) -> f1.numberOfCyborgs - f1.incomingEnemyCount > f2.numberOfCyborgs - f2.incomingEnemyCount ? f1 : f2);
    }

    void calculateStatistics() {
        unCapturedCount = 0;
        unCapturedProducingCount = 0;
        factoryMap.values().forEach(f -> {
            if (!f.isConquered()) {
                unCapturedCount++;
                if (f.production > 0) {
                    unCapturedProducingCount++;
                }
            }
        });
    }

    void printMap() {
        factoryMap.values().forEach(f -> {
            System.err.println(f.id + ": ");
            System.err.println(f.dists.keySet().stream().
                    map(f2 -> "[" + f2.id + " " + f.dists.get(f2) + "]").
                    collect(Collectors.toList()));
            System.err.println();
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
    boolean isConquered() {
        return owner == 1;
    };
    Map<Factory, Integer> dists = new HashMap<Factory, Integer>();

    @Override
    public String toString() {
        return id + "";
    }
}