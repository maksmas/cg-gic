import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//TODO send bombs
//TODO production...
class Player {
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

        //map.printMap();

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
                } else if ("TROOP".equals(entityType) && arg1 == -1) {
                    map.addIncomingEnemies(arg3, arg4);
                }
            }
            map.calculateStatistics();

            Optional<Factory> sourceFactory = map.myFactories().
                    reduce((f1,f2) -> f1.numberOfCyborgs - f1.incomingEnemyCount > f2.numberOfCyborgs - f2.incomingEnemyCount ? f1 : f2);
            if (sourceFactory.isPresent()) {
                int availableCyborgs = sourceFactory.map(f -> f.numberOfCyborgs - f.incomingEnemyCount).get();
                Optional<Factory> target = map.unCapturedFactories().
                        filter(f -> f.numberOfCyborgs + f.incomingEnemyCount +
                                (f.production * sourceFactory.map(f1 -> f1.dists.get(f)).get()) < availableCyborgs).
                        reduce((f1, f2) -> {
                            //TODO capture enemy first
                            //TODO optimize
                            //TODO dont send repeatedly
                            if (f1.production == f2.production) {
                                int f1Dist = sourceFactory.map(f -> f.dists.get(f1)).get();
                                int f2Dist = sourceFactory.map(f -> f.dists.get(f2)).get();
                                return f1Dist > f2Dist ? f2 : f1;
                            } else return f1.production > f2.production ? f1 : f2;
                        });
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

    void addIncomingEnemies(int factoryId, int num) {
        Factory factory = factoryMap.get(factoryId);
        factory.incomingEnemyCount += num;
    }

    void resetIncoming() {
        factoryMap.values().forEach(f -> f.incomingEnemyCount = 0);
    }

    //experimental
    Stream<Factory> unCapturedFactories() {
        if (unCapturedProducingCount > 0) {
            return factoryMap.values().stream().filter(f -> !f.isConquared() && f.production > 0);
        } else {
            return factoryMap.values().stream().filter(f -> !f.isConquared());
        }
    }

//    Stream<Factory> unCapturedFactories() {
//        return factoryMap.values().stream().filter(f -> !f.conquared);
//    }

    Stream<Factory> myFactories() {
        return factoryMap.values().stream().filter(Factory::isConquared);
    }

    void calculateStatistics() {
        unCapturedCount = 0;
        unCapturedProducingCount = 0;
        factoryMap.values().forEach(f -> {
            if (!f.isConquared()) {
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
    int owner;
    boolean isConquared() {
        return owner == 1;
    };
    Map<Factory, Integer> dists = new HashMap();
}