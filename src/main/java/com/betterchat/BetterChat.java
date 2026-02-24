package com.betterchat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/** Mod entry point. Registers the event handler on init. */
@Mod(modid = "betterchat", name = "BetterChat", version = "1.0", acceptedMinecraftVersions = "[1.8.9]")
public class BetterChat {

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ChatTabHandler());
    }
}



