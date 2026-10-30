package org.qs.qsdungeons.dungeon.instance;

import org.qs.qsdungeons.spawn.SpawnPoint;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Estado de un punto de spawn durante una partida.
 */
public final class SpawnerRuntime {

    private final SpawnPoint point;
    private final Set<UUID> alive = new HashSet<>();
    private long nextSpawnAt;
    private boolean initialSpawned;
    private int pendingSpawns;
    // Cantidad total tras aplicar el escalado por jugadores.
    private int amount;
    // La oleada de este spawn ya empezo / ya fue superada.
    private boolean active;
    private boolean finished;

    public SpawnerRuntime(SpawnPoint point) {
        this.point = point;
        this.amount = point.getAmount();
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public SpawnPoint getPoint() {
        return point;
    }

    public Set<UUID> getAlive() {
        return alive;
    }

    public long getNextSpawnAt() {
        return nextSpawnAt;
    }

    public void setNextSpawnAt(long nextSpawnAt) {
        this.nextSpawnAt = nextSpawnAt;
    }

    public boolean isInitialSpawned() {
        return initialSpawned;
    }

    public void setInitialSpawned(boolean initialSpawned) {
        this.initialSpawned = initialSpawned;
    }

    public int getPendingSpawns() {
        return pendingSpawns;
    }

    public void setPendingSpawns(int pendingSpawns) {
        this.pendingSpawns = Math.max(0, pendingSpawns);
    }
}
