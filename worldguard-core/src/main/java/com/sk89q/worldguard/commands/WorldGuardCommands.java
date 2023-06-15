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

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.minecraft.util.commands.NestedCommand;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.util.auth.AuthorizationException;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.component.MessageBox;
import com.sk89q.worldedit.util.formatting.component.TextComponentProducer;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.util.formatting.text.serializer.legacy.LegacyComponentSerializer;
import com.sk89q.worldedit.util.paste.ActorCallbackPaste;
import com.sk89q.worldedit.util.report.ReportList;
import com.sk89q.worldedit.util.report.SystemInfoReport;
import com.sk89q.worldedit.util.task.FutureForwardingTask;
import com.sk89q.worldedit.util.task.Task;
import com.sk89q.worldedit.util.task.TaskStateComparator;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.util.logging.LoggerToChatHandler;
import com.sk89q.worldguard.util.profiler.SamplerBuilder;
import com.sk89q.worldguard.util.profiler.SamplerBuilder.Sampler;
import com.sk89q.worldguard.util.profiler.ThreadIdFilter;
import com.sk89q.worldguard.util.profiler.ThreadNameFilter;
import com.sk89q.worldguard.util.report.ApplicableRegionsReport;
import com.sk89q.worldguard.util.report.ConfigReport;
import com.sk89q.worldguard.util.translation.TranslationAdder;
import org.enginehub.piston.exception.CommandException;

import java.io.File;
import java.io.IOException;
import java.lang.management.ThreadInfo;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

public class WorldGuardCommands {

    private final WorldGuard worldGuard;
    @Nullable
    private Sampler activeSampler;

    public WorldGuardCommands(WorldGuard worldGuard) {
        this.worldGuard = worldGuard;
    }

    @Command(aliases = {"version"}, desc = "Get the WorldGuard version", max = 0)
    public void version(CommandContext args, Actor sender) throws CommandException {
        sender.print(TextComponent.of("WorldGuard " + WorldGuard.getVersion()));
        sender.print(TextComponent.of("http://www.enginehub.org").clickEvent(ClickEvent.of(ClickEvent.Action.OPEN_URL, "http://www.enginehub.org")));

        sender.printDebug(TextComponent.of("----------- Platforms -----------"));
        sender.printDebug(TextComponent.of(String.format("* %s (%s)", worldGuard.getPlatform().getPlatformName(), worldGuard.getPlatform().getPlatformVersion())));
    }

