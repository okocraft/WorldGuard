/*
 * Schedulers, a scheduler wrapper which can use both folia and paper.
 * Copyright (C) Okocraft
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

package com.sk89q.worldguard.bukkit.scheduler.wrapper;

import com.sk89q.worldguard.bukkit.scheduler.Schedulers;
import java.util.function.Consumer;
import com.sk89q.worldguard.bukkit.scheduler.Scheduler;
import com.sk89q.worldguard.bukkit.scheduler.Task;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BukkitAsyncScheduler implements Scheduler {
    @Override
    public boolean execute(@NotNull Runnable run, @Nullable Runnable retired,
                           long delay) {
        try {
            return !Bukkit.getScheduler().runTaskLaterAsynchronously(Schedulers.OWNING_PLUGIN, run, delay).isCancelled();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public @Nullable Task run(@NotNull Consumer<Task> task,
                              @Nullable Runnable retired) {
        return runDelayed(task, retired, 1L);
    }

    @Override
    public @Nullable Task runDelayed(@NotNull Consumer<Task> task,
                                     @Nullable Runnable retired, long delayTicks) {
        try {
            BukkitTask t = new BukkitTask(false);
            Bukkit.getScheduler().runTaskLaterAsynchronously(Schedulers.OWNING_PLUGIN, bukkitTask -> {
                if (!bukkitTask.isCancelled()) {
                    t.provideHandle(bukkitTask);
                    task.accept(t);
                } else if (retired != null) {
                    retired.run();
                }
            }, delayTicks);
            return t;
        } catch (IllegalArgumentException e) {
            if (retired != null) {
                retired.run();
            }
            return null;
        }
    }

    @Override
    public @Nullable Task runAtFixedRate(@NotNull Consumer<Task> task,
                                         @Nullable Runnable retired, long initialDelayTicks,
                                         long periodTicks) {
        try {
            BukkitTask t = new BukkitTask(true);
            Bukkit.getScheduler().runTaskTimerAsynchronously(Schedulers.OWNING_PLUGIN, bukkitTask -> {
                if (!bukkitTask.isCancelled()) {
                    t.provideHandle(bukkitTask);
                    task.accept(t);
                } else if (retired != null) {
                    retired.run();
                }
            }, initialDelayTicks, periodTicks);
            return t;
        } catch (IllegalArgumentException e) {
            if (retired != null) {
                retired.run();
            }
            return null;
        }
    }
}
