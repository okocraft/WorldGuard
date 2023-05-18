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

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import com.sk89q.worldguard.bukkit.scheduler.Scheduler;
import com.sk89q.worldguard.bukkit.scheduler.Task;

public abstract class FoliaScheduler implements Scheduler {

    protected static Consumer<ScheduledTask> unwrapTaskConsumer(Consumer<Task> taskConsumer, FoliaTask wrappedTask) {
        return foliaTask -> {
            wrappedTask.provideHandle(foliaTask);
            taskConsumer.accept(wrappedTask);
        };
    }

}
