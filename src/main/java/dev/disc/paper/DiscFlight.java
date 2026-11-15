package dev.disc.paper;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One disc in the air. This is a port of the mod's ThrowingDiscEntity: the disc flies in a
 * straight line, bounces off blocks (gaining speed), hits every living entity it passes through
 * once per pass, then homes back to the thrower and is caught.
 *
 * The visible "entity" is an ItemDisplay, so players do not need a client mod.
 */
final class DiscFlight {
    private static final double HIT_PADDING = 0.2;      // hitbox grow around targets
    private static final double SWEEP_PADDING = 0.3;    // search-box grow around the path
    private static final double CATCH_RADIUS = 0.8;
    private static final float SPIN_PER_TICK = 1.2f;    // radians; under 90 deg so interpolation spins the right way

    private final ThrowingDiscPlugin plugin;
    private final UUID throwerId;
    private final int originSlot;
    private final DiscTier tier;
    private final World world;

    private ItemStack stack;
    private ItemDisplay display;
    private Vector pos;
    private Vector vel;

    private int age;
    private int bounces;
    private boolean returning;
    private double distance;
    private boolean done;
    private float spin;
    private final Set<UUID> hitThisPass = new HashSet<>();
    private final Set<Long> tickets = new HashSet<>();

    DiscFlight(ThrowingDiscPlugin plugin, Player thrower, ItemStack stack, int originSlot, DiscTier tier) {
        this.plugin = plugin;
        this.throwerId = thrower.getUniqueId();
        this.originSlot = originSlot;
        this.tier = tier;
        this.stack = stack;
        this.world = thrower.getWorld();

        Location eye = thrower.getEyeLocation();
        this.pos = new Vector(eye.getX(), eye.getY() - 0.15, eye.getZ());
        this.vel = eye.getDirection().multiply(plugin.settings().speed);

        updateTickets();
        spawnDisplay();
    }

    UUID throwerId() { return throwerId; }
    boolean isDone() { return done; }

    // ---- per-tick update -----------------------------------------------------

    void tick() {
        if (done) {
            return;
        }
        Settings s = plugin.settings();
        age++;

        Player thrower = Bukkit.getPlayer(throwerId);
        if (thrower == null || thrower.isDead() || thrower.getGameMode() == GameMode.SPECTATOR) {
            dropStack();
            return;
        }
        if (!thrower.getWorld().equals(world) || age > s.hardMaxAge) {
            dropStack();
            return;
        }

        if (!returning && (age >= s.maxAge || distance >= s.baseDistance + bounces * s.distancePerBounce)) {
            beginReturn();
        }
        if (returning) {
            flyHome(thrower);
        } else {
            flyOutbound();
        }

        if (!done) {
            updateTickets();
            updateDisplay();
        }
    }

