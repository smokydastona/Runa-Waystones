package com.example.waystoneinjector.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE, modid = "waystoneinjector")
public final class KeybindHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String KEY_CATEGORY = "key.categories.waystoneinjector";
    private static final String KEY_USE_TELEPORT_ITEM = "key.waystoneinjector.use_teleport_item";
    private static final String KEY_OPEN_VAULT = "key.waystoneinjector.open_vault";

    private static KeyMapping useTeleportItem;
    private static KeyMapping openVault;

    private static boolean loggedWaystonesBindings;
    private static boolean loggedNoTeleportItemOnce;

    // Prevent repeated activations from key spam / key repeat.
    private static int keybindCooldownTicks = 0;

    private static int pendingUseHotbarSlot = -1;
    private static int pendingRestoreHotbarSlot = -1;
    private static int pendingSwapSourceSlotId = -1;
    private static int pendingSwapHotbarSlotId = -1;
    private static int pendingMenuBaseContainerId = -1;
    private static int pendingDelayTicks = 0;
    private static int pendingMenuWaitTicks = 0;
    private static int pendingCuriosOriginalSourceSlotId = -1;
    private static int pendingHoldUseTicks = 0;
    private static boolean pendingHoldPrevUseDown = false;
    private static PendingAction pendingPostScreenAction = PendingAction.NONE;
    private static PendingAction pendingAction = PendingAction.NONE;
    private static PendingAction pendingAfterUseAction = PendingAction.NONE;

    private enum PendingAction {
        NONE,

        // Wait until the current screen is closed before continuing.
        WAIT_SCREEN_CLOSE,

        // Hotbar switch/use is done over multiple ticks to ensure server sees the carried-slot update.
        HOTBAR_SWITCH,
        HOTBAR_USE,
        HOTBAR_RESTORE,

        // Hold right-click for a short period so Waystones items that require charging actually complete.
        HOLD_USE,

        // Swapping is done over multiple ticks to ensure server sees the click packets.
        SWAP_IN,
        SWAP_USE,
        SWAP_BACK,

        // Curios path: ask Curios to open its menu, then swap item into hotbar, close, use, reopen, swap back.
        CURIOS_OPEN_FOR_SWAP_IN,
        CURIOS_WAIT_MENU_FOR_SWAP_IN,
        CURIOS_CLOSE_BEFORE_USE,
        CURIOS_OPEN_FOR_SWAP_BACK,
        CURIOS_WAIT_MENU_FOR_SWAP_BACK,
        CURIOS_CLOSE_AFTER_SWAP_BACK
    }

    private KeybindHandler() {
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // Default to Right-Alt; players can rebind as needed.
        useTeleportItem = new KeyMapping(KEY_USE_TELEPORT_ITEM, GLFW.GLFW_KEY_RIGHT_ALT, KEY_CATEGORY);
        event.register(useTeleportItem);
        LOGGER.info("Registered keybind {} (default=RIGHT_ALT)", KEY_USE_TELEPORT_ITEM);

        // Optional server-side vault: default to V; safe when server mod isn't present (we guard at runtime).
        openVault = new KeyMapping(KEY_OPEN_VAULT, GLFW.GLFW_KEY_V, KEY_CATEGORY);
        event.register(openVault);
        LOGGER.info("Registered keybind {} (default=V)", KEY_OPEN_VAULT);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!loggedWaystonesBindings) {
            loggedWaystonesBindings = true;
            tryLogWaystonesKeybinds(mc);
        }

        if (useTeleportItem == null) return;

        if (openVault != null && openVault.consumeClick()) {
            // Don't conflict with in-progress teleport-item sequences.
            if (pendingAction == PendingAction.NONE && keybindCooldownTicks <= 0) {
                com.example.waystoneinjector.client.serverside.ServerSideNetwork.requestOpenVault(true);
                keybindCooldownTicks = 10;
            }

            while (openVault.consumeClick()) {
                // flush
            }
        }

        if (keybindCooldownTicks > 0) {
            keybindCooldownTicks--;
        }

        // Process any queued hotbar use sequence first.
        if (pendingAction != PendingAction.NONE) {
            processPending(mc);
            return;
        }

        // If we're on cooldown, consume and discard queued clicks so they don't replay later.
        if (keybindCooldownTicks > 0) {
            while (useTeleportItem.consumeClick()) {
                // discard
            }
            return;
        }

        // Single-press semantics: run once, then flush any extra queued presses.
        if (useTeleportItem.consumeClick()) {
            // Small cooldown so key repeat / spam doesn't trigger overlapping sequences.
            keybindCooldownTicks = 10;
            tryUseTeleportItem(mc);
            while (useTeleportItem.consumeClick()) {
                // flush
            }
        }
    }

    private static void processPending(Minecraft mc) {
        Player player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) {
            clearPending();
            return;
        }

        // Some Waystones items only open their menu after being "used" (held) for a duration.
        // Also: never continue swap-back/restore while a screen is open, or we can interrupt the Waystones UI.
        if (pendingAction == PendingAction.WAIT_SCREEN_CLOSE) {
            if (mc.screen != null) {
                return;
            }
            PendingAction next = pendingPostScreenAction;
            pendingPostScreenAction = PendingAction.NONE;
            pendingDelayTicks = 1;
            pendingAction = next == PendingAction.NONE ? PendingAction.NONE : next;
            return;
        }

        if (pendingDelayTicks > 0) {
            pendingDelayTicks--;
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            clearPending();
            return;
        }

        switch (pendingAction) {
            case HOTBAR_SWITCH -> {
                if (pendingUseHotbarSlot < 0 || pendingUseHotbarSlot > 8) {
                    clearPending();
                    return;
                }
                setSelectedHotbarSlot(mc, player, pendingUseHotbarSlot);
                // Wait a tick so the server has processed the carried-slot packet.
                pendingDelayTicks = 1;
                pendingAction = PendingAction.HOTBAR_USE;
            }
            case HOTBAR_USE -> {
                // Use the selected hotbar item using the same path as a real right-click.
                useItem(mc, player, InteractionHand.MAIN_HAND);

                PendingAction nextAfterUse = pendingAfterUseAction != PendingAction.NONE ? pendingAfterUseAction : PendingAction.HOTBAR_RESTORE;
                pendingAfterUseAction = PendingAction.NONE;
                beginHoldUse(mc, nextAfterUse);
            }
            case HOTBAR_RESTORE -> {
                if (pendingRestoreHotbarSlot >= 0 && pendingRestoreHotbarSlot <= 8) {
                    setSelectedHotbarSlot(mc, player, pendingRestoreHotbarSlot);
                }
                clearPending();
            }
            case HOLD_USE -> {
                // Stop holding if a screen opens (Waystones menu) or time expires.
                if (mc.screen != null || pendingHoldUseTicks-- <= 0) {
                    endHoldUse(mc);
                    // Wait until user closes the Waystones screen before we do any restore/swap-back.
                    pendingAction = PendingAction.WAIT_SCREEN_CLOSE;
                    return;
                }
            }
            case SWAP_IN -> {
                if (pendingSwapSourceSlotId < 0 || pendingSwapHotbarSlotId < 0) {
                    clearPending();
                    return;
                }

                if (!menu.getCarried().isEmpty()) {
                    clearPending();
                    return;
                }

                if (!swapSlots(menu, player, pendingSwapSourceSlotId, pendingSwapHotbarSlotId)) {
                    clearPending();
                    return;
                }

                // Wait a tick so the server has processed the click packets.
                pendingDelayTicks = 1;
                pendingAction = PendingAction.SWAP_USE;
            }
            case SWAP_USE -> {
                useItem(mc, player, InteractionHand.MAIN_HAND);
                beginHoldUse(mc, PendingAction.SWAP_BACK);
            }
            case SWAP_BACK -> {
                if (pendingSwapSourceSlotId >= 0 && pendingSwapHotbarSlotId >= 0 && menu.getCarried().isEmpty()) {
                    swapSlots(menu, player, pendingSwapSourceSlotId, pendingSwapHotbarSlotId);
                }
                clearPending();
            }
            case CURIOS_OPEN_FOR_SWAP_IN -> {
                if (!ModList.get().isLoaded("curios")) {
                    clearPending();
                    return;
                }

                if (!menu.getCarried().isEmpty()) {
                    clearPending();
                    return;
                }

                pendingMenuBaseContainerId = menu.containerId;
                pendingMenuWaitTicks = 40;

                if (!CuriosCompat.requestOpenCuriosMenu(player, menu.getCarried())) {
                    clearPending();
                    return;
                }

                pendingDelayTicks = 1;
                pendingAction = PendingAction.CURIOS_WAIT_MENU_FOR_SWAP_IN;
            }
            case CURIOS_WAIT_MENU_FOR_SWAP_IN -> {
                if (pendingMenuWaitTicks-- <= 0) {
                    clearPending();
                    return;
                }

                AbstractContainerMenu currentMenu = player.containerMenu;
                if (currentMenu == null) {
                    clearPending();
                    return;
                }

                // Wait until the server has opened a new container.
                if (currentMenu.containerId == pendingMenuBaseContainerId) {
                    pendingDelayTicks = 1;
                    return;
                }

                int hotbarIndex = player.getInventory().selected;
                int hotbarSlotId = findPlayerInventorySlotId(currentMenu, player, hotbarIndex);
                int sourceSlotId = findTeleportItemSlotId(currentMenu, player);
                if (hotbarSlotId < 0 || sourceSlotId < 0) {
                    clearPending();
                    return;
                }

                if (!currentMenu.getCarried().isEmpty()) {
                    clearPending();
                    return;
                }

                // Move the item into the selected hotbar slot but DO NOT swap back yet.
                if (!swapSlots(currentMenu, player, sourceSlotId, hotbarSlotId)) {
                    clearPending();
                    return;
                }

                pendingCuriosOriginalSourceSlotId = sourceSlotId;
                // After we use the item from hotbar, reopen Curios and swap it back.
                pendingAfterUseAction = PendingAction.CURIOS_OPEN_FOR_SWAP_BACK;

                pendingDelayTicks = 1;
                pendingAction = PendingAction.CURIOS_CLOSE_BEFORE_USE;
            }
            case CURIOS_CLOSE_BEFORE_USE -> {
                if (menu.containerId != 0) {
                    player.closeContainer();
                }
                pendingDelayTicks = 2;
                pendingAction = PendingAction.HOTBAR_USE;
            }
            case CURIOS_OPEN_FOR_SWAP_BACK -> {
                if (!ModList.get().isLoaded("curios")) {
                    clearPending();
                    return;
                }
                pendingMenuBaseContainerId = menu.containerId;
                pendingMenuWaitTicks = 40;

                if (!CuriosCompat.requestOpenCuriosMenu(player, menu.getCarried())) {
                    clearPending();
                    return;
                }

                pendingDelayTicks = 1;
                pendingAction = PendingAction.CURIOS_WAIT_MENU_FOR_SWAP_BACK;
            }
            case CURIOS_WAIT_MENU_FOR_SWAP_BACK -> {
                if (pendingMenuWaitTicks-- <= 0) {
                    clearPending();
                    return;
                }

                AbstractContainerMenu currentMenu = player.containerMenu;
                if (currentMenu == null) {
                    clearPending();
                    return;
                }

                if (currentMenu.containerId == pendingMenuBaseContainerId) {
                    pendingDelayTicks = 1;
                    return;
                }

                if (pendingCuriosOriginalSourceSlotId < 0 || pendingCuriosOriginalSourceSlotId >= currentMenu.slots.size()) {
                    clearPending();
                    return;
                }

                int hotbarIndex = player.getInventory().selected;
                int hotbarSlotId = findPlayerInventorySlotId(currentMenu, player, hotbarIndex);
                if (hotbarSlotId < 0) {
                    clearPending();
                    return;
                }

                pendingSwapSourceSlotId = pendingCuriosOriginalSourceSlotId;
                pendingSwapHotbarSlotId = hotbarSlotId;

                // Swap back is the same operation (swapSlots), just opposite direction.
                if (!currentMenu.getCarried().isEmpty()) {
                    clearPending();
                    return;
                }

                if (!swapSlots(currentMenu, player, pendingSwapSourceSlotId, pendingSwapHotbarSlotId)) {
                    clearPending();
                    return;
                }

                pendingDelayTicks = 1;
                pendingAction = PendingAction.CURIOS_CLOSE_AFTER_SWAP_BACK;
            }
            case CURIOS_CLOSE_AFTER_SWAP_BACK -> {
                if (menu.containerId != 0) {
                    player.closeContainer();
                }
                clearPending();
            }
            default -> clearPending();
        }
    }

    private static void beginHoldUse(Minecraft mc, PendingAction nextActionAfterScreenClose) {
        // Waystones uses "hold to use" for warp stone/scroll in some cases.
        // We simulate holding the use key for a short, fixed duration.
        // (Using a fixed duration avoids depending on obfuscated mappings for getUseDuration.)
        if (mc.options == null) {
            // Should never happen, but fail safe.
            pendingDelayTicks = 2;
            pendingAction = nextActionAfterScreenClose;
            return;
        }

        pendingHoldPrevUseDown = mc.options.keyUse.isDown();
        mc.options.keyUse.setDown(true);
        pendingHoldUseTicks = 40;
        pendingPostScreenAction = nextActionAfterScreenClose;
        pendingAction = PendingAction.HOLD_USE;
    }

    private static void endHoldUse(Minecraft mc) {
        if (mc.options != null) {
            mc.options.keyUse.setDown(pendingHoldPrevUseDown);
        }
        pendingHoldUseTicks = 0;
    }

    private static void clearPending() {
        pendingUseHotbarSlot = -1;
        pendingRestoreHotbarSlot = -1;
        pendingSwapSourceSlotId = -1;
        pendingSwapHotbarSlotId = -1;
        pendingMenuBaseContainerId = -1;
        pendingDelayTicks = 0;
        pendingMenuWaitTicks = 0;
        pendingCuriosOriginalSourceSlotId = -1;
        pendingHoldUseTicks = 0;
        pendingHoldPrevUseDown = false;
        pendingPostScreenAction = PendingAction.NONE;
        pendingAction = PendingAction.NONE;
        pendingAfterUseAction = PendingAction.NONE;
    }

    private static void tryLogWaystonesKeybinds(Minecraft mc) {
        try {
            for (KeyMapping mapping : mc.options.keyMappings) {
                String name = mapping.getName();
                if (name == null) continue;
                if (name.startsWith("key.waystones")) {
                    LOGGER.debug("Detected Waystones keybind: {}", name);
                }
            }
        } catch (Throwable t) {
            // Optional log only
        }
    }

    private static void tryUseTeleportItem(Minecraft mc) {
        Player player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;

        // Prefer hand items (works even when no GUI is open).
        String mainHandType = getTeleportItemType(player.getMainHandItem());
        if (!mainHandType.equals("regular")) {
            useItem(mc, player, InteractionHand.MAIN_HAND);
            beginHoldUse(mc, PendingAction.NONE);
            return;
        }

        String offHandType = getTeleportItemType(player.getOffhandItem());
        if (!offHandType.equals("regular")) {
            useItem(mc, player, InteractionHand.OFF_HAND);
            beginHoldUse(mc, PendingAction.NONE);
            return;
        }

        // Prefer hotbar items (works even when no GUI is open).
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!getTeleportItemType(stack).equals("regular")) {
                int prevSelected = player.getInventory().selected;
                if (i == prevSelected) {
                    useItem(mc, player, InteractionHand.MAIN_HAND);
                    beginHoldUse(mc, PendingAction.NONE);
                    return;
                }

                // Queue switch -> use -> restore across ticks for reliability.
                pendingUseHotbarSlot = i;
                pendingRestoreHotbarSlot = prevSelected;
                pendingAction = PendingAction.HOTBAR_SWITCH;
                return;
            }
        }

        // Pull from inventory/curios slots by swapping into the selected hotbar slot.
        // This can work even when no GUI is open because the player still has an active container menu.
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        if (!menu.getCarried().isEmpty()) {
            LOGGER.debug("Skipping teleport keybind: cursor is carrying an item");
            return;
        }

        int hotbarIndex = player.getInventory().selected;
        int hotbarSlotId = findPlayerInventorySlotId(menu, player, hotbarIndex);
        if (hotbarSlotId < 0) {
            LOGGER.debug("Could not locate selected hotbar slot in menu");
            return;
        }

        int sourceSlotId = findTeleportItemSlotId(menu, player);
        if (sourceSlotId < 0) {
            // If Curios has it, request Curios menu open and try swapping from there.
            boolean curiosLoaded = ModList.get().isLoaded("curios");
            String curiosType = curiosLoaded ? CuriosCompat.findWaystonesTeleportItemType(player) : "regular";
            if (curiosLoaded && !curiosType.equals("regular")) {
                pendingAction = PendingAction.CURIOS_OPEN_FOR_SWAP_IN;
                return;
            }

            if (!loggedNoTeleportItemOnce) {
                loggedNoTeleportItemOnce = true;
                LOGGER.info(
                    "Teleport keybind: no Waystones teleport item found (curiosLoaded={}, curiosType={})",
                    curiosLoaded,
                    curiosType
                );
            }
            return;
        }

        if (sourceSlotId == hotbarSlotId) {
            useItem(mc, player, InteractionHand.MAIN_HAND);
            return;
        }

        // Swap-in -> use -> swap-back across ticks for reliability.
        pendingSwapSourceSlotId = sourceSlotId;
        pendingSwapHotbarSlotId = hotbarSlotId;
        pendingAction = PendingAction.SWAP_IN;
    }

    private static int findTeleportItemSlotId(AbstractContainerMenu menu, Player player) {
        int bestCuriosLikeSlotId = -1;
        int bestPlayerInventorySlotId = -1;

        for (int slotId = 0; slotId < menu.slots.size(); slotId++) {
            Slot slot = menu.slots.get(slotId);

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (getTeleportItemType(stack).equals("regular")) continue;

            boolean isPlayerInv = slot.container == player.getInventory();
            if (!isPlayerInv) {
                // Heuristic: Curios slots generally live in Curios packages/classes.
                String cls = slot.getClass().getName().toLowerCase();
                String containerCls = slot.container.getClass().getName().toLowerCase();
                if (cls.contains("curio") || containerCls.contains("curios")) {
                    bestCuriosLikeSlotId = slotId;
                    break;
                }
                if (bestCuriosLikeSlotId < 0) bestCuriosLikeSlotId = slotId;
            } else if (bestPlayerInventorySlotId < 0) {
                bestPlayerInventorySlotId = slotId;
            }
        }

        return bestCuriosLikeSlotId >= 0 ? bestCuriosLikeSlotId : bestPlayerInventorySlotId;
    }

    private static int findPlayerInventorySlotId(AbstractContainerMenu menu, Player player, int inventoryIndex) {
        for (int slotId = 0; slotId < menu.slots.size(); slotId++) {
            Slot slot = menu.slots.get(slotId);
            if (slot.container != player.getInventory()) continue;

            // Official mappings expose this as getContainerSlot() on Slot in 1.20.1.
            if (slot.getContainerSlot() == inventoryIndex) {
                return slotId;
            }
        }
        return -1;
    }

    private static boolean swapSlots(AbstractContainerMenu menu, Player player, int a, int b) {
        Minecraft mc = Minecraft.getInstance();
        var gameMode = mc.gameMode;
        if (gameMode == null) return false;

        if (!menu.getCarried().isEmpty()) return false;

        try {
            // Some Forge nullness annotations declare a @Nonnull Player parameter; the local player is non-null here.
            @SuppressWarnings("null")
            Player nonNullPlayer = Objects.requireNonNull(player, "player");

            gameMode.handleInventoryMouseClick(menu.containerId, a, 0, ClickType.PICKUP, nonNullPlayer);
            gameMode.handleInventoryMouseClick(menu.containerId, b, 0, ClickType.PICKUP, nonNullPlayer);
            gameMode.handleInventoryMouseClick(menu.containerId, a, 0, ClickType.PICKUP, nonNullPlayer);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Inventory swap failed: {}", t.toString());
            LOGGER.debug("Inventory swap failure details", t);
            return false;
        }
    }

    private static void setSelectedHotbarSlot(Minecraft mc, Player player, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return;

        player.getInventory().selected = hotbarSlot;
        var connection = mc.getConnection();
        if (connection != null) {
            connection.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
        }
    }

    private static void useItem(Minecraft mc, Player player, InteractionHand hand) {
        var gameMode = mc.gameMode;
        if (gameMode == null) return;

        // Don’t try to use items while a screen is open; vanilla also blocks this.
        if (mc.screen != null) return;

        // Forge nullness annotations may declare @Nonnull parameters.
        @SuppressWarnings("null")
        Player nonNullPlayer = Objects.requireNonNull(player, "player");
        @SuppressWarnings("null")
        InteractionHand nonNullHand = Objects.requireNonNull(hand, "hand");
        gameMode.useItem(nonNullPlayer, nonNullHand);
    }

    private static String getTeleportItemType(ItemStack itemStack) {
        if (itemStack.isEmpty()) return "regular";

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        if (itemId == null || !itemId.getNamespace().equals("waystones")) return "regular";

        String path = itemId.getPath();
        if (path.contains("warp_scroll")) return "warp_scroll";
        if (path.contains("bound_scroll")) return "bound_scroll";
        if (path.contains("warp_stone")) return "warp_stone";
        if (path.contains("return_scroll")) return "return_scroll";

        return "regular";
    }
}
