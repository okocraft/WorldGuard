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

package com.sk89q.worldguard.bukkit.scheduler;

import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Scheduler {

    boolean execute(@NotNull Runnable run, @Nullable Runnable retired, long delay);

    @Nullable Task run(@NotNull Consumer<Task> task,
                                    @Nullable Runnable retired);

    @Nullable Task runDelayed(@NotNull Consumer<Task> task,
                                           @Nullable Runnable retired, long delayTicks);

    @Nullable Task runAtFixedRate(@NotNull Consumer<Task> task, @Nullable Runnable retired, long initialDelayTicks, long periodTicks);

}
