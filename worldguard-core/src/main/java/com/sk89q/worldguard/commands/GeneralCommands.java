/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.commands;

import com.google.common.collect.Lists;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.auth.AuthorizationException;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.GodMode;
import org.enginehub.piston.exception.CommandException;

public class GeneralCommands {
    private final WorldGuard worldGuard;

    public GeneralCommands(WorldGuard worldGuard) {
        this.worldGuard = worldGuard;
    }
    
    @Command(aliases = {"god"}, usage = "[player]",
            desc = "Enable godmode on a player", flags = "s", max = 1)
    public void god(CommandContext args, Actor sender) throws CommandException, AuthorizationException {
        Iterable<? extends LocalPlayer> targets = null;
        boolean included = false;
        
        // Detect arguments based on the number of arguments provided
        if (args.argsLength() == 0) {
            targets = worldGuard.getPlatform().getMatcher().matchPlayers(worldGuard.checkPlayer(sender));
            
            // Check permissions!
            sender.checkPermission("worldguard.god");
        } else {
            targets = worldGuard.getPlatform().getMatcher().matchPlayers(sender, args.getString(0));
            
            // Check permissions!
            sender.checkPermission("worldguard.god.other");
        }

        for (LocalPlayer player : targets) {
            Session session = WorldGuard.getInstance().getPlatform().getSessionManager().get(player);

            if (GodMode.set(player, session, true)) {
                player.setFireTicks(0);

                // Tell the user
                if (player.equals(sender)) {
                    player.print(TranslatableComponent.of("worldguard.command.general.god.enable"));

                    // Keep track of this
                    included = true;
                } else if (!args.hasFlag('s')) {
                    player.print(TranslatableComponent.of("worldguard.command.general.god.enable-by-other")
                            .args(TextComponent.of(sender.getDisplayName())));
                }
            }
        }
        
        // The player didn't receive any items, then we need to send the
        // user a message so s/he know that something is indeed working
        if (!included) {
            sender.print(TranslatableComponent.of("worldguard.command.general.god.enabled-other"));
        }
    }
    
    @Command(aliases = {"ungod"}, usage = "[player]",
            desc = "Disable godmode on a player", flags = "s", max = 1)
    public void ungod(CommandContext args, Actor sender) throws CommandException, AuthorizationException {
        Iterable<? extends LocalPlayer> targets;
        boolean included = false;
        
        // Detect arguments based on the number of arguments provided
        if (args.argsLength() == 0) {
            targets = worldGuard.getPlatform().getMatcher().matchPlayers(worldGuard.checkPlayer(sender));
            
            // Check permissions!
            sender.checkPermission("worldguard.god");
        } else {
            targets = worldGuard.getPlatform().getMatcher().matchPlayers(sender, args.getString(0));
            
            // Check permissions!
            sender.checkPermission("worldguard.god.other");
        }

        for (LocalPlayer player : targets) {
            Session session = WorldGuard.getInstance().getPlatform().getSessionManager().get(player);

            if (GodMode.set(player, session, false)) {
                // Tell the user
                if (player.equals(sender)) {
                    player.print(TranslatableComponent.of("worldguard.command.general.ungod.disable"));

                    // Keep track of this
                    included = true;
                } else if (!args.hasFlag('s')) {
                    player.print(TranslatableComponent.of("worldguard.command.general.ungod.disable-by-other")
                            .args(TextComponent.of(sender.getDisplayName())));
                }
            }
        }
        
        // The player didn't receive any items, then we need to send the
        // user a message so s/he know that something is indeed working
        if (!included) {
            sender.print(TranslatableComponent.of("worldguard.command.general.ungod.disabled-other"));        }
    }
    
