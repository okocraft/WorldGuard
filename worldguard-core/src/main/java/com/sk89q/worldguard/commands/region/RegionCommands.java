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

package com.sk89q.worldguard.commands.region;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.AsyncCommandBuilder;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.util.formatting.text.format.TextDecoration;
import com.sk89q.worldedit.util.formatting.text.serializer.legacy.LegacyComponentSerializer;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.gamemode.GameModes;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.commands.task.RegionAdder;
import com.sk89q.worldguard.commands.task.RegionLister;
import com.sk89q.worldguard.commands.task.RegionManagerLoader;
import com.sk89q.worldguard.commands.task.RegionManagerSaver;
import com.sk89q.worldguard.commands.task.RegionRemover;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.internal.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.FlagValueCalculator;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormatException;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.RegionGroupFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import com.sk89q.worldguard.protection.managers.migration.DriverMigration;
import com.sk89q.worldguard.protection.managers.migration.MigrationException;
import com.sk89q.worldguard.protection.managers.migration.UUIDMigration;
import com.sk89q.worldguard.protection.managers.migration.WorldHeightMigration;
import com.sk89q.worldguard.protection.managers.storage.DriverType;
import com.sk89q.worldguard.protection.managers.storage.RegionDriver;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion.CircularInheritanceException;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.util.WorldEditRegionConverter;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.util.Enums;
import com.sk89q.worldguard.util.logging.LoggerToChatHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.enginehub.piston.exception.CommandException;

/**
 * Implements the /region commands for WorldGuard.
 */
public final class RegionCommands extends RegionCommandsBase {

    private static final Logger log = Logger.getLogger(RegionCommands.class.getCanonicalName());
    private final WorldGuard worldGuard;

    public RegionCommands(WorldGuard worldGuard) {
        checkNotNull(worldGuard);
        this.worldGuard = worldGuard;
    }

    private static Component passthroughFlagWarning = TextComponent.empty()
            .append(TranslatableComponent.of("worldguard.command.region.flag.passthrough-flag-warning.prefix", TextColor.RED, Sets.newHashSet(TextDecoration.BOLD)))
            .append(TextComponent.space())
            .append(TranslatableComponent.of("worldguard.command.region.flag.passthrough-flag-warning.message", TextColor.RED))
            .append(TextComponent.newline())
            .append(TranslatableComponent.of("worldguard.command.region.flag.passthrough-flag-warning.info")
                    .args(TextComponent.of("[this documentation page]", TextColor.AQUA)
                            .clickEvent(ClickEvent.of(ClickEvent.Action.OPEN_URL,
                                    "https://worldguard.enginehub.org/en/latest/regions/flags/#overrides"))));

    private static Component buildFlagWarning = TextComponent.empty()
            .append(TranslatableComponent.of("worldguard.command.region.flag.build-flag-warning.prefix", TextColor.RED, Sets.newHashSet(TextDecoration.BOLD)))
            .append(TextComponent.space())
            .append(TranslatableComponent.of("worldguard.command.region.flag.build-flag-warning.message", TextColor.RED))
            .append(TextComponent.newline())
            .append(TranslatableComponent.of("worldguard.command.region.flag.build-flag-warning.info-line1"))
            .append(TextComponent.newline())
            .append(TranslatableComponent.of("worldguard.command.region.flag.build-flag-warning.info-line2")
                    .args(TextComponent.of("[this documentation page]", TextColor.AQUA)
                            .clickEvent(ClickEvent.of(ClickEvent.Action.OPEN_URL,
                                    "https://worldguard.enginehub.org/en/latest/regions/flags/#protection-related"))));

