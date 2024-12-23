package com.badbones69.crazyauctions;

import com.badbones69.crazyauctions.api.CrazyManager;
import com.badbones69.crazyauctions.api.enums.Messages;
import com.badbones69.crazyauctions.api.enums.other.Permissions;
import com.badbones69.crazyauctions.api.guis.types.AuctionsMenu;
import com.badbones69.crazyauctions.api.guis.types.CategoriesMenu;
import com.badbones69.crazyauctions.api.guis.types.CurrentMenu;
import com.badbones69.crazyauctions.api.guis.types.ExpiredMenu;
import com.badbones69.crazyauctions.api.guis.types.other.BidMenu;
import com.badbones69.crazyauctions.api.guis.types.other.BuyingMenu;
import com.badbones69.crazyauctions.api.support.MetricsWrapper;
import com.badbones69.crazyauctions.commands.AuctionCommand;
import com.badbones69.crazyauctions.commands.AuctionTab;
import com.badbones69.crazyauctions.controllers.MarcoListener;
import com.badbones69.crazyauctions.controllers.MiscListener;
import com.badbones69.crazyauctions.currency.VaultSupport;
import com.badbones69.crazyauctions.tasks.InventoryManager;
import com.badbones69.crazyauctions.tasks.UserManager;
import com.ryderbelserion.vital.paper.Vital;
import com.ryderbelserion.vital.paper.api.enums.Support;
import com.ryderbelserion.vital.paper.util.scheduler.FoliaRunnable;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class CrazyAuctions extends Vital {

    public static CrazyAuctions getPlugin() {
        return JavaPlugin.getPlugin(CrazyAuctions.class);
    }

    private UserManager userManager;
    private CrazyManager crazyManager;

    private VaultSupport support;

    @Override
    public void onEnable() {
        if (!Support.vault.isEnabled()) {
            getLogger().severe("Vault was not found, so the plugin will now disable.");

            getServer().getPluginManager().disablePlugin(this);

            return;
        }

        this.support = new VaultSupport();

        if (!this.support.setupEconomy()) {
            getLogger().severe("An economy provider was not found, so the plugin will disable.");

            getServer().getPluginManager().disablePlugin(this);

            return;
        }

        getFileManager().addFile("config.yml")
                .addFile("data.yml")
                .addFile("messages.yml")
                .init();

        InventoryManager.loadButtons();

        this.userManager = new UserManager();
        this.userManager.updateAuctionsCache();
        this.userManager.updateExpiredCache();

        // we want to update this cache, after the cache above... because we will also calculate if items are expired!
        new FoliaRunnable(getServer().getGlobalRegionScheduler()) {
            @Override
            public void run() {
                userManager.updateAuctionsCache();
                userManager.updateExpiredCache();

                for (final Player player : getServer().getOnlinePlayers()) {
                    final Inventory inventory = player.getInventory();

                    if (inventory instanceof AuctionsMenu menu) {
                        menu.calculateItems();

                        continue;
                    }

                    if (inventory instanceof CurrentMenu menu) {
                        menu.calculateItems();
                    }
                }
            }
        }.runAtFixedRate(this, 20, 300 * 5);

        this.crazyManager = new CrazyManager();
        this.crazyManager.load();

        final PluginManager manager = getServer().getPluginManager();

        manager.registerEvents(new AuctionsMenu(), this); // register main menu
        manager.registerEvents(new CategoriesMenu(), this); // register categories menu
        manager.registerEvents(new CurrentMenu(), this); // register current listings menu
        manager.registerEvents(new ExpiredMenu(), this); // register expired menu
        manager.registerEvents(new BuyingMenu(), this); // register buying menu
        manager.registerEvents(new BidMenu(), this); // register bid menu

        manager.registerEvents(new MiscListener(), this);
        manager.registerEvents(new MarcoListener(), this);

        registerCommand(getCommand("crazyauctions"), new AuctionTab(), new AuctionCommand());

        for (final Permissions permission : Permissions.values()) {
            if (permission.shouldRegister()) {
                permission.registerPermission();
            }
        }

        Messages.addMissingMessages();

        new MetricsWrapper(this, 4624);
    }

    private void registerCommand(PluginCommand pluginCommand, TabCompleter tabCompleter, CommandExecutor commandExecutor) {
        if (pluginCommand != null) {
            pluginCommand.setExecutor(commandExecutor);

            if (tabCompleter != null) pluginCommand.setTabCompleter(tabCompleter);
        }
    }

    @Override
    public void onDisable() {
        if (this.crazyManager != null) this.crazyManager.unload();
    }

    public final VaultSupport getSupport() {
        return this.support;
    }

    public final CrazyManager getCrazyManager() {
        return this.crazyManager;
    }

    public final UserManager getUserManager() {
        return this.userManager;
    }
}