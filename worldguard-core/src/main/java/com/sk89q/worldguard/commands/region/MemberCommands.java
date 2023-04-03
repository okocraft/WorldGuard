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

import com.google.common.collect.ImmutableList;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.worldedit.command.util.AsyncCommandBuilder;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.formatting.text.serializer.legacy.LegacyComponentSerializer;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.util.DomainInputResolver;
import com.sk89q.worldguard.protection.util.DomainInputResolver.UserLocatorPolicy;

import java.util.concurrent.Callable;
import org.enginehub.piston.exception.CommandException;

public class MemberCommands extends RegionCommandsBase {

    private final WorldGuard worldGuard;

    public MemberCommands(WorldGuard worldGuard) {
        this.worldGuard = worldGuard;
    }

    @Command(aliases = {"addmember", "addmember", "addmem", "am"},
            usage = "<id> <members...>",
            flags = "nw:",
            desc = "Add a member to a region",
            min = 2)
    public void addMember(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(sender).mayAddMembers(region)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        // Resolve members asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
        resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_ONLY);

        final String description = LegacyComponentSerializer.legacy().serialize(
                WorldEditText.format(
                        TranslatableComponent.of("worldguard.command.member.add-member.adding-members")
                                .args(TextComponent.of(region.getId()), TextComponent.of(world.getName())),
                        sender.getLocale()
                )
        );

        AsyncCommandBuilder.wrap(resolver, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .onSuccess(TranslatableComponent.of("worldguard.command.member.add-member.member-added").args(TextComponent.of(region.getId())), region.getMembers()::addAll)
                .onFailure(TranslatableComponent.of("worldguard.command.member.add-member.failed"), worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    @Command(aliases = {"addowner", "addowner", "ao"},
            usage = "<id> <owners...>",
            flags = "nw:",
            desc = "Add an owner to a region",
            min = 2)
    public void addOwner(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world

        String id = args.getString(0);

        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(sender).mayAddOwners(region)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        // Resolve owners asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
        resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_ONLY);

        final String description = LegacyComponentSerializer.legacy().serialize(
                WorldEditText.format(
                        TranslatableComponent.of("worldguard.command.member.add-owner.adding-owners")
                                .args(TextComponent.of(region.getId()), TextComponent.of(world.getName())),
                        sender.getLocale()
                )
        );

        AsyncCommandBuilder.wrap(checkedAddOwners(sender, manager, region, world, resolver), sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .onSuccess(TranslatableComponent.of("worldguard.command.member.add-owner.owner-added").args(TextComponent.of(region.getId())), region.getOwners()::addAll)
                .onFailure(TranslatableComponent.of("worldguard.command.member.add-owner.failed"), worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    private static Callable<DefaultDomain> checkedAddOwners(Actor sender, RegionManager manager, ProtectedRegion region,
                                                            World world, DomainInputResolver resolver) {
        return () -> {
            DefaultDomain owners = resolver.call();
            // TODO this was always broken and never checked other players
            if (sender instanceof LocalPlayer) {
                LocalPlayer player = (LocalPlayer) sender;
                if (owners.contains(player) && !sender.hasPermission("worldguard.region.unlimited")) {
                    int maxRegionCount = WorldGuard.getInstance().getPlatform().getGlobalStateManager()
                            .get(world).getMaxRegionCount(player);
                    if (maxRegionCount >= 0 && manager.getRegionCountOfPlayer(player)
                            >= maxRegionCount) {
                        throw new CommandException(
                                TranslatableComponent.of("worldguard.error.command.member.add-owner.owning-maximum-regions"),
                                ImmutableList.of()
                        );
                    }
                }
            }
            if (region.getOwners().size() == 0) {
                boolean anyOwners = false;
                ProtectedRegion parent = region;
                while ((parent = parent.getParent()) != null) {
                    if (parent.getOwners().size() > 0) {
                        anyOwners = true;
                        break;
                    }
                }
                if (!anyOwners) {
                    sender.checkPermission("worldguard.region.addowner.unclaimed." + region.getId().toLowerCase());
                }
            }
            return owners;
        };
    }

    @Command(aliases = {"removemember", "remmember", "removemem", "remmem", "rm"},
            usage = "<id> <owners...>",
            flags = "naw:",
            desc = "Remove an owner to a region",
            min = 1)
    public void removeMember(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(sender).mayRemoveMembers(region)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        Callable<DefaultDomain> callable;
        if (args.hasFlag('a')) {
            callable = region::getMembers;
        } else {
            if (args.argsLength() < 2) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.member.remove-member.list-names"),
                        ImmutableList.of()
                );
            }

            // Resolve members asynchronously
            DomainInputResolver resolver = new DomainInputResolver(
                    WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
            resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_AND_NAME);

            callable = resolver;
        }

        final String description = LegacyComponentSerializer.legacy().serialize(
                WorldEditText.format(
                        TranslatableComponent.of("worldguard.command.member.remove-member.removing-members")
                                .args(TextComponent.of(region.getId()), TextComponent.of(world.getName())),
                        sender.getLocale()
                )
        );

        AsyncCommandBuilder.wrap(callable, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .setDelayMessage(TranslatableComponent.of("worldguard.command.member.remove-member.delay-message"))
                .onSuccess(TranslatableComponent.of("worldguard.command.member.remove-member.member-removed").args(TextComponent.of(region.getId())), region.getMembers()::removeAll)
                .onFailure(TranslatableComponent.of("worldguard.command.member.remove-member.failed"), worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    @Command(aliases = {"removeowner", "remowner", "ro"},
            usage = "<id> <owners...>",
            flags = "naw:",
            desc = "Remove an owner to a region",
            min = 1)
    public void removeOwner(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(sender).mayRemoveOwners(region)) {
            // TODO: if we use piston correctly, we can remove this.
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.no-permission"),
                    ImmutableList.of()
            );
        }

        Callable<DefaultDomain> callable;
        if (args.hasFlag('a')) {
            callable = region::getOwners;
        } else {
            if (args.argsLength() < 2) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.member.remove-owner.list-names"),
                        ImmutableList.of()
                );
            }

            // Resolve owners asynchronously
            DomainInputResolver resolver = new DomainInputResolver(
                    WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
            resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_AND_NAME);

            callable = resolver;
        }

        final String description = LegacyComponentSerializer.legacy().serialize(
                WorldEditText.format(
                        TranslatableComponent.of("worldguard.command.member.remove-owner.removing-owners")
                                .args(TextComponent.of(region.getId()), TextComponent.of(world.getName())),
                        sender.getLocale()
                )
        );
        AsyncCommandBuilder.wrap(callable, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .setDelayMessage(TranslatableComponent.of("worldguard.command.member.remove-owner.delay-message"))
                .onSuccess(TranslatableComponent.of("worldguard.command.member.remove-owner.owner-removed").args(TextComponent.of(region.getId())), region.getOwners()::removeAll)
                .onFailure(TranslatableComponent.of("worldguard.command.member.remove-owner.failed"), worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }
}
