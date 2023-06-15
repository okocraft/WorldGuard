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

package com.sk89q.worldguard.protection.flags;

import com.google.gson.JsonSyntaxException;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.serializer.gson.GsonComponentSerializer;
import com.sk89q.worldedit.util.formatting.text.serializer.legacy.LegacyComponentSerializer;
import javax.annotation.Nullable;

/**
 * Stores a component.
 */
public class ComponentFlag extends Flag<Component> {

    private final Component defaultValue;

    public ComponentFlag(String name) {
        super(name);
        this.defaultValue = null;
    }

    public ComponentFlag(String name, Component defaultValue) {
        super(name);
        this.defaultValue = defaultValue;
    }

    public ComponentFlag(String name, RegionGroup defaultGroup) {
        super(name, defaultGroup);
        this.defaultValue = null;
    }

    public ComponentFlag(String name, RegionGroup defaultGroup, Component defaultValue) {
        super(name, defaultGroup);
        this.defaultValue = defaultValue;
    }

    @Nullable
    @Override
    public Component getDefault() {
        return defaultValue;
    }

    @Override
    public Component parseInput(FlagContext context) throws InvalidFlagFormatException {
        return parseInput(context.getUserInput());
    }

    private Component parseInput(String context) {
        try {
            return GsonComponentSerializer.INSTANCE.deserialize(context);
        } catch (JsonSyntaxException e) {
            return LegacyComponentSerializer.INSTANCE.deserialize(context);
        }
    }

    @Override
    public Component unmarshal(Object o) {
        if (o instanceof Component) {
            return (Component) o;
        } else if (o instanceof String) {
            return parseInput((String) o);
        } else {
            return null;
        }
    }

    @Override
    public Object marshal(Component o) {
        return GsonComponentSerializer.INSTANCE.serialize(o);
    }

}
