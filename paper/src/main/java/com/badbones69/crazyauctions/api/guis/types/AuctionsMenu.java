package com.badbones69.crazyauctions.api.guis.types;

import com.badbones69.crazyauctions.Methods;
import com.badbones69.crazyauctions.api.builders.ItemBuilder;
import com.badbones69.crazyauctions.api.enums.Category;
import com.badbones69.crazyauctions.api.enums.Messages;
import com.badbones69.crazyauctions.api.enums.Reasons;
import com.badbones69.crazyauctions.api.enums.ShopType;
import com.badbones69.crazyauctions.api.enums.misc.Files;
import com.badbones69.crazyauctions.api.enums.misc.Keys;
import com.badbones69.crazyauctions.api.events.AuctionCancelledEvent;
import com.badbones69.crazyauctions.api.guis.Holder;
import com.badbones69.crazyauctions.api.guis.HolderManager;
import com.badbones69.crazyauctions.api.GuiManager;
import com.badbones69.crazyauctions.tasks.InventoryManager;
import com.ryderbelserion.vital.paper.util.scheduler.FoliaRunnable;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings({"FieldCanBeLocal", "UnusedAssignment"})
public class AuctionsMenu extends Holder {

    private List<ItemStack> items;
    private List<String> options;
    private int maxPages;

    private FileConfiguration config;
    private FileConfiguration data;

    private Category category;

    public AuctionsMenu(final Player player, final ShopType shopType, final Category category, final String title, final int size, final int page) {
        super(player, shopType, title, size, page);

        this.items = new ArrayList<>();
        this.options = new ArrayList<>();

        this.config = Files.config.getConfiguration();
        this.data = Files.data.getConfiguration();

        if (!this.data.contains("Items")) {
            this.data.set("Items.Clear", null);

            Files.data.save();
        }

        if (category != null) {
            HolderManager.addShopCategory(player, this.category = category);
        }
    }

    private String target;

    public AuctionsMenu(final Player player, final String target, final String title, final int size, final int page) {
        this(player, null, null, title, size, page);

        this.target = target;
    }

    public AuctionsMenu() {}

    @Override
    public final Holder build() {
        Methods.updateAuction();

        if (this.target != null) {
            this.options.add("WhatIsThis.Viewing");
        } else {
            this.options.addAll(List.of(
                    "SellingItems",
                    "Cancelled/ExpiredItems",
                    "PreviousPage",
                    "Refresh",
                    "Refesh",
                    "NextPage",
                    "Category1",
                    "Category2"
            ));
        }

        getItems(); // populates the lists

        this.maxPages = getMaxPage(this.items);

        HolderManager.addShopType(this.player, this.shopType);

        switch (this.shopType) {
            case SELL -> {
                if (this.crazyManager.isSellingEnabled()) {
                    this.options.add("Bidding/Selling.Selling");
                }

                this.options.add("WhatIsThis.SellingShop");
            }

            case BID -> {
                if (this.crazyManager.isBiddingEnabled()) {
                    this.options.add("Bidding/Selling.Bidding");
                }

                this.options.add("WhatIsThis.BiddingShop");
            }
        }

        for (final String key : this.options) {
            if (!this.config.contains("Settings.GUISettings.OtherSettings." + key)) {
                continue;
            }

            if (!this.config.getBoolean("Settings.GUISettings.OtherSettings." + key + ".Toggle", true)) {
                continue;
            }

            final String id = this.config.getString("Settings.GUISettings.OtherSettings." + key + ".Item");
            final String name = this.config.getString("Settings.GUISettings.OtherSettings." + key + ".Name");
            final List<String> lore = new ArrayList<>();
            final int slot = this.config.getInt("Settings.GUISettings.OtherSettings." + key + ".Slot");
            final String cName = Methods.color(this.config.getString("Settings.GUISettings.Category-Settings." + HolderManager.getShopCategory(this.player).getName() + ".Name"));

            final ItemBuilder itemBuilder = new ItemBuilder().setMaterial(id).setName(name).setAmount(1);

            if (this.config.contains("Settings.GUISettings.OtherSettings." + key + ".Lore")) {
                for (final String line : this.config.getStringList("Settings.GUISettings.OtherSettings." + key + ".Lore")) {
                    lore.add(line.replace("%Category%", cName).replace("%category%", cName));
                }
            }

            switch (key) {
                case "NextPage" -> this.inventory.setItem(slot - 1, InventoryManager.getNextButton(this.player, this).setLore(lore).build());

                case "PreviousPage" -> this.inventory.setItem(slot - 1, InventoryManager.getBackButton(this.player, this).setLore(lore).build());

                default -> this.inventory.setItem(slot - 1, itemBuilder.setLore(lore).addString(key, Keys.auction_button.getNamespacedKey()).build());
            }
        }

        for (final ItemStack item : getPageItems(this.items, getPage(), getSize())) {
            int slot = this.inventory.firstEmpty();

            this.inventory.setItem(slot, item);
        }

        this.player.openInventory(this.inventory);

        return this;
    }

