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

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.scheduler.wrapper.BukkitAsyncScheduler;
import com.sk89q.worldguard.bukkit.scheduler.wrapper.BukkitScheduler;
import com.sk89q.worldguard.bukkit.scheduler.wrapper.FoliaAsyncScheduler;
import com.sk89q.worldguard.bukkit.scheduler.wrapper.FoliaEntityScheduler;
import com.sk89q.worldguard.bukkit.scheduler.wrapper.FoliaGlobalRegionScheduler;
import com.sk89q.worldguard.bukkit.scheduler.wrapper.FoliaRegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class Schedulers {

    public static final Plugin OWNING_PLUGIN = WorldGuardPlugin.inst();

    private static final boolean FOLIA = isFoliaAvailable();

    private static boolean isFoliaAvailable() {
        try {
            Bukkit.class.getDeclaredMethod("getAsyncScheduler");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private Schedulers() {
        throw new UnsupportedOperationException("Cannot instantiate this class.");
    }

    public static Scheduler getEntityScheduler(Entity entity) {
        if (FOLIA) {
            return new FoliaEntityScheduler(entity);
        } else {
            return new BukkitScheduler();
        }
    }

    public static Scheduler getGlobalRegionScheduler() {
        if (FOLIA) {
            return new FoliaGlobalRegionScheduler();
        } else {
            return new BukkitScheduler();
        }
    }

    public static Scheduler getRegionScheduler(Location location) {
        if (FOLIA) {
            return new FoliaRegionScheduler(location);
        } else {
            return new BukkitScheduler();
        }
    }

    public static Scheduler getAsyncScheduler() {
        if (FOLIA) {
            return new FoliaAsyncScheduler();
        } else {
            return new BukkitAsyncScheduler();
        }
    }
}
