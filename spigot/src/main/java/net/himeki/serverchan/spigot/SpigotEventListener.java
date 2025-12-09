package net.himeki.serverchan.spigot;

import net.himeki.serverchan.ServerChanCore;
import net.himeki.serverchan.i18n.I18n;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Spigot event listener for ServerChan
 */
public class SpigotEventListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String message = event.getMessage();

        // Get permission level (op level in Spigot)
        int permissionLevel = player.isOp() ? 4 : 0;

        // Note: Since this is an async event, we need to handle the server instance carefully
        ServerChanCore.onChatMessage(playerName, message, permissionLevel);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();
        String translatedMessage = I18n.getMinecraftTranslation("multiplayer.player.joined", playerName);

        // Pass to the core handler
        ServerChanCore.onGameEvent("multiplayer.player.joined", translatedMessage);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        String translatedMessage = I18n.getMinecraftTranslation("multiplayer.player.left", playerName);

        // Pass to the core handler
        ServerChanCore.onGameEvent("multiplayer.player.left", translatedMessage);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getDeathMessage() != null) {
            // Spigot death messages are already formatted strings
            String deathMessage = event.getDeathMessage();

            // Use DamageCause for robust death type identification
            String key = getDeathKey(event.getEntity());

            // Pass to the core handler
            ServerChanCore.onGameEvent(key, deathMessage);
        }
    }

    /**
     * Get the death key based on the player's last damage cause.
     * Uses EntityDamageEvent.DamageCause for robust identification.
     * @param player The player who died
     * @return The translation key for the death type
     */
    private String getDeathKey(Player player) {
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage == null) {
            return "death.generic";
        }

        EntityDamageEvent.DamageCause cause = lastDamage.getCause();
        switch (cause) {
            case ENTITY_ATTACK:
            case ENTITY_SWEEP_ATTACK:
                return "death.attack.player";
            case PROJECTILE:
                return "death.attack.arrow";
            case FALL:
                return "death.fell";
            case DROWNING:
                return "death.drowned";
            case FIRE:
            case FIRE_TICK:
                return "death.attack.onFire";
            case LAVA:
                return "death.attack.lava";
            case SUFFOCATION:
                return "death.attack.inWall";
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION:
                return "death.attack.explosion";
            case VOID:
                return "death.attack.outOfWorld";
            case LIGHTNING:
                return "death.attack.lightningBolt";
            case STARVATION:
                return "death.attack.starve";
            case POISON:
            case MAGIC:
            case DRAGON_BREATH:
                return "death.attack.magic";
            case WITHER:
                return "death.attack.wither";
            case FALLING_BLOCK:
                return "death.attack.fallingBlock";
            case THORNS:
                return "death.attack.thorns";
            case CRAMMING:
                return "death.attack.cramming";
            case FLY_INTO_WALL:
                return "death.attack.flyIntoWall";
            #if MC_VER >= MC_1_17
            case FREEZE:
                return "death.attack.freeze";
            #endif
            #if MC_VER >= MC_1_19
            case SONIC_BOOM:
                return "death.attack.sonic_boom";
            #endif
            default:
                return "death.generic";
        }
    }
}