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
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import com.sk89q.worldguard.bukkit.scheduler.Task;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FoliaEntityScheduler extends FoliaScheduler {

    private final Entity entity;

    public FoliaEntityScheduler(Entity entity) {
        this.entity = entity;
    }

    @Override
    public boolean execute(@NotNull Runnable run, @Nullable Runnable retired,
                           long delay) {
        return entity.getScheduler().execute(Schedulers.OWNING_PLUGIN, run, retired, delay);
    }

    @Override
    public @Nullable Task run(@NotNull Consumer<Task> task,
                              @Nullable Runnable retired) {
        return executeRun((scheduler, wrappedTask) -> scheduler
                .run(Schedulers.OWNING_PLUGIN, unwrapTaskConsumer(task, wrappedTask), retired));
    }

    @Override
    public @Nullable Task runDelayed(@NotNull Consumer<Task> task,
                                     @Nullable Runnable retired, long delayTicks) {
        return executeRun((scheduler, wrappedTask) -> scheduler
                .runDelayed(Schedulers.OWNING_PLUGIN, unwrapTaskConsumer(task, wrappedTask), retired, delayTicks));
    }

    @Override
    public @Nullable Task runAtFixedRate(@NotNull Consumer<Task> task,
                                         @Nullable Runnable retired, long initialDelayTicks, long periodTicks) {
        return executeRun((scheduler, wrappedTask) -> scheduler
                .runAtFixedRate(Schedulers.OWNING_PLUGIN, unwrapTaskConsumer(task, wrappedTask), retired, initialDelayTicks, periodTicks));
    }

    private Task executeRun(BiFunction<EntityScheduler, FoliaTask, ScheduledTask> method)  {
        FoliaTask wrappedTask = new FoliaTask();
        ScheduledTask foliaTask = method.apply(entity.getScheduler(), wrappedTask);
        return foliaTask == null ? null : wrappedTask;
    }
}
