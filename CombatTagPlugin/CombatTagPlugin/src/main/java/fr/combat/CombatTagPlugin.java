package fr.combat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class CombatTagPlugin extends JavaPlugin implements Listener {

    // ===== Valeur modifiable =====
    private static final int DUREE_COMBAT_SECONDES = 45;

    /** Joueur -> moment (ms) où son combat se termine. */
    private final Map<UUID, Long> enCombat = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        // Toutes les secondes : affiche le timer et termine les combats expirés
        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, Long>> it = enCombat.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null) {
                    it.remove();
                    continue;
                }
                long reste = e.getValue() - now;
                if (reste <= 0) {
                    it.remove();
                    p.sendActionBar(Component.text("Tu n'es plus en combat.", NamedTextColor.GREEN));
                } else {
                    long sec = (reste + 999) / 1000;
                    p.sendActionBar(Component.text("En combat : " + sec + "s", NamedTextColor.RED));
                }
            }
        }, 20L, 20L);
    }

    private boolean estEnCombat(Player p) {
        Long fin = enCombat.get(p.getUniqueId());
        if (fin == null) return false;
        if (fin <= System.currentTimeMillis()) {
            enCombat.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    private void tag(Player p) {
        boolean deja = estEnCombat(p);
        enCombat.put(p.getUniqueId(), System.currentTimeMillis() + DUREE_COMBAT_SECONDES * 1000L);
        if (p.isGliding()) {
            p.setGliding(false);
        }
        if (!deja) {
            p.sendMessage(Component.text("Tu es en combat pendant " + DUREE_COMBAT_SECONDES
                    + "s : pas d'élytra, et te déconnecter te tue.", NamedTextColor.RED));
        }
    }

    // ===== Début du combat =====
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victime)) return;

        Player attaquant = null;
        if (e.getDamager() instanceof Player p) {
            attaquant = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attaquant = p;
        }
        if (attaquant == null || attaquant.equals(victime)) return;

        tag(victime);
        tag(attaquant);
    }

    // ===== Pas d'élytra en combat =====
    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.isGliding() && estEnCombat(p)) {
            e.setCancelled(true);
            p.sendActionBar(Component.text("Élytra interdites en combat !", NamedTextColor.RED));
        }
    }

    // ===== Déconnexion en combat = mort =====
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        boolean combat = estEnCombat(p);
        enCombat.remove(p.getUniqueId());
        // On ne tue pas pendant l'arrêt/redémarrage du serveur
        if (combat && !Bukkit.isStopping()) {
            p.setHealth(0.0);
        }
    }

    // ===== Mort = fin du combat =====
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        enCombat.remove(e.getEntity().getUniqueId());
    }
}
