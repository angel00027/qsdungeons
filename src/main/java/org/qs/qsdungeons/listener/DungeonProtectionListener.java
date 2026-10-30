package org.qs.qsdungeons.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.qs.qsdungeons.QsDungeons;
import org.qs.qsdungeons.dungeon.Dungeon;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Protege las regiones de las dungeons salvo para administradores.
 */
public final class DungeonProtectionListener implements Listener {

    private final QsDungeons plugin;

    public DungeonProtectionListener(QsDungeons plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------------
    // Jugadores: bloques
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player != null && block != null) {
            org.qs.qsdungeons.dungeon.instance.DungeonInstance instance = plugin.getDungeonManager().getPlayerInstance(player);
            if (instance != null && instance.hasChest(block) && instance.getSettings().isAllowChestBreak()) {
                return;
            }
            org.qs.qsdungeons.dungeon.Dungeon dungeon = plugin.getDungeonManager().findDungeonAt(block.getLocation());
            if (dungeon != null && dungeon.getSettings().isAllowChestBreak()) {
                org.qs.qsdungeons.dungeon.instance.DungeonInstance active = plugin.getDungeonManager().getInstance(dungeon.getId());
                if (active != null && active.hasChest(block)) {
                    return;
                }
            }
        }
        if (deny(player, block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (deny(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (deny(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (deny(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (player != null && canBypass(player)) {
            return;
        }
        boolean touchesProtected = isProtected(event.getBlock().getLocation())
                || event.getBlocks().stream().anyMatch(state -> isProtected(state.getLocation()));
        if (touchesProtected) {
            event.setCancelled(true);
        }
    }

    /**
     * Pisotear cultivos o huevos de tortuga (fisico) nunca se permite. Las
     * interacciones con clic derecho solo se bloquean si
     * protection.block-interactions esta activo; los contenedores (cofres de
     * loot) siempre se pueden abrir.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !isProtected(block.getLocation()) || canBypass(event.getPlayer())) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            if (isTrampleable(block.getType())) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !plugin.getConfig().getBoolean("protection.block-interactions", false)) {
            return;
        }
        if (!block.getType().isInteractable() || block.getState() instanceof Container) {
            return;
        }
        event.setCancelled(true);
        plugin.getMessages().send(event.getPlayer(), "protection.denied", Map.of());
    }

    // ---------------------------------------------------------------------
    // Jugadores: entidades decorativas
    // ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null && deny(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getPlayer() != null && deny(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!isProtected(event.getEntity().getLocation())) {
            return;
        }
        if (event instanceof HangingBreakByEntityEvent byEntity
                && resolvePlayer(byEntity.getRemover()) instanceof Player player
                && canBypass(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if ((entity instanceof Hanging || entity instanceof ArmorStand)
                && isProtected(entity.getLocation())
                && !canBypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isProtected(event.getRightClicked().getLocation()) && !canBypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Soportes de armadura y marcos no reciben dano dentro de la region, y la
     * TNT (que solo puede venir de fuera) no hiere a nadie dentro.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (!isProtected(victim.getLocation())) {
            return;
        }
        Entity damager = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        if (victim instanceof ArmorStand || victim instanceof Hanging) {
            Player player = resolvePlayer(damager);
            if (player == null || !canBypass(player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (damager instanceof TNTPrimed || damager instanceof ExplosiveMinecart
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------------
    // Mobs, explosiones y cambios del entorno
    // ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    /**
     * Endermen, withers, ravagers, zombis rompiendo puertas, ovejas comiendo
     * cesped, bloques que caen...
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!isProtected(event.getBlock().getLocation())) {
            return;
        }
        if (event.getEntity() instanceof Player player && canBypass(player)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        if (isTrampleable(event.getBlock().getType()) && isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!isProtected(event.getBlock().getLocation())) {
            return;
        }
        if (event.getPlayer() != null && canBypass(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Material source = event.getSource().getType();
        if ((source == Material.FIRE || source == Material.SOUL_FIRE)
                && isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Agua y lava de fuera no pueden entrar. Los liquidos colocados dentro de
     * la propia dungeon siguen fluyendo con normalidad.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Dungeon target = protectedDungeonAt(event.getToBlock().getLocation());
        if (target != null && !Objects.equals(target, dungeonAt(event.getBlock().getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesOtherDungeon(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesOtherDungeon(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenser = event.getBlock();
        if (!(dispenser.getBlockData() instanceof Directional directional)) {
            return;
        }
        Dungeon target = protectedDungeonAt(dispenser.getRelative(directional.getFacing()).getLocation());
        if (target != null && !Objects.equals(target, dungeonAt(dispenser.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Hielo que se derrite, nieve, coral que muere, cultivo que se seca. El
     * fuego y el hielo escarchado si pueden desaparecer.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        Material type = event.getBlock().getType();
        if (type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.FROSTED_ICE) {
            return;
        }
        if (isProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    private boolean pistonTouchesOtherDungeon(Block piston, List<Block> blocks, BlockFace direction) {
        Dungeon own = dungeonAt(piston.getLocation());
        if (differentProtected(piston.getRelative(direction), own)) {
            return true;
        }
        for (Block block : blocks) {
            if (differentProtected(block, own) || differentProtected(block.getRelative(direction), own)) {
                return true;
            }
        }
        return false;
    }

    private boolean differentProtected(Block block, Dungeon own) {
        Dungeon target = protectedDungeonAt(block.getLocation());
        return target != null && !Objects.equals(target, own);
    }

    private boolean isTrampleable(Material type) {
        return type == Material.FARMLAND || type == Material.TURTLE_EGG;
    }

    private Dungeon dungeonAt(Location location) {
        return plugin.getDungeonManager().findDungeonAt(location);
    }

    private Dungeon protectedDungeonAt(Location location) {
        Dungeon dungeon = dungeonAt(location);
        return dungeon != null && dungeon.getSettings().isProtection() ? dungeon : null;
    }

    private boolean isProtected(Location location) {
        return plugin.getDungeonManager().isProtected(location);
    }

    private boolean canBypass(Player player) {
        return player != null
                && player.hasPermission("qsdungeons.admin.bypass")
                && plugin.getConfig().getBoolean("protection.admin-bypass", false);
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean deny(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        if (canBypass(player)) {
            return false;
        }
        if (!isProtected(block.getLocation())) {
            return false;
        }
        plugin.getMessages().send(player, "protection.denied", Map.of());
        return true;
    }
}