    /**
     * Defines a new region.
     * 
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"define", "def", "d", "create"},
             usage = "[-w <world>] <id> [<owner1> [<owner2> [<owners...>]]]",
             flags = "ngw:",
             desc = "Defines a region",
             min = 1)
    public void define(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        // Check permissions
        if (!getPermissionModel(sender).mayDefine()) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        String id = checkRegionId(args.getString(0), false);

        World world = checkWorld(args, sender, 'w');
        RegionManager manager = checkRegionManager(world);

        checkRegionDoesNotExist(manager, id, true);

        ProtectedRegion region;

        if (args.hasFlag('g')) {
            region = new GlobalProtectedRegion(id);
        } else {
            region = checkRegionFromSelection(sender, id);
        }

        RegionAdder task = new RegionAdder(manager, region, sender);
        task.addOwnersFromCommand(args, 2);

        final String description = String.format("Adding region '%s'", region.getId());
        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .onSuccess((Component) null,
                        t -> {
                            sender.print(TranslatableComponent.of("worldguard.command.region.define.new-region").args(TextComponent.of(region.getId())));
                            warnAboutDimensions(sender, region);
                            informNewUser(sender, manager, region);
                            checkSpawnOverlap(sender, world, region);
                        })
                .onFailure(
                        TranslatableComponent.of("worldguard.error.command.region.define.failed")
                                .args(TextComponent.of(region.getId())),
                        worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    /**
     * Re-defines a region with a new selection.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"redefine", "update", "move"},
             usage = "[-w <world>] <id>",
             desc = "Re-defines the shape of a region",
             flags = "gw:",
             min = 1, max = 1)
    public void redefine(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        String id = checkRegionId(args.getString(0), false);

        World world = checkWorld(args, sender, 'w');
        RegionManager manager = checkRegionManager(world);

        ProtectedRegion existing = checkExistingRegion(manager, id, false);

        // Check permissions
        if (!getPermissionModel(sender).mayRedefine(existing)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        ProtectedRegion region;

        if (args.hasFlag('g')) {
            region = new GlobalProtectedRegion(id);
        } else {
            region = checkRegionFromSelection(sender, id);
        }

        region.copyFrom(existing);
        sender.print(TranslatableComponent.of(""));
        RegionAdder task = new RegionAdder(manager, region, sender);
        final String description = LegacyComponentSerializer.legacy().serialize(
                WorldEditText.format(TranslatableComponent.of("worldguard.command.region.redefine.updating-region")
                        .args(TextComponent.of(region.getId())), sender.getLocale())
        );
        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .setDelayMessage(TranslatableComponent.of("worldguard.command.region.redefine.delay-message").args(TextComponent.of(description)))
                .onSuccess((Component) null,
                        t -> {
                            sender.print(TranslatableComponent.of("worldguard.command.region.redefine.updated").args(TextComponent.of(region.getId())));
                            warnAboutDimensions(sender, region);
                            informNewUser(sender, manager, region);
                            checkSpawnOverlap(sender, world, region);
                        })
                .onFailure(TranslatableComponent.of("worldguard.error.command.region.redefine.failed").args(TextComponent.of(region.getId())), worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    /**
     * Claiming command for users.
     *
     * <p>This command is a joke and it needs to be rewritten. It was contributed
     * code :(</p>
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"claim"},
             usage = "<id>",
             desc = "Claim a region",
             min = 1, max = 1)
    public void claim(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        LocalPlayer player = worldGuard.checkPlayer(sender);
        RegionPermissionModel permModel = getPermissionModel(player);

        // Check permissions
        if (!permModel.mayClaim()) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        String id = checkRegionId(args.getString(0), false);

        RegionManager manager = checkRegionManager(player.getWorld());

        checkRegionDoesNotExist(manager, id, false);
        ProtectedRegion region = checkRegionFromSelection(player, id);

        WorldConfiguration wcfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(player.getWorld());

        // Check whether the player has created too many regions
        if (!permModel.mayClaimRegionsUnbounded()) {
            int maxRegionCount = wcfg.getMaxRegionCount(player);
            if (maxRegionCount >= 0
                    && manager.getRegionCountOfPlayer(player) >= maxRegionCount) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.region.claim.too-many-regions"),
                        ImmutableList.of()
                );
            }
        }

        ProtectedRegion existing = manager.getRegion(id);

        // Check for an existing region
        if (existing != null) {
            if (!existing.getOwners().contains(player)) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.region.claim.region-already-exists"),
                        ImmutableList.of()
                );
            }
        }

        // We have to check whether this region violates the space of any other region
        ApplicableRegionSet regions = manager.getApplicableRegions(region);

        // Check if this region overlaps any other region
        if (regions.size() > 0) {
            if (!regions.isOwnerOfAll(player)) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.region.claim.overlaps"),
                        ImmutableList.of()
                );
            }
        } else {
            if (wcfg.claimOnlyInsideExistingRegions) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.region.claim.only-inside"),
                        ImmutableList.of()
                );
            }
        }

        if (wcfg.maxClaimVolume >= Integer.MAX_VALUE) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.region.claim.config-maximum-volume-invalid")
                            .args(TextComponent.of(Integer.MAX_VALUE)),
                    ImmutableList.of()
            );
        }

        // Check claim volume
        if (!permModel.mayClaimRegionsUnbounded()) {
            if (region.volume() > wcfg.maxClaimVolume) {
                player.printError(TranslatableComponent.of("worldguard.error.command.region.claim.too-large"));
                player.printError(TranslatableComponent.of("worldguard.error.command.region.claim.too-large.min-max")
                        .args(TextComponent.of(wcfg.maxClaimVolume), TextComponent.of(region.volume())));
                return;
            }
        }

        // Inherit from a template region
        if (!Strings.isNullOrEmpty(wcfg.setParentOnClaim)) {
            ProtectedRegion templateRegion = manager.getRegion(wcfg.setParentOnClaim);
            if (templateRegion != null) {
                try {
                    region.setParent(templateRegion);
                } catch (CircularInheritanceException e) {
                    throw new CommandException(TextComponent.empty(), ImmutableList.of());
                }
            }
        }

        region.getOwners().addPlayer(player);
        manager.addRegion(region);
        player.print(TranslatableComponent.of("worldguard.command.region.claim.claimed").args(TextComponent.of(id)));
    }

    /**
     * Get a WorldEdit selection from a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"select", "sel", "s"},
             usage = "[-w <world>] [id]",
             desc = "Load a region as a WorldEdit selection",
             min = 0, max = 1,
             flags = "w:")
    public void select(CommandContext args, Actor sender) throws CommandException {
        World world = checkWorld(args, sender, 'w');
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing;

        // If no arguments were given, get the region that the player is inside
        if (args.argsLength() == 0) {
            LocalPlayer player = worldGuard.checkPlayer(sender);
            if (!player.getWorld().equals(world)) { // confusing to get current location regions in another world
                // just don't allow that
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.region.select.specify-name"),
                        ImmutableList.of()
                );
            }
            world = player.getWorld();
            existing = checkRegionStandingIn(manager, player, "/rg select -w \"" + world.getName() + "\" %id%");
        } else {
            existing = checkExistingRegion(manager, args.getString(0), false);
        }

        // Check permissions
        if (!getPermissionModel(sender).maySelect(existing)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        // Select
        setPlayerSelection(sender, existing, world);
    }

    /**
     * Get information about a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"info", "i"},
             usage = "[id]",
             flags = "usw:",
             desc = "Get information about a region",
             min = 0, max = 1)
    public void info(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        RegionPermissionModel permModel = getPermissionModel(sender);

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing;

        if (args.argsLength() == 0) { // Get region from where the player is
            if (!(sender instanceof LocalPlayer)) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.region.info.specify-region"),
                        ImmutableList.of()
                );
            }

            existing = checkRegionStandingIn(manager, (LocalPlayer) sender, true,
                    "/rg info -w \"" + world.getName() + "\" %id%" + (args.hasFlag('u') ? " -u" : "") + (args.hasFlag('s') ? " -s" : ""));
        } else { // Get region from the ID
            existing = checkExistingRegion(manager, args.getString(0), true);
        }

        // Check permissions
        if (!permModel.mayLookup(existing)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        // Let the player select the region
        if (args.hasFlag('s')) {
            // Check permissions
            if (!permModel.maySelect(existing)) {
                // TODO: if we use piston correctly, we can remove this.
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.no-permission"),
                        ImmutableList.of()
                );
            }

            setPlayerSelection(worldGuard.checkPlayer(sender), existing, world);
        }

        // Print region information
        RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), existing,
                args.hasFlag('u') ? null : WorldGuard.getInstance().getProfileCache(), sender);

        final String description = LegacyComponentSerializer.legacy().serialize(WorldEditText.format(
                TranslatableComponent.of("worldguard.command.region.info.fetching-region-info"),
                sender.getLocale()
        ));
        AsyncCommandBuilder.wrap(printout, sender)
                .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), description)
                .setDelayMessage(TranslatableComponent.of("worldguard.command.region.info.delay-message"))
                .onSuccess((Component) null, component -> {
                    sender.print(component);
                    checkSpawnOverlap(sender, world, existing);
                })
                .onFailure(TranslatableComponent.of("worldguard.error.command.region.info.failure"), WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * List regions.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"list"},
             usage = "[-w world] [-p owner [-n]] [-s] [-i filter] [page]",
             desc = "Get a list of regions",
             flags = "np:w:i:s",
             max = 1)
    public void list(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String ownedBy;

        // Get page
        int page = args.getInteger(0, 1);
        if (page < 1) {
            page = 1;
        }

        // -p flag to lookup a player's regions
        if (args.hasFlag('p')) {
            ownedBy = args.getFlag('p');
        } else {
            ownedBy = null; // List all regions
        }

        // Check permissions
        if (!getPermissionModel(sender).mayList(ownedBy)) {
            ownedBy = sender.getName(); // assume they only want their own
            if (!getPermissionModel(sender).mayList(ownedBy)) {
                // TODO: if we use piston correctly, we can remove this.
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.no-permission"),
                        ImmutableList.of()
                );
            }
        }

        RegionManager manager = checkRegionManager(world);

        RegionLister task = new RegionLister(manager, sender, world.getName());
        task.setPage(page);
        if (ownedBy != null) {
            task.filterOwnedByName(ownedBy, args.hasFlag('n'));
        }

        if (args.hasFlag('s')) {
            ProtectedRegion existing = checkRegionFromSelection(sender, "tmp");
            task.filterByIntersecting(existing);
        }

        // -i string is in region id
        if (args.hasFlag('i')) {
            task.filterIdByMatch(args.getFlag('i'));
        }
        final String description = LegacyComponentSerializer.legacy().serialize(WorldEditText.format(
                TranslatableComponent.of("worldguard.command.region.list.getting-region-list"),
                sender.getLocale()
        ));
        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), description)
                .setDelayMessage(TranslatableComponent.of("worldguard.command.region.list.delay-message"))
                .onFailure(
                        TranslatableComponent.of("worldguard.error.command.region.list.failure"),
                        WorldGuard.getInstance().getExceptionConverter()
                )
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * Set a flag.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"flag", "f"},
             usage = "<id> <flag> [-w world] [-g group] [value]",
             flags = "g:w:eh:",
             desc = "Set flags",
             min = 2)
    public void flag(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String flagName = args.getString(1);
        String value = args.argsLength() >= 3 ? args.getJoinedStrings(2) : null;
        RegionGroup groupValue = null;
        FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();
        RegionPermissionModel permModel = getPermissionModel(sender);

        if (args.hasFlag('e')) {
            if (value != null) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.region.flag.cannot-use-empty"),
                        ImmutableList.of()
                );
            }

            value = "";
        }

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(manager, args.getString(0), true);

        // Check permissions
        if (!permModel.maySetFlag(existing)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }
        String regionId = existing.getId();

        Flag<?> foundFlag = Flags.fuzzyMatchFlag(flagRegistry, flagName);

        // We didn't find the flag, so let's print a list of flags that the user
        // can use, and do nothing afterwards
        if (foundFlag == null) {
            final String description = LegacyComponentSerializer.legacy().serialize(WorldEditText.format(
                    TranslatableComponent.of("worldguard.command.region.flag.flag-list-for-invalid"),
                    sender.getLocale()
            ));
            AsyncCommandBuilder.wrap(new FlagListBuilder(flagRegistry, permModel, existing, world,
                                                         regionId, sender, flagName), sender)
                    .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), description)
                    .onSuccess((Component) null, sender::print)
                    .onFailure((Component) null, WorldGuard.getInstance().getExceptionConverter())
                    .buildAndExec(WorldGuard.getInstance().getExecutorService());
            return;
        } else if (value != null) {
            if (foundFlag == Flags.BUILD || foundFlag == Flags.BLOCK_BREAK || foundFlag == Flags.BLOCK_PLACE) {
                sender.print(buildFlagWarning);
                if (!sender.isPlayer()) {
                    sender.print(TextComponent.of("https://worldguard.enginehub.org/en/latest/regions/flags/#protection-related"));
                }
            } else if (foundFlag == Flags.PASSTHROUGH) {
                sender.print(passthroughFlagWarning);
                if (!sender.isPlayer()) {
                    sender.print(TextComponent.of("https://worldguard.enginehub.org/en/latest/regions/flags/#overrides"));
                }
            }
        }

        // Also make sure that we can use this flag
        // This permission is confusing and probably should be replaced, but
        // but not here -- in the model
        if (!permModel.maySetFlag(existing, foundFlag, value)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        // -g for group flag
        if (args.hasFlag('g')) {
            String group = args.getFlag('g');
            RegionGroupFlag groupFlag = foundFlag.getRegionGroupFlag();

            if (groupFlag == null) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.command.region.flag.flag-does-not-have-group")
                                .args(TextComponent.of(foundFlag.getName())),
                        ImmutableList.of()
                );
            }

            // Parse the [-g group] separately so entire command can abort if parsing
            // the [value] part throws an error.
            try {
                groupValue = groupFlag.parseInput(FlagContext.create().setSender(sender).setInput(group).setObject("region", existing).build());
            } catch (InvalidFlagFormatException e) {
                throw new CommandException(e.getRichMessage(), ImmutableList.of());
            }

        }

        // Set the flag value if a value was set
        if (value != null) {
            // Set the flag if [value] was given even if [-g group] was given as well
            try {
                value = setFlag(existing, foundFlag, sender, value).toString();
            } catch (InvalidFlagFormatException e) {
                throw new CommandException(e.getRichMessage(), ImmutableList.of());
            }

            if (!args.hasFlag('h')) {
                sender.print(TranslatableComponent.of("worldguard.command.region.flag.flag-is-set")
                        .args(
                                TextComponent.of(foundFlag.getName()),
                                TextComponent.of(regionId),
                                TextComponent.of(value)
                        )
                );
            }

        // No value? Clear the flag, if -g isn't specified
        } else if (!args.hasFlag('g')) {
            // Clear the flag only if neither [value] nor [-g group] was given
            existing.setFlag(foundFlag, null);

            // Also clear the associated group flag if one exists
            RegionGroupFlag groupFlag = foundFlag.getRegionGroupFlag();
            if (groupFlag != null) {
                existing.setFlag(groupFlag, null);
            }

            if (!args.hasFlag('h')) {

                sender.print(TranslatableComponent.of("worldguard.command.region.flag.flag-is-removed")
                        .args(TextComponent.of(foundFlag.getName()), TextComponent.of(regionId)));
            }
        }

        // Now set the group
        if (groupValue != null) {
            RegionGroupFlag groupFlag = foundFlag.getRegionGroupFlag();

            // If group set to the default, then clear the group flag
            if (groupValue == groupFlag.getDefault()) {
                existing.setFlag(groupFlag, null);

                sender.print(TranslatableComponent.of("worldguard.command.region.flag.flag-group-is-reset")
                        .args(TextComponent.of(foundFlag.getName())));
            } else {
                existing.setFlag(groupFlag, groupValue);
                sender.print(TranslatableComponent.of("worldguard.command.region.flag.flag-group-is-set")
                        .args(TextComponent.of(foundFlag.getName())));
            }
        }

        // Print region information
        if (args.hasFlag('h')) {
            int page = args.getFlagInteger('h');
            sendFlagHelper(sender, world, existing, permModel, page);
        } else {
            RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), existing, null, sender);
            printout.append(TextComponent.builder().append(TranslatableComponent.of("worldguard.command.region.flag.current-flags").args(printout
                    .getFlagsList(false))).build());
            printout.send(sender);
            checkSpawnOverlap(sender, world, existing);
        }
    }

    @Command(aliases = "flags",
             usage = "[-p <page>] [id]",
             flags = "p:w:",
             desc = "View region flags",
             min = 0, max = 2)
    public void flagHelper(CommandContext args, Actor sender) throws CommandException {
        World world = checkWorld(args, sender, 'w'); // Get the world

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region;
        if (args.argsLength() == 0) { // Get region from where the player is
            if (!(sender instanceof LocalPlayer)) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.command.region.flags.specify-region"),
                        ImmutableList.of()
                );
            }

            region = checkRegionStandingIn(manager, (LocalPlayer) sender, true,
                    "/rg flags -w \"" + world.getName() + "\" %id%");
        } else { // Get region from the ID
            region = checkExistingRegion(manager, args.getString(0), true);
        }

        final RegionPermissionModel perms = getPermissionModel(sender);
        if (!perms.mayLookup(region)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }
        int page = args.hasFlag('p') ? args.getFlagInteger('p') : 1;

        sendFlagHelper(sender, world, region, perms, page);
    }

    private static void sendFlagHelper(Actor sender, World world, ProtectedRegion region, RegionPermissionModel perms, int page) {
        // TODO: to localize here, we have to modify worldedit...
        final FlagHelperBox flagHelperBox = new FlagHelperBox(world, region, perms);
        flagHelperBox.setComponentsPerPage(18);
        if (!sender.isPlayer()) {
            flagHelperBox.tryMonoSpacing();
        }
        AsyncCommandBuilder.wrap(() -> {
                    if (checkSpawnOverlap(sender, world, region)) {
                        flagHelperBox.setComponentsPerPage(15);
                    }
                    return flagHelperBox.create(page);
                }, sender)
                .onSuccess((Component) null, sender::print)
                .onFailure("Failed to get region flags", WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * Set the priority of a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"setpriority", "priority", "pri"},
             usage = "<id> <priority>",
             flags = "w:",
             desc = "Set the priority of a region",
             min = 2, max = 2)
    public void setPriority(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        int priority = args.getInteger(1);

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(manager, args.getString(0), false);

        // Check permissions
        if (!getPermissionModel(sender).maySetPriority(existing)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        existing.setPriority(priority);

        sender.print(TranslatableComponent.of("worldguard.command.region.setpriority.priority-is-set")
                .args(TextComponent.of(existing.getId()), TextComponent.of(priority)));
        checkSpawnOverlap(sender, world, existing);
    }

    /**
     * Set the parent of a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"setparent", "parent", "par"},
             usage = "<id> [parent-id]",
             flags = "w:",
             desc = "Set the parent of a region",
             min = 1, max = 2)
    public void setParent(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        ProtectedRegion parent;
        ProtectedRegion child;

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);

        // Get parent and child
        child = checkExistingRegion(manager, args.getString(0), false);
        if (args.argsLength() == 2) {
            parent = checkExistingRegion(manager, args.getString(1), false);
        } else {
            parent = null;
        }

        // Check permissions
        if (!getPermissionModel(sender).maySetParent(child, parent)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        try {
            child.setParent(parent);
        } catch (CircularInheritanceException e) {
            // Tell the user what's wrong
            RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), parent, null, sender);
            assert parent != null;
            printout.append(
                    TranslatableComponent.of(
                            "worldguard.command.region.setparent.circular-inheritance",
                            TextColor.RED)
            ).newline();

            printout.append(
                    TranslatableComponent.of(
                            "worldguard.command.region.setparent.current-inheritance",
                            TextColor.GRAY
                    ).args(
                            TextComponent.of(parent.getId()),
                            TextComponent.newline()
                                    .append(printout.getParentTree(true))
                                    .append(TextComponent.of(")", TextColor.GRAY))
                    )
            );
            printout.send(sender);
            return;
        }

        // Tell the user the current inheritance
        RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), child, null, sender);
        printout.append(TranslatableComponent.of(
                "worldguard.command.region.setparent.inheritance-is-set",
                TextColor.LIGHT_PURPLE
        ).args(TextComponent.of(child.getId())));
        if (parent != null) {
            printout.newline();
            printout.append(
                    TranslatableComponent.of(
                            "worldguard.command.region.setparent.current-inheritance",
                            TextColor.GRAY
                    ).args(
                            TextComponent.of(parent.getId()),
                            TextComponent.of("\n").append(printout.getParentTree(true))
                    )
            );
        } else {
            printout.append(TranslatableComponent.of(
                    "worldguard.command.region.setparent.region-is-now-orphaned",
                    TextColor.YELLOW)
            );
        }
        printout.send(sender);
    }

    /**
     * Remove a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"remove", "delete", "del", "rem"},
             usage = "<id>",
             flags = "fuw:",
             desc = "Remove a region",
             min = 1, max = 1)
    public void remove(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        boolean removeChildren = args.hasFlag('f');
        boolean unsetParent = args.hasFlag('u');

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(manager, args.getString(0), true);

        // Check permissions
        if (!getPermissionModel(sender).mayDelete(existing)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        RegionRemover task = new RegionRemover(manager, existing);

        if (removeChildren && unsetParent) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.remove.cannot-use-u-and-f-together"),
                    ImmutableList.of()
            );
        } else if (removeChildren) {
            task.setRemovalStrategy(RemovalStrategy.REMOVE_CHILDREN);
        } else if (unsetParent) {
            task.setRemovalStrategy(RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
        }

        final String description = LegacyComponentSerializer.legacy().serialize(WorldEditText.format(
                TranslatableComponent.of("worldguard.command.region.remove.removing-region")
                        .args(TextComponent.of(existing.getId()), TextComponent.of(world.getName())),
                sender.getLocale()
        ));

        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), description)
                .setDelayMessage(TranslatableComponent.of("worldguard.command.region.remove.delay-message"))
                .onSuccess((Component) null, removed -> sender.print(TranslatableComponent.of(
                        "worldguard.command.region.remove.success-remove",
                        TextColor.LIGHT_PURPLE).args(TextComponent.of(removed.stream().map(ProtectedRegion::getId).collect(Collectors.joining(", "))))))
                .onFailure(
                        TranslatableComponent.of("worldguard.command.region.remove.failed-removing-region"),
                        WorldGuard.getInstance().getExceptionConverter()
                ).buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * Reload the region database.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"load", "reload"},
            usage = "[world]",
            desc = "Reload regions from file",
            flags = "w:")
    public void load(CommandContext args, final Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = null;
        try {
            world = checkWorld(args, sender, 'w'); // Get the world
        } catch (CommandException ignored) {
            // assume the user wants to reload all worlds
        }

        // Check permissions
        if (!getPermissionModel(sender).mayForceLoadRegions()) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        if (world != null) {
            RegionManager manager = checkRegionManager(world);

            TranslatableComponent descriptionComponent = TranslatableComponent.of("worldguard.command.region.load.loading-region-data")
                    .args(TextComponent.of(world.getName()));
            final String description = LegacyComponentSerializer.legacy().serialize(WorldEditText.format(descriptionComponent, sender.getLocale()));
            AsyncCommandBuilder.wrap(new RegionManagerLoader(manager), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), description)
                    .setDelayMessage(TranslatableComponent.of("worldguard.command.region.load.wait-for-loading")
                            .args(descriptionComponent))
                    .onSuccess(TranslatableComponent.of("worldguard.command.region.load.loaded-region-data")
                            .args(TextComponent.of(world.getName())), null)
                    .onFailure(TranslatableComponent.of("worldguard.command.region.load.failed-to-load-region-data")
                            .args(TextComponent.of(world.getName())), worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        } else {
            // Load regions for all worlds
            List<RegionManager> managers = new ArrayList<>();

            for (World w : WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds()) {
                RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(w);
                if (manager != null) {
                    managers.add(manager);
                }
            }

            AsyncCommandBuilder.wrap(new RegionManagerLoader(managers), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), LegacyComponentSerializer.legacy().serialize(
                            WorldEditText.format(TranslatableComponent.of(
                                            "worldguard.command.region.load.loading-all-world-region-data"),
                                    sender.getLocale())))
                    .setDelayMessage(
                            TranslatableComponent.of("worldguard.command.region.load.loading-all-world-delay-message"))
                    .onSuccess(TranslatableComponent.of("worldguard.command.region.load.loaded-all-world-regions"),
                            null)
                    .onFailure(
                            TranslatableComponent.of("worldguard.command.region.load.failed-to-load-all-world-regions"),
                            worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        }
    }

    /**
     * Re-save the region database.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"save", "write"},
            usage = "[world]",
            desc = "Re-save regions to file",
            flags = "w:")
    public void save(CommandContext args, final Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = null;
        try {
            world = checkWorld(args, sender, 'w'); // Get the world
        } catch (CommandException ignored) {
            // assume user wants to save all worlds
        }

        // Check permissions
        if (!getPermissionModel(sender).mayForceSaveRegions()) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        if (world != null) {
            RegionManager manager = checkRegionManager(world);

            TranslatableComponent descriptionComponent = TranslatableComponent.of("worldguard.command.region.save.saving-region-data")
                    .args(TextComponent.of(world.getName()));
            final String description = LegacyComponentSerializer.legacy().serialize(WorldEditText.format(descriptionComponent, sender.getLocale()));
            AsyncCommandBuilder.wrap(new RegionManagerSaver(manager), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), description)
                    .setDelayMessage(TranslatableComponent.of("worldguard.command.region.save.wait-for-saving")
                            .args(descriptionComponent))
                    .onSuccess(TranslatableComponent.of("worldguard.command.region.save.saved-region-data")
                            .args(TextComponent.of(world.getName())), null)
                    .onFailure(TranslatableComponent.of("worldguard.command.region.save.failed-to-save-region-data")
                            .args(TextComponent.of(world.getName())), worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        } else {
            // Save for all worlds
            List<RegionManager> managers = new ArrayList<>();

            final RegionContainer regionContainer = worldGuard.getPlatform().getRegionContainer();
            for (World w : WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds()) {
                RegionManager manager = regionContainer.get(w);
                if (manager != null) {
                    managers.add(manager);
                }
            }

            AsyncCommandBuilder.wrap(new RegionManagerSaver(managers), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), LegacyComponentSerializer.legacy().serialize(
                            WorldEditText.format(TranslatableComponent.of(
                                            "worldguard.command.region.save.saving-all-world-region-data"),
                                    sender.getLocale())))
                    .setDelayMessage(
                            TranslatableComponent.of("worldguard.command.region.save.saving-all-world-delay-message"))
                    .onSuccess(TranslatableComponent.of("worldguard.command.region.save.saved-all-world-regions"),
                            null)
                    .onFailure(
                            TranslatableComponent.of("worldguard.command.region.save.failed-to-save-all-world-regions"),
                            worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        }
    }

    /**
     * Migrate the region database.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"migratedb"}, usage = "<from> <to>",
             flags = "y",
             desc = "Migrate from one Protection Database to another.", min = 2, max = 2)
    public void migrateDB(CommandContext args, Actor sender) throws CommandException {
        // Check permissions
        if (!getPermissionModel(sender).mayMigrateRegionStore()) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        DriverType from = Enums.findFuzzyByValue(DriverType.class, args.getString(0));
        DriverType to = Enums.findFuzzyByValue(DriverType.class, args.getString(1));

        if (from == null) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migratedb.unrecognized-type-of-from-db"),
                    ImmutableList.of()
            );
        }

        if (to == null) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migratedb.unrecognized-type-of-to-db"),
                    ImmutableList.of()
            );
        }

        if (from == to) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migratedb.same-type-of-db"),
                    ImmutableList.of()
            );
        }

        if (!args.hasFlag('y')) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migratedb.ensure-backup-line1")
                            .append(TextComponent.newline())
                            .append(TranslatableComponent.of("worldguard.command.region.migratedb.ensure-backup-line2")),
                    ImmutableList.of()
            );
        }

        ConfigurationManager config = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        RegionDriver fromDriver = config.regionStoreDriverMap.get(from);
        RegionDriver toDriver = config.regionStoreDriverMap.get(to);

        if (fromDriver == null) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migratedb.from-db-driver-not-supported"),
                    ImmutableList.of()
            );
        }

        if (toDriver == null) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migratedb.to-db-driver-not-supported"),
                    ImmutableList.of()
            );
        }

        DriverMigration migration = new DriverMigration(fromDriver, toDriver, WorldGuard.getInstance().getFlagRegistry());

        LoggerToChatHandler handler = null;
        Logger minecraftLogger = null;

        if (sender instanceof LocalPlayer) {
            handler = new LoggerToChatHandler(sender);
            handler.setLevel(Level.ALL);
            minecraftLogger = Logger.getLogger("com.sk89q.worldguard");
            minecraftLogger.addHandler(handler);
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            sender.print(TranslatableComponent.of("worldguard.command.region.migratedb.performing"));
            container.migrate(migration);
            sender.print(TranslatableComponent.of("worldguard.command.region.migratedb.complete"));
        } catch (MigrationException e) {
            log.log(Level.WARNING, "Failed to migrate", e);
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migratedb.error")
                            .args(TextComponent.of(e.getMessage())),
                    ImmutableList.of()
            );
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }

    /**
     * Migrate the region databases to use UUIDs rather than name.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"migrateuuid"},
            desc = "Migrate loaded databases to use UUIDs", max = 0)
    public void migrateUuid(CommandContext args, Actor sender) throws CommandException {
        // Check permissions
        if (!getPermissionModel(sender).mayMigrateRegionNames()) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        LoggerToChatHandler handler = null;
        Logger minecraftLogger = null;

        if (sender instanceof LocalPlayer) {
            handler = new LoggerToChatHandler(sender);
            handler.setLevel(Level.ALL);
            minecraftLogger = Logger.getLogger("com.sk89q.worldguard");
            minecraftLogger.addHandler(handler);
        }

        try {
            ConfigurationManager config = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionDriver driver = container.getDriver();
            UUIDMigration migration = new UUIDMigration(driver, WorldGuard.getInstance().getProfileService(), WorldGuard.getInstance().getFlagRegistry());
            migration.setKeepUnresolvedNames(config.keepUnresolvedNames);
            sender.print(TranslatableComponent.of("worldguard.command.region.migrateuuid.performing"));
            container.migrate(migration);
            sender.print(TranslatableComponent.of("worldguard.command.region.migrateuuid.complete"));
        } catch (MigrationException e) {
            log.log(Level.WARNING, "Failed to migrate", e);
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migrateuuid.error")
                            .args(TextComponent.of(e.getMessage())),
                    ImmutableList.of()
            );
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }


    /**
     * Migrate regions that went from 0-255 to new world heights.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"migrateheights"},
            usage = "[world]", max = 1,
            flags = "yw:",
            desc = "Migrate regions from old height limits to new height limits")
    public void migrateHeights(CommandContext args, Actor sender) throws CommandException {
        // Check permissions
        if (!getPermissionModel(sender).mayMigrateRegionHeights()) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        if (!args.hasFlag('y')) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migrateheights.ensure-backup-line1")
                            .append(TextComponent.newline())
                            .append(TranslatableComponent.of("worldguard.command.region.migrateheights.ensure-backup-line2")),
                    ImmutableList.of()
            );
        }

        World world = null;
        try {
            world = checkWorld(args, sender, 'w');
        } catch (CommandException ignored) {
        }

        LoggerToChatHandler handler = null;
        Logger minecraftLogger = null;

        if (sender instanceof LocalPlayer) {
            handler = new LoggerToChatHandler(sender);
            handler.setLevel(Level.ALL);
            minecraftLogger = Logger.getLogger("com.sk89q.worldguard");
            minecraftLogger.addHandler(handler);
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionDriver driver = container.getDriver();
            WorldHeightMigration migration = new WorldHeightMigration(driver, WorldGuard.getInstance().getFlagRegistry(), world);
            container.migrate(migration);
            sender.print(TranslatableComponent.of("worldguard.command.region.migrateheights.complete"));
        } catch (MigrationException e) {
            log.log(Level.WARNING, "Failed to migrate", e);
            throw new CommandException(
                    TranslatableComponent.of("worldguard.command.region.migrateheights.error")
                            .args(TextComponent.of(e.getMessage())),
                    ImmutableList.of()
            );
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }


    /**
     * Teleport to a region
     * 
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"teleport", "tp"},
             usage = "[-w world] [-c|s] <id>",
             flags = "csw:",
             desc = "Teleports you to the location associated with the region.",
             min = 1, max = 1)
    public void teleport(CommandContext args, Actor sender) throws CommandException {
        LocalPlayer player = worldGuard.checkPlayer(sender);
        Location teleportLocation;

        // Lookup the existing region
        World world = checkWorld(args, player, 'w');
        RegionManager regionManager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(regionManager, args.getString(0), true);

        // Check permissions
        if (!getPermissionModel(player).mayTeleportTo(existing)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        // -s for spawn location
        if (args.hasFlag('s')) {
            teleportLocation = FlagValueCalculator.getEffectiveFlagOf(existing, Flags.SPAWN_LOC, player);
            
            if (teleportLocation == null) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.command.region.teleport.no-spawn-point"),
                        ImmutableList.of()
                );
            }
        } else if (args.hasFlag('c')) {
            // Check permissions
            if (!getPermissionModel(player).mayTeleportToCenter(existing)) {
                // TODO: if we use piston correctly, we can remove this.
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.no-permission"),
                        ImmutableList.of()
                );
            }
            Region region = WorldEditRegionConverter.convertToRegion(existing);
            if (region == null || region.getCenter() == null) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.command.region.teleport.no-center-point"),
                        ImmutableList.of()
                );
            }
            if (player.getGameMode() == GameModes.SPECTATOR) {
                teleportLocation = new Location(world, region.getCenter(), 0, 0);
            } else {
                // TODO: Add some method to create a safe teleport location.
                // The method AbstractPlayerActor$findFreePoisition(Location loc) is no good way for this.
                // It doesn't return the found location and it can't be checked if the location is inside the region.
                throw new CommandException(
                        TranslatableComponent.of("worldguard.command.region.teleport.center-spectator-warning"),
                        ImmutableList.of()
                );
            }
        } else {
            teleportLocation = FlagValueCalculator.getEffectiveFlagOf(existing, Flags.TELE_LOC, player);
            
            if (teleportLocation == null) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.command.region.teleport.no-teleport-point"),
                        ImmutableList.of()
                );
            }
        }

        String message = FlagValueCalculator.getEffectiveFlagOf(existing, Flags.TELE_MESSAGE, player);

        // If the flag isn't set, use the default message
        // If message.isEmpty(), no message is sent by LocalPlayer#teleport(...)
        if (message == null) {
            message = Flags.TELE_MESSAGE.getDefault();
        }

        TranslatableComponent failMessage = TranslatableComponent.of(
                "worldguard.command.region.teleport.unable-to-teleport"
        ).args(TextComponent.of(existing.getId()));

        player.teleport(teleportLocation,
                message.replace("%id%", existing.getId()),
                LegacyComponentSerializer.legacy().serialize(WorldEditText.format(failMessage, sender.getLocale())));
    }

    @Command(aliases = {"toggle-bypass", "bypass"},
             usage = "[on|off]",
             desc = "Toggle region bypassing, effectively ignoring bypass permissions.")
    public void toggleBypass(CommandContext args, Actor sender) throws CommandException {
        LocalPlayer player = worldGuard.checkPlayer(sender);
        if (!player.hasPermission("worldguard.region.toggle-bypass")) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }
        Session session = WorldGuard.getInstance().getPlatform().getSessionManager().get(player);
        boolean shouldEnableBypass;
        if (args.argsLength() > 0) {
            String arg1 = args.getString(0);
            if (!arg1.equalsIgnoreCase("on") && !arg1.equalsIgnoreCase("off")) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.command.region.toggle-bypass.on-or-off"),
                        ImmutableList.of()
                );
            }
            shouldEnableBypass = arg1.equalsIgnoreCase("on");
        } else {
            shouldEnableBypass = session.hasBypassDisabled();
        }
        if (shouldEnableBypass) {
            session.setBypassDisabled(false);
            player.print(TranslatableComponent.of("worldguard.command.region.toggle-bypass.now-bypassing"));
        } else {
            session.setBypassDisabled(true);
            player.print(TranslatableComponent.of("worldguard.command.region.toggle-bypass.no-longer-bypassing"));
        }
    }

    private static class FlagListBuilder implements Callable<Component> {
        private final FlagRegistry flagRegistry;
        private final RegionPermissionModel permModel;
        private final ProtectedRegion existing;
        private final World world;
        private final String regionId;
        private final Actor sender;
        private final String flagName;

        FlagListBuilder(FlagRegistry flagRegistry, RegionPermissionModel permModel, ProtectedRegion existing,
                        World world, String regionId, Actor sender, String flagName) {
            this.flagRegistry = flagRegistry;
            this.permModel = permModel;
            this.existing = existing;
            this.world = world;
            this.regionId = regionId;
            this.sender = sender;
            this.flagName = flagName;
        }

        @Override
        public Component call() {
            ArrayList<String> flagList = new ArrayList<>();

            // Need to build a list
            for (Flag<?> flag : flagRegistry) {
                // Can the user set this flag?
                if (!permModel.maySetFlag(existing, flag)) {
                    continue;
                }

                flagList.add(flag.getName());
            }

            Collections.sort(flagList);

            final TextComponent.Builder builder = TextComponent.builder();

            final HoverEvent clickToSet = HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TranslatableComponent.of("worldguard.command.region.flag.flag-list.click-to-set"));
            for (int i = 0; i < flagList.size(); i++) {
                String flag = flagList.get(i);

                builder.append(TextComponent.of(flag, i % 2 == 0 ? TextColor.GRAY : TextColor.WHITE)
                        .hoverEvent(clickToSet).clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND,
                                "/rg flag -w \"" + world.getName() + "\" " + regionId + " " + flag + " ")));
                if (i < flagList.size() + 1) {
                    builder.append(TextComponent.of(", "));
                }
            }
            Component ret = TextComponent.builder().color(TextColor.RED)
                    .append(TranslatableComponent.of("worldguard.command.region.flag.flag-list.unknown-flag-specified", TextColor.RED)
                            .args(TextComponent.of(flagName))).build()
                    .append(TextComponent.newline())
                    .append(TranslatableComponent.of("worldguard.command.region.flag.flag-list.available-flags"))
                    .append(builder.build());
            if (sender.isPlayer()) {
                return ret.append(TranslatableComponent.of("worldguard.command.region.flag.flag-list.or-use-command", TextColor.LIGHT_PURPLE)
                        .args(TextComponent.of("/rg flags " + regionId, TextColor.AQUA)
                                .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND,
                                        "/rg flags -w \"" + world.getName() + "\" " + regionId))));
            }
            return ret;
        }
    }
}
