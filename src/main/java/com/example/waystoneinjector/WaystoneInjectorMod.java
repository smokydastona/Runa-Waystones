package com.example.waystoneinjector;

import com.example.waystoneinjector.config.WaystoneConfig;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WaystoneInjectorMod.MODID)
public class WaystoneInjectorMod {
    public static final String MODID = "waystoneinjector";

    private static final Logger LOGGER = LogUtils.getLogger();

    public WaystoneInjectorMod() {
        LOGGER.info("Initializing {} (dist={})", MODID, FMLEnvironment.dist);
        
        // Client-side only mod - only register on client
        if (FMLEnvironment.dist.isClient()) {
            LOGGER.info("Client-side detected; registering client handlers");
            
            // Register config
            LOGGER.debug("Registering config");
            WaystoneConfig.register();
            LOGGER.debug("Config registered");

            // Add a "Config" button entry in the Forge Mods list.
            ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                    (mc, parent) -> new com.example.waystoneinjector.client.gui.WaystoneInjectorConfigScreen(parent)
                )
            );

            // Optional networking bridge to the server-side sister mod.
            // Safe when the server doesn't have the mod: we only send when the channel is present.
            com.example.waystoneinjector.client.serverside.ServerSideNetwork.init();
            
            // Register client-only event handlers (static methods require class registration)
            LOGGER.debug("Registering ClientEvents");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.example.waystoneinjector.client.ClientEvents.class);
            LOGGER.debug("ClientEvents registered");

            // Register keybind handler (Forge bus for tick + Mod bus for key registration)
            LOGGER.debug("Registering KeybindHandler");
            IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

            // Log config load/reload so we can confirm the file is being read.
            modBus.addListener((ModConfigEvent.Loading e) -> {
                if (e.getConfig().getSpec() == WaystoneConfig.SPEC) {
                    LOGGER.info("Loaded client config: button1.enabled={} netherPortal.variant={}",
                        WaystoneConfig.BUTTON1_ENABLED.get(),
                        WaystoneConfig.NETHER_PORTAL_VARIANT.get());
                }
            });
            modBus.addListener((ModConfigEvent.Reloading e) -> {
                if (e.getConfig().getSpec() == WaystoneConfig.SPEC) {
                    LOGGER.info("Reloaded client config: button1.enabled={} netherPortal.variant={}",
                        WaystoneConfig.BUTTON1_ENABLED.get(),
                        WaystoneConfig.NETHER_PORTAL_VARIANT.get());
                }
            });

            modBus.addListener(com.example.waystoneinjector.client.KeybindHandler::onRegisterKeyMappings);
            LOGGER.debug("KeybindHandler registered");

            // Register built-in resource pack finders (nether portal texture variants)
            LOGGER.debug("Registering NetherPortalResourcePacks");
            modBus.addListener(com.example.waystoneinjector.client.NetherPortalResourcePacks::onAddPackFinders);
            LOGGER.debug("NetherPortalResourcePacks registered");
            
            // Register built-in death and sleep event handlers (client-side detection)
            LOGGER.debug("Registering DeathSleepEvents");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.example.waystoneinjector.client.DeathSleepEvents.class);
            LOGGER.debug("DeathSleepEvents registered");
            
            // Register resource pack handler (auto-accept during redirects)
            LOGGER.debug("Registering ResourcePackHandler");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.example.waystoneinjector.client.ResourcePackHandler.class);
            LOGGER.debug("ResourcePackHandler registered");
            
            // Register server settings manager (auto-configure resource packs)
            LOGGER.debug("Registering ServerSettingsManager");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.example.waystoneinjector.client.ServerSettingsManager.class);
            LOGGER.debug("ServerSettingsManager registered");

            // Apply configured nether portal resource pack selection
            LOGGER.debug("Registering NetherPortalPackApplier");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(com.example.waystoneinjector.client.NetherPortalPackApplier.class);
            LOGGER.debug("NetherPortalPackApplier registered");

            LOGGER.info("{} initialization complete", MODID);
        } else {
            LOGGER.info("Server-side detected; skipping client initialization");
        }
    }
}
