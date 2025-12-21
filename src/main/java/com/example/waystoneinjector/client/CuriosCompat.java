package com.example.waystoneinjector.client;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

final class CuriosCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CuriosCompat() {
    }

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean warnedInitFailure;

    private static volatile boolean inventoryAvailable;
    private static volatile boolean helperAvailable;

    private static Method curiosApi_getCuriosInventory;
    private static Method curiosItemHandler_getCurios;
    private static Method curioStacksHandler_getStacks;
    private static Method dynamicStackHandler_getSlots;
    private static Method dynamicStackHandler_getStackInSlot;

    // Alternative detection path (more stable across Curios versions)
    private static Method curiosApi_getCuriosHelper;
    private static Method curiosHelper_findFirstCurio;

    // Open Curios menu (optional, reflection-based)
    private static volatile boolean openInit;
    private static volatile boolean openAvailable;
    private static volatile boolean warnedOpenInitFailure;
    private static Constructor<?> openPacketCtor;
    private static Object curiosNetworkChannel;
    private static Method curiosSendToServer;

    private static void ensureInit() {
        if (initialized) return;
        synchronized (CuriosCompat.class) {
            if (initialized) return;
            try {
                Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                // Strategy A: full inventory enumeration via CuriosApi.getCuriosInventory
                try {
                    curiosApi_getCuriosInventory = findSingleArgMethod(curiosApi, "getCuriosInventory", Player.class);

                    Class<?> curiosItemHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.ICuriosItemHandler");
                    curiosItemHandler_getCurios = findNoArgMethod(curiosItemHandler, "getCurios");

                    Class<?> curioStacksHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
                    curioStacksHandler_getStacks = findNoArgMethod(curioStacksHandler, "getStacks");

                    Class<?> dynamicStackHandler = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
                    dynamicStackHandler_getSlots = findNoArgMethod(dynamicStackHandler, "getSlots");
                    dynamicStackHandler_getStackInSlot = findSingleArgExact(dynamicStackHandler, "getStackInSlot", int.class);
                    inventoryAvailable = true;
                } catch (Throwable t) {
                    inventoryAvailable = false;
                    LOGGER.debug("Curios inventory reflection path unavailable", t);
                }

                // Strategy B: helper search via CuriosApi.getCuriosHelper().findFirstCurio
                try {
                    curiosApi_getCuriosHelper = findNoArgMethod(curiosApi, "getCuriosHelper");
                    Object helper = curiosApi_getCuriosHelper.invoke(null);
                    if (helper != null) {
                        curiosHelper_findFirstCurio = findFindFirstCurioMethod(helper.getClass());
                        helperAvailable = curiosHelper_findFirstCurio != null;
                    } else {
                        helperAvailable = false;
                    }
                } catch (Throwable t) {
                    helperAvailable = false;
                    curiosApi_getCuriosHelper = null;
                    curiosHelper_findFirstCurio = null;
                    LOGGER.debug("Curios helper reflection path unavailable", t);
                }

                available = inventoryAvailable || helperAvailable;
            } catch (Throwable t) {
                available = false;
                inventoryAvailable = false;
                helperAvailable = false;
                // If Curios is installed but reflection fails, surface it once at WARN.
                if (!warnedInitFailure && ModList.get().isLoaded("curios")) {
                    warnedInitFailure = true;
                    LOGGER.warn("Curios is installed but Curios API reflection init failed; Curios integration disabled: {}", t.toString());
                }
                LOGGER.debug("Curios API reflection init failed; Curios integration disabled", t);
            } finally {
                initialized = true;
            }
        }
    }

    private static Method findFindFirstCurioMethod(Class<?> helperType) {
        for (Method m : helperType.getMethods()) {
            if (!m.getName().equals("findFirstCurio")) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 2) continue;
            // Param 0 is LivingEntity in most versions; accept any supertype of Player.
            if (!params[0].isAssignableFrom(Player.class)) continue;
            if (!java.util.function.Predicate.class.isAssignableFrom(params[1])) continue;
            return m;
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != 0) continue;
            return m;
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + "() not found");
    }

    private static Method findSingleArgExact(Class<?> type, String name, Class<?> param) throws NoSuchMethodException {
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1) continue;
            if (params[0] != param) continue;
            return m;
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + "(" + param.getName() + ") not found");
    }

    private static Method findSingleArgMethod(Class<?> type, String name, Class<?> argType) throws NoSuchMethodException {
        // Curios has changed signatures across versions (e.g., Player vs LivingEntity).
        // We accept any overload where the single parameter is a supertype of argType.
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 1) continue;
            if (!params[0].isAssignableFrom(argType)) continue;
            return m;
        }
        // Fallback: accept any one-arg overload by name.
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != 1) continue;
            return m;
        }
        throw new NoSuchMethodException(type.getName() + "#" + name + "(<any>) not found");
    }

    static boolean requestOpenCuriosMenu(Player player, ItemStack carried) {
        // Curios opens its menu server-side via a Curios C2S packet.
        // We reflectively construct that packet and send it through Curios' own SimpleChannel.
        ensureOpenInit();
        if (!openAvailable) return false;

        try {
            ItemStack carriedSafe = carried == null ? ItemStack.EMPTY : carried;
            Object pkt = openPacketCtor.newInstance(carriedSafe);
            curiosSendToServer.invoke(curiosNetworkChannel, pkt);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Curios open menu send failed: {}", t.toString());
            LOGGER.debug("Curios open menu send failure details", t);
            return false;
        }
    }

    private static void ensureOpenInit() {
        if (openInit) return;
        synchronized (CuriosCompat.class) {
            if (openInit) return;
            try {
                // Curios has moved this packet class across versions; try a few known locations.
                Class<?> pktClass = null;
                String[] candidates = new String[]{
                    "top.theillusivec4.curios.common.network.client.CPacketOpenCurios",
                    "top.theillusivec4.curios.common.network.packets.CPacketOpenCurios",
                    "top.theillusivec4.curios.common.network.CPacketOpenCurios"
                };
                for (String name : candidates) {
                    try {
                        pktClass = Class.forName(name);
                        break;
                    } catch (Throwable ignored) {
                    }
                }
                if (pktClass == null) throw new ClassNotFoundException("CPacketOpenCurios not found in known packages");
                openPacketCtor = pktClass.getConstructor(ItemStack.class);

                // Use Curios' own network channel: top.theillusivec4.curios.common.network.NetworkHandler.INSTANCE
                Class<?> handlerClass = Class.forName("top.theillusivec4.curios.common.network.NetworkHandler");
                Field instanceField = handlerClass.getField("INSTANCE");
                curiosNetworkChannel = instanceField.get(null);
                if (curiosNetworkChannel == null) throw new IllegalStateException("Curios NetworkHandler.INSTANCE is null");

                // net.minecraftforge.network.simple.SimpleChannel#sendToServer(Object)
                Method sendToServer = null;
                for (Method m : curiosNetworkChannel.getClass().getMethods()) {
                    if (!m.getName().equals("sendToServer")) continue;
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length != 1) continue;
                    sendToServer = m;
                    break;
                }
                if (sendToServer == null) throw new NoSuchMethodException("SimpleChannel.sendToServer not found");
                curiosSendToServer = sendToServer;

                openAvailable = true;
            } catch (Throwable t) {
                openAvailable = false;
                openPacketCtor = null;
                curiosNetworkChannel = null;
                curiosSendToServer = null;
                if (!warnedOpenInitFailure && ModList.get().isLoaded("curios")) {
                    warnedOpenInitFailure = true;
                    LOGGER.warn("Curios is installed but Curios open-menu reflection init failed; Curios menu open disabled: {}", t.toString());
                }
                LOGGER.debug("Curios open-menu reflection init failed; Curios menu open disabled", t);
            } finally {
                openInit = true;
            }
        }
    }

    static String findWaystonesTeleportItemType(Player player) {
        ensureInit();
        if (!available) return "regular";

        if (inventoryAvailable) {
            try {
                @SuppressWarnings("unchecked")
                Optional<Object> curiosInventory = (Optional<Object>) curiosApi_getCuriosInventory.invoke(null, player);
                if (curiosInventory != null && curiosInventory.isPresent()) {
                    Object curiosHandler = curiosInventory.get();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> curios = (Map<String, Object>) curiosItemHandler_getCurios.invoke(curiosHandler);
                    if (curios != null) {
                        for (Object stacksHandler : curios.values()) {
                            Object stacks = curioStacksHandler_getStacks.invoke(stacksHandler);
                            if (stacks == null) continue;

                            int slots = (int) dynamicStackHandler_getSlots.invoke(stacks);
                            for (int i = 0; i < slots; i++) {
                                ItemStack stack = (ItemStack) dynamicStackHandler_getStackInSlot.invoke(stacks, i);
                                String type = checkItemType(stack);
                                if (!type.equals("regular")) {
                                    return type;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                // Continue to helper strategy.
                LOGGER.debug("Curios inventory scan failed; falling back to helper strategy", t);
            }
        }

        if (helperAvailable) {
            try {
                Object helper = curiosApi_getCuriosHelper.invoke(null);
                if (helper == null) return "regular";

                java.util.function.Predicate<ItemStack> pred = stack -> !checkItemType(stack).equals("regular");
                @SuppressWarnings("unchecked")
                Optional<Object> resultOpt = (Optional<Object>) curiosHelper_findFirstCurio.invoke(helper, player, pred);
                if (resultOpt == null || resultOpt.isEmpty()) return "regular";

                ItemStack stack = extractStackFromSlotResult(resultOpt.get());
                return checkItemType(stack);
            } catch (Throwable t) {
                return "regular";
            }
        }

        return "regular";
    }

    private static ItemStack extractStackFromSlotResult(Object slotResult) {
        if (slotResult == null) return ItemStack.EMPTY;

        try {
            // 1.20+ Curios commonly uses a SlotResult record with accessor stack()
            Method m = slotResult.getClass().getMethod("stack");
            Object v = m.invoke(slotResult);
            if (v instanceof ItemStack s) return s;
        } catch (Throwable ignored) {
        }

        try {
            Method m = slotResult.getClass().getMethod("getStack");
            Object v = m.invoke(slotResult);
            if (v instanceof ItemStack s) return s;
        } catch (Throwable ignored) {
        }

        return ItemStack.EMPTY;
    }

    private static String checkItemType(ItemStack itemStack) {
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
