package com.enderdevelopment.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Main extends JavaPlugin implements Listener {
    private static final String MACE = "senpai_mace";
    private static final String SPEAR = "senpai_spear";
    private NamespacedKey weaponKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        weaponKey = new NamespacedKey(this, "weapon");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("weapons").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            player.getInventory().addItem(createWeapon(MACE), createWeapon(SPEAR));
            player.sendMessage(Component.text("Senpai's arsenal delivered. Right-click to use abilities.", NamedTextColor.GOLD));
            return true;
        });
    }

    @Override
    public void onDisable() {
        cooldowns.clear();
    }

    private ItemStack createWeapon(String id) {
        boolean mace = MACE.equals(id);
        ItemStack item = new ItemStack(mace ? Material.MACE : Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(Component.text(mace ? "Senpai's Skybreaker Mace" : "Senpai's Void Spear", mace ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.DARK_PURPLE));
        meta.lore(mace
            ? java.util.List.of(Component.text("Right-click: Elytra double-rocket momentum", NamedTextColor.GRAY), Component.text("Right-click on ground: Maledict Domain", NamedTextColor.RED))
            : java.util.List.of(Component.text("Right-click: Blackhole Domain", NamedTextColor.DARK_PURPLE), Component.text("Pulls nearby enemies inward", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, id);
        Enchantment sharpness = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
        if (sharpness != null) meta.addEnchant(sharpness, 5, true);
        item.setItemMeta(meta);
        return item;
    }

    private String weaponType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeaponUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        Player player = event.getPlayer();
        String type = weaponType(event.getItem());
        if (type == null) return;
        event.setCancelled(true);
        if (!ready(player)) {
            player.sendMessage(Component.text("That ability is recharging.", NamedTextColor.GRAY));
            return;
        }
        if (MACE.equals(type)) useMace(player);
        else if (SPEAR.equals(type)) useSpear(player);
    }

    private boolean ready(Player player) {
        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) return false;
        cooldowns.put(player.getUniqueId(), now + 8000L);
        return true;
    }

    private void useMace(Player player) {
        if (player.isGliding() && player.getInventory().getChestplate() != null
                && player.getInventory().getChestplate().getType() == Material.ELYTRA) {
            Vector direction = player.getLocation().getDirection().normalize();
            player.setVelocity(player.getVelocity().add(direction.multiply(2.4)).add(new Vector(0, 0.5, 0)));
            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation(), 45, 0.35, 0.35, 0.35, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 0.8f);
            player.sendMessage(Component.text("Skybreaker momentum!", NamedTextColor.LIGHT_PURPLE));
            return;
        }
        LivingEntity target = nearestTarget(player, 7);
        if (target != null) {
            target.damage(16.0, player);
            target.setFireTicks(100);
            target.setVelocity(target.getVelocity().add(new Vector(0, 0.8, 0)));
        }
        player.getWorld().strikeLightningEffect(player.getLocation());
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 70, 2, 0.3, 2, 0.04);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        player.sendMessage(Component.text("Maledict Domain!", NamedTextColor.RED));
    }

    private void useSpear(Player player) {
        var center = player.getLocation().add(player.getLocation().getDirection().multiply(5));
        player.getWorld().spawnParticle(Particle.PORTAL, center, 100, 2.5, 1.5, 2.5, 0.1);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 45, 2, 1, 2, 0.05);
        player.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.55f);
        for (var entity : player.getWorld().getNearbyEntities(center, 5, 3, 5)) {
            if (!(entity instanceof LivingEntity target) || target == player) continue;
            Vector pull = center.toVector().subtract(target.getLocation().toVector());
            if (pull.lengthSquared() > 0.01) target.setVelocity(pull.normalize().multiply(1.1).add(new Vector(0, 0.25, 0)));
            target.damage(8.0, player);
        }
        player.sendMessage(Component.text("Void Domain: Singularity!", NamedTextColor.DARK_PURPLE));
    }

    private LivingEntity nearestTarget(Player player, double range) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (var entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living) || entity == player) continue;
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < bestDistance) { best = living; bestDistance = distance; }
        }
        return best;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCustomDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        String type = weaponType(player.getInventory().getItemInMainHand());
        if (MACE.equals(type)) event.setDamage(event.getDamage() + 8.0);
        if (SPEAR.equals(type)) event.setDamage(event.getDamage() + 5.0);
    }
}
