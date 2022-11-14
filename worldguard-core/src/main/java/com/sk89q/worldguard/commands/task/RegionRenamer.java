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

package com.sk89q.worldguard.commands.task;

import static com.google.common.base.Preconditions.checkNotNull;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import java.util.concurrent.Callable;

/**
 * Renames a region.
 */
public class RegionRenamer implements Callable<ProtectedRegion> {

    private final RegionManager manager;
    private final ProtectedRegion region;
    private final String newName;

    /**
     * Create a new instance.
     *
     * @param manager a region manager
     * @param region the region to remove
     */
    public RegionRenamer(RegionManager manager, ProtectedRegion region, String newName) {
        this.manager = checkNotNull(manager);
        this.region = checkNotNull(region);
        this.newName = checkNotNull(newName);
    }

    @Override
    public ProtectedRegion call() throws Exception {
        ProtectedRegion renamed;
        if (region.getType() == RegionType.CUBOID) {
            renamed = new ProtectedCuboidRegion(
                    newName,
                    region.isTransient(),
                    region.getMinimumPoint(),
                    region.getMaximumPoint()
            );
        } else {
            renamed = new ProtectedPolygonalRegion(
                    newName,
                    region.isTransient(),
                    region.getPoints(),
                    region.getMinimumPoint().getY(),
                    region.getMaximumPoint().getY()
            );
        }

        renamed.copyFrom(region);

        for (ProtectedRegion test : manager.getRegions().values()) {
            ProtectedRegion parent = test.getParent();
            if (parent != null && parent.equals(region)) {
                test.setParent(renamed);
            }
        }

        manager.addRegion(renamed);
        manager.removeRegion(region.getId(), RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
        return renamed;
    }
}