    @Command(aliases = {"reload"}, desc = "Reload WorldGuard configuration", max = 0)
    @CommandPermissions({"worldguard.reload"})
    public void reload(CommandContext args, Actor sender) throws CommandException {
        // TODO: This is subject to a race condition, but at least other commands are not being processed concurrently
        List<Task<?>> tasks = WorldGuard.getInstance().getSupervisor().getTasks();
        if (!tasks.isEmpty()) {
            throw new CommandException(
                    TranslatableComponent.of("worldguard.error.command.worldguard.reload.there-are-tasks"),
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
            config.unload();

            TranslationAdder.addAndSaveTranslations();
            WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.CONFIGURATION).reload();

            config.load();
            for (World world : WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds()) {
                config.get(world);
            }
            WorldGuard.getInstance().getPlatform().getRegionContainer().reload();
            // WGBukkit.cleanCache();
            sender.print(TranslatableComponent.of("worldguard.command.worldguard.reload.reloaded"));
        } catch (Throwable t) {
            sender.printError(TranslatableComponent.of("worldguard.error.command.worldguard.reload.error")
                    .args(TextComponent.of(t.getMessage())));
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }

    @Command(aliases = {"report"}, desc = "Writes a report on WorldGuard", flags = "p", max = 0)
    @CommandPermissions({"worldguard.report"})
    public void report(CommandContext args, final Actor sender) throws CommandException, AuthorizationException {
        ReportList report = new ReportList("Report");
        worldGuard.getPlatform().addPlatformReports(report);
        report.add(new SystemInfoReport());
        report.add(new ConfigReport());
        if (sender instanceof LocalPlayer) {
            report.add(new ApplicableRegionsReport((LocalPlayer) sender));
        }
        String result = report.toString();

        try {
            File dest = new File(worldGuard.getPlatform().getConfigDir().toFile(), "report.txt");
            Files.write(result, dest, StandardCharsets.UTF_8);
            sender.print(TranslatableComponent.of("worldguard.command.worldguard.report.written")
                    .args(TextComponent.of(dest.getAbsolutePath())));
        } catch (IOException e) {
            throw new CommandException(TranslatableComponent.of("worldguard.error.command.worldguard.report.failed")
                    .args(TextComponent.of(e.getMessage())), ImmutableList.of());
        }

        if (args.hasFlag('p')) {
            sender.checkPermission("worldguard.report.pastebin");
            ActorCallbackPaste.pastebin(worldGuard.getSupervisor(), sender, result, "WorldGuard report: %s.report");
        }
    }

    @Command(aliases = {"profile"}, usage = "[-p] [-i <interval>] [-t <thread filter>] [<minutes>]",
            desc = "Profile the CPU usage of the server", min = 0, max = 1,
            flags = "t:i:p")
    @CommandPermissions("worldguard.profile")
    public void profile(final CommandContext args, final Actor sender) throws CommandException, AuthorizationException {
        Predicate<ThreadInfo> threadFilter;
        String threadName = args.getFlag('t');
        final boolean pastebin;

        if (args.hasFlag('p')) {
            sender.checkPermission("worldguard.report.pastebin");
            pastebin = true;
        } else {
            pastebin = false;
        }

        if (threadName == null) {
            threadFilter = new ThreadIdFilter(Thread.currentThread().getId());
        } else if (threadName.equals("*")) {
            threadFilter = thread -> true;
        } else {
            threadFilter = new ThreadNameFilter(threadName);
        }

        int minutes;
        if (args.argsLength() == 0) {
            minutes = 5;
        } else {
            minutes = args.getInteger(0);
            if (minutes < 1) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.worldguard.profile.run-for-1min"),
                        ImmutableList.of()
                );
            } else if (minutes > 10) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.worldguard.profile.at-maximum-10min"),
                        ImmutableList.of()
                );
            }
        }

        int interval = 20;
        if (args.hasFlag('i')) {
            interval = args.getFlagInteger('i');
            if (interval < 1 || interval > 100) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.worldguard.profile.interval-is-1-to-100"),
                        ImmutableList.of()
                );
            }
            if (interval < 10) {
                sender.printDebug(TranslatableComponent.of("worldguard.command.worldguard.profile.low-interval-note"));
            }
        }
        Sampler sampler;

        synchronized (this) {
            if (activeSampler != null) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.worldguard.profile.in-progress"),
                        ImmutableList.of()
                );
            }

            SamplerBuilder builder = new SamplerBuilder();
            builder.setThreadFilter(threadFilter);
            builder.setRunTime(minutes, TimeUnit.MINUTES);
            builder.setInterval(interval);
            sampler = activeSampler = builder.start();
        }


        sender.print(TranslatableComponent.of("worldguard.command.worldguard.profile.starting-cpu-profiling", TextColor.LIGHT_PURPLE).args(TextComponent.of(minutes))
                .append(TextComponent.newline())
                .append(TranslatableComponent.of("worldguard.command.worldguard.profile.to-stop", TextColor.GRAY)
                        .args(TextComponent.of("/wg stopprofile", TextColor.AQUA)
                                .clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND, "/wg stopprofile"))))
        );

        worldGuard.getSupervisor().monitor(FutureForwardingTask.create(
                sampler.getFuture(), "CPU profiling for " + minutes + " minutes", sender));

        sampler.getFuture().addListener(() -> {
            synchronized (WorldGuardCommands.this) {
                activeSampler = null;
            }
        }, MoreExecutors.directExecutor());

        Futures.addCallback(sampler.getFuture(), new FutureCallback<>() {
            @Override
            public void onSuccess(Sampler result) {
                String output = result.toString();

                try {
                    File dest = new File(worldGuard.getPlatform().getConfigDir().toFile(), "profile.txt");
                    Files.write(output, dest, StandardCharsets.UTF_8);
                    sender.print(TranslatableComponent.of("worldguard.command.worldguard.profile.data-written").args(TextComponent.of(dest.getAbsolutePath())));
                } catch (IOException e) {
                    sender.printError(TranslatableComponent.of("worldguard.error.command.worldguard.profile.failed").args(TextComponent.of(e.getMessage())));
                }

                if (pastebin) {
                    ActorCallbackPaste.pastebin(worldGuard.getSupervisor(), sender, output, "Profile result: %s.profile");
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        }, MoreExecutors.directExecutor());
    }

    @Command(aliases = {"stopprofile"}, usage = "", desc = "Stop a running profile", min = 0, max = 0)
    @CommandPermissions("worldguard.profile")
    public void stopProfile(CommandContext args, final Actor sender) throws CommandException {
        synchronized (this) {
            if (activeSampler == null) {
                throw new CommandException(
                        TranslatableComponent.of("worldguard.error.command.worldguard.stop-profile.not-running"),
                        ImmutableList.of()
                );
            }

            activeSampler.cancel();
            activeSampler = null;
        }

        sender.print(TranslatableComponent.of("worldguard.command.worldguard.stop-profile.cancelled"));
    }

    @Command(aliases = {"flushstates", "clearstates"},
            usage = "[player]", desc = "Flush the state manager", max = 1)
    @CommandPermissions("worldguard.flushstates")
    public void flushStates(CommandContext args, Actor sender) throws CommandException {
        if (args.argsLength() == 0) {
            WorldGuard.getInstance().getPlatform().getSessionManager().resetAllStates();
            sender.print(TranslatableComponent.of("worldguard.command.worldguard.flush-states.clear-all"));
        } else {
            LocalPlayer player = worldGuard.getPlatform().getMatcher().matchSinglePlayer(sender, args.getString(0));
            if (player != null) {
                WorldGuard.getInstance().getPlatform().getSessionManager().resetState(player);
                sender.print(TranslatableComponent.of("worldguard.command.worldguard.flush-states.clear-for-player")
                        .args(TextComponent.of(player.getName())));
            }
        }
    }

    @Command(aliases = {"running", "queue"}, desc = "List running tasks", max = 0)
    @CommandPermissions("worldguard.running")
    public void listRunningTasks(CommandContext args, Actor sender) throws CommandException {
        List<Task<?>> tasks = WorldGuard.getInstance().getSupervisor().getTasks();

        if (tasks.isEmpty()) {
            sender.print(TranslatableComponent.of("worldguard.command.worldguard.running.no-tasks"));
        } else {
            tasks.sort(new TaskStateComparator());
            String title = LegacyComponentSerializer.legacy().serialize(WorldEditText.format(TranslatableComponent.of("worldguard.command.worldguard.running.running-tasks"), sender.getLocale()));
            MessageBox builder = new MessageBox(title, new TextComponentProducer());
            builder.append(TranslatableComponent.of("worldguard.command.worldguard.running.waiting-task-note", TextColor.GRAY));
            for (Task<?> task : tasks) {
                builder.append(TextComponent.newline());
                builder.append(TextComponent.of("(" + task.getState().name() + ") ", TextColor.BLUE));
                builder.append(TextComponent.of(CommandUtils.getOwnerName(task.getOwner()) + ": ", TextColor.YELLOW));
                builder.append(TextComponent.of(task.getName(), TextColor.WHITE));
            }
            sender.print(builder.create());
        }
    }

    @Command(aliases = {"debug"}, desc = "Debugging commands")
    @NestedCommand({DebuggingCommands.class})
    public void debug(CommandContext args, Actor sender) {}

}
