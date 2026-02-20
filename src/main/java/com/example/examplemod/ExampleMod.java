package com.example.examplemod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "cleanchat", version = "1.0", acceptedMinecraftVersions = "[1.8.9]")
public class ExampleMod {

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // This is the "on switch" for your ChatTabHandler class
        MinecraftForge.EVENT_BUS.register(new ChatTabHandler());

        System.out.println("CleanChat Mod Initialized!");
    }
}