    @Command(aliases = {"heal"}, usage = "[player]", desc = "Heal a player", flags = "s", max = 1)
    public void heal(CommandContext args, Actor sender) throws CommandException, AuthorizationException {

        Iterable<? extends LocalPlayer> targets = null;
        boolean included = false;
        
        // Detect arguments based on the number of arguments provided
        if (args.argsLength() == 0) {
            targets = worldGuard.getPlatform().getMatcher().matchPlayers(worldGuard.checkPlayer(sender));
            
            // Check permissions!
            sender.checkPermission("worldguard.heal");
        } else if (args.argsLength() == 1) {            
            targets = worldGuard.getPlatform().getMatcher().matchPlayers(sender, args.getString(0));
            
            // Check permissions!
            sender.checkPermission("worldguard.heal.other");
        }

        for (LocalPlayer player : targets) {
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20);
            player.setExhaustion(0);
            
            // Tell the user
            if (player.equals(sender)) {
                player.print(TranslatableComponent.of("worldguard.command.general.heal.healed"));
                
                // Keep track of this
                included = true;
            } else if (!args.hasFlag('s')) {
                player.print(TranslatableComponent.of("worldguard.command.general.heal.healed-by-other")
                        .args(TextComponent.of(sender.getDisplayName())));
            }
        }
        
        // The player didn't receive any items, then we need to send the
        // user a message so s/he know that something is indeed working
        if (!included) {
            sender.print(TranslatableComponent.of("worldguard.command.general.heal.healed-other"));
        }
    }
    
    @Command(aliases = {"slay"}, usage = "[player]", desc = "Slay a player", flags = "s", max = 1)
    public void slay(CommandContext args, Actor sender) throws CommandException, AuthorizationException {
        
        Iterable<? extends LocalPlayer> targets = Lists.newArrayList();
        boolean included = false;
        
        // Detect arguments based on the number of arguments provided
        if (args.argsLength() == 0) {
            targets = worldGuard.getPlatform().getMatcher().matchPlayers(worldGuard.checkPlayer(sender));
            
            // Check permissions!
            sender.checkPermission("worldguard.slay");
        } else if (args.argsLength() == 1) {            
            targets = worldGuard.getPlatform().getMatcher().matchPlayers(sender, args.getString(0));
            
            // Check permissions!
            sender.checkPermission("worldguard.slay.other");
        }

        for (LocalPlayer player : targets) {
            player.setHealth(0);
            
            // Tell the user
            if (player.equals(sender)) {
                player.print(TranslatableComponent.of("worldguard.command.general.slay.slain"));
                
                // Keep track of this
                included = true;
            } else if (!args.hasFlag('s')) {
                player.print(TranslatableComponent.of("worldguard.command.general.slay.slain-by-other")
                        .args(TextComponent.of(sender.getDisplayName())));
            }
        }
        
        // The player didn't receive any items, then we need to send the
        // user a message so s/he know that something is indeed working
        if (!included) {
            sender.print(TranslatableComponent.of("worldguard.command.general.slay.slain-other"));
        }
    }
    
    @Command(aliases = {"locate"}, usage = "[player]", desc = "Locate a player", max = 1)
    @CommandPermissions({"worldguard.locate"})
    public void locate(CommandContext args, Actor sender) throws CommandException {
        LocalPlayer player = worldGuard.checkPlayer(sender);
        
        if (args.argsLength() == 0) {
            player.setCompassTarget(new Location(player.getWorld(), player.getWorld().getSpawnPosition().toVector3()));
            
            sender.print(TranslatableComponent.of("worldguard.command.general.locate.reset-to-spawn"));
        } else {
            LocalPlayer target = worldGuard.getPlatform().getMatcher().matchSinglePlayer(sender, args.getString(0));
            player.setCompassTarget(target.getLocation());
            
            sender.print(TranslatableComponent.of("worldguard.command.general.locate.repointed"));
        }
    }
    
    @SuppressWarnings("removal")
    @Command(aliases = {"stack", ";"}, usage = "", desc = "Stack items", max = 0)
    @CommandPermissions({"worldguard.stack"})
    public void stack(CommandContext args, Actor sender) throws CommandException {
        LocalPlayer player = worldGuard.checkPlayer(sender);

        WorldGuard.getInstance().getPlatform().stackPlayerInventory(player);

        player.print(TranslatableComponent.of("worldguard.command.general.stack.compacted"));
    }
}
