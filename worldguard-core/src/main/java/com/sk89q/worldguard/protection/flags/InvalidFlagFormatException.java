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

import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.serializer.plain.PlainComponentSerializer;

public class InvalidFlagFormatException extends InvalidFlagFormat {
    private static final long serialVersionUID = 8101615074524004172L;

    private final Component richMessage;

    public InvalidFlagFormatException(String msg) {
        super(msg);
        this.richMessage = TextComponent.of(msg);
    }

    public InvalidFlagFormatException(Component msg) {
        super(PlainComponentSerializer.INSTANCE.serialize(msg));
        this.richMessage = msg;
    }

    public Component getRichMessage() {
        return richMessage;
    }
}
