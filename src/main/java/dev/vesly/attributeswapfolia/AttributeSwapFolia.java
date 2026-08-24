package dev.vesly.attributeswapfolia;

import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AttributeSwapFolia extends JavaPlugin implements Listener {
    private boolean restartRequired;

    @Override
    public void onEnable() {
        Path serverRoot = resolveServerRoot();
        if (serverRoot != null) {
            PaperGlobalConfigPatcher.PatchResult result = new PaperGlobalConfigPatcher().patch(serverRoot);
            restartRequired = result.status() == PaperGlobalConfigPatcher.PatchStatus.MODIFIED;
            logPatchResult(result);
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (restartRequired && event.getPlayer().isOp()) {
            event.getPlayer().sendMessage(Component.text(
                    "[AttributeSwapFolia] Attribute swapping was enabled on disk. Restart the server once more to apply it.",
                    NamedTextColor.GOLD));
        }
    }

    private Path resolveServerRoot() {
        Path dataFolder = getDataFolder().toPath().toAbsolutePath().normalize();
        Path pluginsDirectory = dataFolder.getParent();
        Path serverRoot = pluginsDirectory == null ? null : pluginsDirectory.getParent();

        if (serverRoot == null) {
            getLogger().severe("Could not determine the server root from plugin data folder: " + dataFolder);
        }

        return serverRoot;
    }

    private void logPatchResult(PaperGlobalConfigPatcher.PatchResult result) {
        switch (result.status()) {
            case MODIFIED -> {
                getLogger().info("Attribute swapping has been enabled in paper-global.yml.");
                getLogger().warning("Restart the server once more for the change to take effect.");
            }
            case ALREADY_ENABLED -> getLogger().info("Attribute swapping is already enabled.");
            case MISSING_FILE -> getLogger().warning(
                    "Paper global configuration was not found at " + result.configPath() + ". No changes were made.");
            case MISSING_SETTING -> getLogger().severe(
                    "Expected Paper 1.21.11 setting unsupported-settings.update-equipment-on-player-actions "
                            + "was not found in "
                            + result.configPath()
                            + ". No changes were made.");
            case DUPLICATE_SETTING -> getLogger().severe(
                    "Multiple active update-equipment-on-player-actions settings were found in "
                            + result.configPath()
                            + ". No changes were made.");
            case INVALID_SETTING_VALUE -> getLogger().severe(
                    "The update-equipment-on-player-actions setting in "
                            + result.configPath()
                            + " is malformed or has an unsupported value. No changes were made.");
            case BACKUP_FAILED, WRITE_FAILED -> getLogger().severe(
                    "Could not safely update " + result.configPath() + ": " + result.detail());
        }
    }
}
