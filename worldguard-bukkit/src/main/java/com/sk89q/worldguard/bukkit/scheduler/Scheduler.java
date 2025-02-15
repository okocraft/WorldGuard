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

package com.sk89q.worldguard.bukkit.scheduler;

import com.sk89q.worldedit.util.report.ReportList;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import org.bukkit.Location;

public interface Scheduler {

    static Scheduler create(WorldGuardPlugin plugin) {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return new FoliaScheduler(plugin);
        } catch (ClassNotFoundException e) {
            return new BukkitScheduler(plugin);
        }
    }

    void runGlobal(Runnable task);

    void runGlobalAtFixedRate(Runnable task, long delay, long period);

    void runAtRegion(Location location, Runnable task);

    void runAsyncAtFixedRate(Runnable task, long delay, long period);

    void cancelTasks();

    default void addReport(ReportList report) {
    }
}