    @Override
    public void run(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof AuctionsMenu menu)) return;

        event.setCancelled(true);

        final int slot = event.getSlot();

        final Inventory inventory = menu.getInventory();

        if (slot > inventory.getSize()) return;

        if (event.getCurrentItem() == null) return;

        final ItemStack itemStack = event.getCurrentItem();

        if (itemStack == null) return;

        final PersistentDataContainerView container = itemStack.getPersistentDataContainer();

        final Player player = (Player) event.getWhoClicked();

        FileConfiguration config = Files.config.getConfiguration();
        FileConfiguration data = Files.data.getConfiguration();

        if (container.has(Keys.auction_button.getNamespacedKey())) {
            String type = container.getOrDefault(Keys.auction_button.getNamespacedKey(), PersistentDataType.STRING, this.target == null ? "Refresh" : "");

            if (this.target == null && !type.isEmpty()) {
                switch (type) {
                    case "Your-Item", "Top-Bidder", "Cant-Afford" -> {
                        menu.click(player);

                        return;
                    }

                    case "NextPage" -> {
                        menu.click(player);

                        if (menu.getPage() >= menu.maxPages) {
                            return;
                        }

                        menu.nextPage();

                        GuiManager.openShop(player, HolderManager.getShopType(player), HolderManager.getShopCategory(player), menu.getPage());

                        return;
                    }

                    case "PreviousPage" -> {
                        menu.click(player);

                        final int page = menu.getPage();

                        if (page <= 1) {
                            return;
                        }

                        menu.backPage();

                        GuiManager.openShop(player, HolderManager.getShopType(player), HolderManager.getShopCategory(player), menu.getPage());

                        return;
                    }

                    case "Refesh", "Refresh" -> {
                        menu.click(player);

                        GuiManager.openShop(player, HolderManager.getShopType(player), HolderManager.getShopCategory(player), menu.getPage());

                        return;
                    }

                    case "Bidding/Selling.Selling" -> {
                        menu.click(player);

                        GuiManager.openShop(player, ShopType.BID, HolderManager.getShopCategory(player), 1);

                        return;
                    }

                    case "Bidding/Selling.Bidding" -> {
                        menu.click(player);

                        GuiManager.openShop(player, ShopType.SELL, HolderManager.getShopCategory(player), 1);

                        return;
                    }

                    case "Cancelled/ExpiredItems" -> {
                        menu.click(player);

                        GuiManager.openPlayersExpiredList(player, 1);

                        return;
                    }

                    case "SellingItems" -> {
                        menu.click(player);

                        GuiManager.openPlayersCurrentList(player, 1);

                        return;
                    }

                    case "Category1", "Category2" -> {
                        menu.click(player);

                        GuiManager.openCategories(player, HolderManager.getShopType(player));

                        return;
                    }
                }
            }
        }

        if (!data.contains("Items")) return;

        final ConfigurationSection section = data.getConfigurationSection("Items");

        if (section == null) return;

        final String auction_id = container.getOrDefault(Keys.auction_item.getNamespacedKey(), PersistentDataType.STRING, "");

        final ConfigurationSection auction = section.getConfigurationSection(auction_id);

        if (auction == null) return;

        final UUID uuid = player.getUniqueId();

        if (player.hasPermission("crazyauctions.admin") || player.hasPermission("crazyauctions.force-end")) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                int num = 1;

                for (;data.contains("OutOfTime/Cancelled." + num); num++);

                String seller = auction.getString("Seller");

                Player sellerPlayer = Methods.getPlayer(seller);

                if (Methods.isOnline(seller) && sellerPlayer != null) {
                    sellerPlayer.sendMessage(Messages.ADMIN_FORCE_CANCELLED_TO_PLAYER.getMessage(player));
                }

                AuctionCancelledEvent auctionCancelledEvent = new AuctionCancelledEvent((sellerPlayer != null ? sellerPlayer : Methods.getOfflinePlayer(seller)), Methods.fromBase64(auction.getString("Item")), Reasons.ADMIN_FORCE_CANCEL);
                this.server.getPluginManager().callEvent(auctionCancelledEvent);

                data.set("OutOfTime/Cancelled." + num + ".Seller", section.getString("Seller"));
                data.set("OutOfTime/Cancelled." + num + ".Name", section.getString("Name"));
                data.set("OutOfTime/Cancelled." + num + ".Full-Time", section.getLong("Full-Time"));
                data.set("OutOfTime/Cancelled." + num + ".StoreID", section.getInt("StoreID"));
                data.set("OutOfTime/Cancelled." + num + ".Item", auction.getString("Item"));
                data.set("Items." + auction_id, null);

                Files.data.save();

                player.sendMessage(Messages.ADMIN_FORCE_CANCELLED.getMessage(player));

                menu.click(player);

                GuiManager.openShop(player, HolderManager.getShopType(player), HolderManager.getShopCategory(player), menu.getPage());

                return;
            }
        }

        if (auction.getString("Seller", "").equalsIgnoreCase(uuid.toString())) {
            String itemName = config.getString("Settings.GUISettings.OtherSettings.Your-Item.Item");
            String name = config.getString("Settings.GUISettings.OtherSettings.Your-Item.Name");

            ItemBuilder itemBuilder = new ItemBuilder().setMaterial(itemName).setName(name).setAmount(1);

            if (config.contains("Settings.GUISettings.OtherSettings.Your-Item.Lore")) {
                itemBuilder.setLore(config.getStringList("Settings.GUISettings.OtherSettings.Your-Item.Lore"));
            }

            inventory.setItem(slot, itemBuilder.build());

            menu.click(player);

            new FoliaRunnable(this.plugin.getServer().getGlobalRegionScheduler()) {
                @Override
                public void run() {
                    inventory.setItem(slot, itemStack);
                }
            }.runDelayed(this.plugin, 3 * 20);

            return;
        }

        long cost = auction.getLong("Price");

        if (this.plugin.getSupport().getMoney(player) < cost) {
            String itemName = config.getString("Settings.GUISettings.OtherSettings.Cant-Afford.Item");
            String name = config.getString("Settings.GUISettings.OtherSettings.Cant-Afford.Name");

            ItemBuilder itemBuilder = new ItemBuilder().setMaterial(itemName).setName(name).setAmount(1);

            if (config.contains("Settings.GUISettings.OtherSettings.Cant-Afford.Lore")) {
                itemBuilder.setLore(config.getStringList("Settings.GUISettings.OtherSettings.Cant-Afford.Lore"));
            }

            inventory.setItem(slot, itemBuilder.build());
            menu.click(player);

            new FoliaRunnable(this.plugin.getServer().getGlobalRegionScheduler()) {
                @Override
                public void run() {
                    inventory.setItem(slot, itemStack);
                }
            }.runDelayed(this.plugin, 3 * 20);

            return;
        }

        if (auction.getBoolean("Biddable")) {
            if (uuid.toString().equalsIgnoreCase(auction.getString("TopBidder"))) {
                String itemName = config.getString("Settings.GUISettings.OtherSettings.Top-Bidder.Item");
                String name = config.getString("Settings.GUISettings.OtherSettings.Top-Bidder.Name");

                ItemBuilder itemBuilder = new ItemBuilder().setMaterial(itemName).setName(name).setAmount(1);

                if (config.contains("Settings.GUISettings.OtherSettings.Top-Bidder.Lore")) {
                    itemBuilder.setLore(config.getStringList("Settings.GUISettings.OtherSettings.Top-Bidder.Lore"));
                }

                inventory.setItem(slot, itemBuilder.build());

                menu.click(player);

                new FoliaRunnable(this.plugin.getServer().getGlobalRegionScheduler()) {
                    @Override
                    public void run() {
                        inventory.setItem(slot, itemStack);
                    }
                }.runDelayed(this.plugin, 3 * 20);

                return;
            }

            menu.click(player);

            GuiManager.openBidding(player, auction_id);
        } else {
            menu.click(player);

            GuiManager.openBuying(player, auction_id);
        }
    }

    private void getItems() {
        final ConfigurationSection section = this.data.getConfigurationSection("Items");

        if (section == null) return;

        for (String key : section.getKeys(false)) {
            final ConfigurationSection auction = section.getConfigurationSection(key);

            if (auction == null) continue;

            final String seller = auction.getString("Seller", "");

            if (seller.isEmpty()) continue;

            if (this.target != null && !this.target.isEmpty() && seller.equalsIgnoreCase(this.target)) {
                fetch(auction, seller);

                return;
            }

            fetch(auction, seller);
        }
    }

    private void fetch(final ConfigurationSection auction, final String seller) {
        final String item = auction.getString("Item", "");

        if (item.isEmpty()) return;

        final ItemBuilder itemBuilder = ItemBuilder.convertItemStack(item);

        if (itemBuilder == null) {
            this.plugin.getLogger().warning("The item with store id " + auction.getString("StoreID", "auctions_menu") + " obtained from your data.yml could not be converted!");

            return;
        }

        if (this.category != null && this.category != Category.NONE && !this.category.getItems().contains(itemBuilder.getMaterial())) return;

        final long price = auction.getLong("Price");

        final String priceFormat = String.format(Locale.ENGLISH, "%,d", price);

        final String player = auction.getString("Name", "None");

        final String time = Methods.convertToTime(auction.getLong("Time-Till-Expire"));

        final List<String> lore = new ArrayList<>(itemBuilder.getUpdatedLore());

        lore.add(" ");

        if (this.shopType != null && this.shopType == ShopType.BID && auction.getBoolean("Biddable") || auction.getBoolean("Biddable")) {
            final String top_bidder = auction.getString("TopBidderName", "None");

            for (final String line : this.config.getStringList("Settings.GUISettings.Bidding")) {
                String newLine = line.replace("%TopBid%", priceFormat).replace("%topbid%", priceFormat);

                newLine = line.replace("%Seller%", player).replace("%seller%", player);

                newLine = line.replace("%TopBidder%", top_bidder).replace("%topbid%", top_bidder);

                lore.add(newLine.replace("%Time%", time).replace("%time%", time));
            }
        } else {
            for (final String line : this.config.getStringList("Settings.GUISettings.SellingItemLore")) {
                String newLine = line.replace("%TopBid%", priceFormat).replace("%topbid%", priceFormat);

                newLine = line.replace("%Seller%", player).replace("%seller%", player);

                lore.add(newLine.replace("%Time%", time).replace("%time%", time).replace("%price%", priceFormat).replace("%Price%", priceFormat));
            }
        }

        itemBuilder.setLore(lore);

        itemBuilder.addInteger(auction.getInt("StoreID"), Keys.auction_id.getNamespacedKey());
        itemBuilder.addString(auction.getName(), Keys.auction_item.getNamespacedKey());

        this.items.add(itemBuilder.build());
    }
}