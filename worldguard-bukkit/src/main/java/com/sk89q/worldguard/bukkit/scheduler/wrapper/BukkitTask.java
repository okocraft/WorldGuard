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

import com.sk89q.worldguard.bukkit.scheduler.Task;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class BukkitTask implements Task {

    private final boolean repeating;

    private org.bukkit.scheduler.BukkitTask handle = null;

    public BukkitTask(boolean repeating) {
        this.repeating = repeating;
    }

    public void provideHandle(org.bukkit.scheduler.BukkitTask handle) {
        this.handle = handle;
    }

    @Override
    public @NotNull Plugin getOwningPlugin() {
        return handle.getOwner();
    }

    @Override
    public boolean isRepeatingTask() {
        return repeating;
    }

    @Override
    public @NotNull CancelledState cancel() {
        handle.cancel();
        return handle.isCancelled()
                ? CancelledState.CANCELLED_ALREADY
                : CancelledState.RUNNING;
    }

    @Override
    public @NotNull ExecutionState getExecutionState() {
        if (Bukkit.getScheduler().isCurrentlyRunning(handle.getTaskId())) {
            return Bukkit.getScheduler().isQueued(handle.getTaskId())
                    ? ExecutionState.CANCELLED_RUNNING
                    : ExecutionState.RUNNING;
        } else if (Bukkit.getScheduler().isQueued(handle.getTaskId())) {
            return ExecutionState.IDLE;
        } else if (handle.isCancelled()) {
            return ExecutionState.CANCELLED;
        } else {
            return ExecutionState.FINISHED;
        }
    }

    public boolean isCancelled() {
        final ExecutionState state = this.getExecutionState();
        return state == ExecutionState.CANCELLED || state == ExecutionState.CANCELLED_RUNNING;
    }
}