    private void beginReturn() {
        returning = true;
        hitThisPass.clear();
        playSound(Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
    }

    // ---- outbound flight ----------------------------------------------------------

    private void flyOutbound() {
        Settings s = plugin.settings();
        double remaining = 1.0;
        for (int i = 0; i < 4 && remaining > 1.0e-5 && !returning; i++) {
            Vector start = pos.clone();
            Vector delta = vel.clone().multiply(remaining);
            Vector end = start.clone().add(delta);

            if (!world.isChunkLoaded(blockToChunk(end.getX()), blockToChunk(end.getZ()))) {
                beginReturn();
                return;
            }

            RayTraceResult hit = rayTrace(start, delta);
            Vector stop = hit != null ? hit.getHitPosition() : end;

            pierce(start, stop);
            distance += start.distance(stop);
            pos = stop.clone();

            if (hit == null) {
                return;
            }

            Vector normal = faceNormal(hit, delta);
            pos = pos.add(normal.clone().multiply(0.015));
            vel = reflect(vel, normal);
            playSound(Sound.ENTITY_ARROW_HIT, 0.65f, 1.0f);

            bounces++;
            if (bounces >= s.maxBounces) {
                beginReturn();
                return;
            }
            remaining *= 1.0 - clamp01(start.distance(stop) / Math.max(delta.length(), 1.0e-9));
        }
    }

    // ---- return flight ---------------------------------------------------------------

    private void flyHome(Player thrower) {
        Vector target = targetPoint(thrower);
        vel = home(vel, target.clone().subtract(pos));

        double remaining = 1.0;
        for (int i = 0; i < 4 && remaining > 1.0e-5; i++) {
            Vector start = pos.clone();
            Vector delta = vel.clone().multiply(remaining);
            Vector end = start.clone().add(delta);

            if (!world.isChunkLoaded(blockToChunk(end.getX()), blockToChunk(end.getZ()))) {
                dropStack();
                return;
            }

            RayTraceResult hit = rayTrace(start, delta);
            Vector stop = hit != null ? hit.getHitPosition() : end;

            if (segmentWithin(start, stop, target, CATCH_RADIUS) && clearPath(stop, target)) {
                catchBy(thrower);
                return;
            }

            pierce(start, stop);
            pos = stop.clone();

            if (hit == null) {
                return;
            }
            // Wedged inside a block: give up and drop.
            if (stop.distanceSquared(start) < 1.0e-9 && world.getBlockAt(start.toLocation(world)).isSolid()) {
                dropStack();
                return;
            }

            Vector normal = faceNormal(hit, delta);
            pos = pos.add(normal.clone().multiply(0.015));
            vel = reflect(vel, normal);
            playSound(Sound.ENTITY_ARROW_HIT, 0.65f, 1.0f);
            remaining *= 1.0 - clamp01(start.distance(stop) / Math.max(delta.length(), 1.0e-9));
        }
    }

    // ---- hitting entities -------------------------------------------------------------

    private record Target(LivingEntity entity, double distanceSq) { }

    private boolean canHit(Entity e) {
        if (!(e instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
            return false;
        }
        if (e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (e instanceof ArmorStand stand && stand.isMarker()) {
            return false;
        }
        return !e.getUniqueId().equals(throwerId) && !hitThisPass.contains(e.getUniqueId());
    }

    private void pierce(Vector from, Vector to) {
        Vector segment = to.clone().subtract(from);
        double length = segment.length();
        BoundingBox sweep = BoundingBox.of(from, to).expand(SWEEP_PADDING);

        List<Target> targets = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(sweep, this::canHit)) {
            BoundingBox box = e.getBoundingBox().expand(HIT_PADDING);
            if (box.contains(from)) {
                targets.add(new Target((LivingEntity) e, 0.0));
            } else if (length > 1.0e-9) {
                RayTraceResult ray = box.rayTrace(from, segment.clone().normalize(), length);
                if (ray != null) {
                    targets.add(new Target((LivingEntity) e, ray.getHitPosition().distanceSquared(from)));
                }
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        targets.sort(Comparator.comparingDouble(Target::distanceSq));

        Player thrower = Bukkit.getPlayer(throwerId);
        double amount = plugin.items().damage(tier);
        DamageType type = plugin.settings().armorPiercing ? DamageType.INDIRECT_MAGIC : DamageType.THROWN;

        for (Target target : targets) {
            LivingEntity victim = target.entity();
            if (thrower != null && sameTeamNoFriendlyFire(thrower, victim)) {
                continue;
            }
            hitThisPass.add(victim.getUniqueId());

            DamageSource.Builder builder = DamageSource.builder(type);
            if (thrower != null) {
                builder.withCausingEntity(thrower);
            }
            if (display != null && display.isValid()) {
                builder.withDirectEntity(display);
            }
            if (thrower != null && victim instanceof Player) {
                plugin.flights().recordHit(victim.getUniqueId(), thrower.getName());
            }
            // Check before the hit lands: a blocking player may stop being "blocking" once damaged.
            Player shielded = null;
            if (plugin.settings().shieldDisableTicks > 0 && victim instanceof Player p && shieldUp(p)) {
                shielded = p;
            }
            victim.damage(amount, builder.build());
            playSound(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.65f, 1.0f);
            if (shielded != null) {
                disableShield(shielded);
            }
        }
    }

    /** True if the target is actively blocking with a shield that faces the disc (same rule as vanilla). */
    private boolean shieldUp(Player target) {
        if (!target.isBlocking()) {
            return false;
        }
        ItemStack active = target.getActiveItem();
        if (active == null || active.getType() != Material.SHIELD) {
            return false;
        }
        Vector toDisc = pos.clone().subtract(target.getLocation().toVector());
        toDisc.setY(0);
        if (toDisc.lengthSquared() < 1.0e-9) {
            return true;
        }
        Vector view = target.getEyeLocation().getDirection();
        view.setY(0);
        return view.dot(toDisc) > 0;
    }

    /** Knock the shield down like an axe does: lower it and put it on cooldown. */
    private void disableShield(Player target) {
        target.setCooldown(Material.SHIELD, plugin.settings().shieldDisableTicks);
        target.clearActiveItem();
        world.playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    private static boolean sameTeamNoFriendlyFire(Player thrower, LivingEntity victim) {
        if (Bukkit.getScoreboardManager() == null) {
            return false;
        }
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntityTeam(thrower);
        return team != null && !team.allowFriendlyFire() && team.hasEntity(victim);
    }

    // ---- ending a flight -------------------------------------------------------------------

    /** Put the disc back where it was thrown from (or the first free slot), then end the flight. */
    void catchBy(Player player) {
        if (done || stack == null) {
            return;
        }
        ItemStack caught = stack;
        stack = null;
        finish();

        PlayerInventory inv = player.getInventory();
        boolean slotOk = (originSlot >= 0 && originSlot < 9) || originSlot == 40;
        if (slotOk && isEmpty(inv.getItem(originSlot))) {
            inv.setItem(originSlot, caught);
        } else {
            for (ItemStack leftover : inv.addItem(caught).values()) {
                Location at = player.getLocation().add(0, 0.5, 0);
                player.getWorld().dropItem(at, leftover, item -> {
                    item.setOwner(player.getUniqueId());
                    item.setPickupDelay(0);
                });
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.0f);
    }

    /** Drop the disc as an item where it is, then end the flight. */
    void dropStack() {
        if (done) {
            return;
        }
        ItemStack dropped = stack;
        stack = null;
        Location at = pos.toLocation(world);
        finish();
        if (dropped != null && !isEmpty(dropped)) {
            world.dropItem(at, dropped, item -> {
                item.setThrower(throwerId);
                item.setPickupDelay(0);
            });
        }
    }

    /** Called when the thrower logs out or the server stops: catch the disc if possible, otherwise drop it. */
    void recoverFor(Player player) {
        if (done) {
            return;
        }
        if (player.getWorld().equals(world) && clearPath(pos, targetPoint(player))) {
            catchBy(player);
        } else {
            dropStack();
        }
    }

    private void finish() {
        done = true;
        if (display != null) {
            display.remove();
            display = null;
        }
        for (long key : tickets) {
            world.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
        }
        tickets.clear();
    }

    // ---- display -----------------------------------------------------------------------------

    private Location location() {
        return new Location(world, pos.getX(), pos.getY(), pos.getZ());
    }

    private Transformation transformation(float angle) {
        // Lay the sprite flat, then spin it around the vertical axis.
        Quaternionf rotation = new Quaternionf().rotateY(angle).rotateX((float) (Math.PI / 2.0));
        return new Transformation(new Vector3f(), rotation, new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf());
    }

    private void spawnDisplay() {
        display = world.spawn(location(), ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setTransformation(transformation(0.0f));
        });
    }

    private void updateDisplay() {
        if (display == null || !display.isValid()) {
            spawnDisplay();
            return;
        }
        spin += SPIN_PER_TICK;
        display.setTransformation(transformation(spin));
        display.setInterpolationDelay(0);
        display.teleport(location());
    }

    /** Keep the chunks around the disc loaded while it flies (like the mod's chunk ticket). */
    private void updateTickets() {
        int cx = blockToChunk(pos.getX());
        int cz = blockToChunk(pos.getZ());
        Set<Long> wanted = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                wanted.add(chunkKey(cx + dx, cz + dz));
            }
        }
        for (long key : wanted) {
            if (tickets.add(key)) {
                world.addPluginChunkTicket((int) (key >> 32), (int) key, plugin);
            }
        }
        tickets.removeIf(boxed -> {
            long key = boxed;
            if (wanted.contains(key)) {
                return false;
            }
            world.removePluginChunkTicket((int) (key >> 32), (int) key, plugin);
            return true;
        });
    }

    private void playSound(Sound sound, float volume, float pitch) {
        world.playSound(location(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    // ---- math helpers ------------------------------------------------------------------------------

    private RayTraceResult rayTrace(Vector start, Vector delta) {
        double length = delta.length();
        if (length < 1.0e-9) {
            return null;
        }
        return world.rayTraceBlocks(start.toLocation(world), delta.clone().normalize(), length,
                FluidCollisionMode.NEVER, true);
    }

    private boolean clearPath(Vector from, Vector to) {
        Vector delta = to.clone().subtract(from);
        return rayTrace(from, delta) == null;
    }

    private Vector faceNormal(RayTraceResult hit, Vector travel) {
        BlockFace face = hit.getHitBlockFace();
        if (face != null) {
            return face.getDirection();
        }
        Vector back = travel.clone().multiply(-1.0);
        return back.lengthSquared() > 1.0e-12 ? back.normalize() : new Vector(0, 1, 0);
    }

    /** Mirror the velocity about the surface normal, 15% faster each bounce up to a cap. */
    private Vector reflect(Vector v, Vector normal) {
        Settings s = plugin.settings();
        Vector r = v.clone().subtract(normal.clone().multiply(2.0 * v.dot(normal)));
        double length = r.length();
        if (length < 1.0e-9) {
            return r;
        }
        return r.normalize().multiply(Math.min(s.maxSpeed, length * s.bounceBoost));
    }

    /** Steer toward the thrower: keep 65% of the current velocity, add a pull that grows as it speeds up. */
    private Vector home(Vector v, Vector toTarget) {
        double speed = Math.min(2.25, v.length() + 0.12);
        Vector pull = toTarget.clone();
        if (pull.lengthSquared() > 1.0e-12) {
            pull.normalize();
        } else {
            pull.zero();
        }
        return v.clone().multiply(0.65).add(pull.multiply(speed * 0.35));
    }

    private static boolean segmentWithin(Vector a, Vector b, Vector p, double radius) {
        Vector ab = b.clone().subtract(a);
        double lengthSq = ab.lengthSquared();
        double t = lengthSq < 1.0e-10 ? 0.0 : clamp01(p.clone().subtract(a).dot(ab) / lengthSq);
        Vector closest = a.clone().add(ab.multiply(t));
        return closest.distanceSquared(p) <= radius * radius;
    }

    private static Vector targetPoint(Player player) {
        return player.getLocation().toVector().add(new Vector(0, player.getHeight() * 0.7, 0));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static int blockToChunk(double coordinate) {
        return (int) Math.floor(coordinate) >> 4;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static boolean isEmpty(ItemStack s) {
        return s == null || s.getType().isAir() || s.getAmount() <= 0;
    }
}
