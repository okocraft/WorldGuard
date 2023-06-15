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

package com.sk89q.worldguard.util.translation;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldguard.WorldGuard;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class TranslationAdder {

    private static final TranslationManager WORLDEDIT_TRANSLATION = new TranslationManager(
            WorldEdit.class,
            path -> WorldEdit.getInstance().getWorkingDirectoryPath(path)
    );

    private static final TranslationManager WORLDGUARD_TRANSLATION = new TranslationManager(
            WorldGuard.class,
            path -> WorldGuard.getInstance().getPlatform().getConfigDir().resolve(path)
    );

    private TranslationAdder() {}

    public static void addAndSaveTranslations() {
        Set<Locale> loaded = new HashSet<>();
        loaded.add(TranslationManager.getDefaultLocale());
        loaded.addAll(TranslationManager.getLoadedLocales());
        loaded.forEach(TranslationAdder::addAndSaveTranslations);
    }

    public static void addAndSaveTranslations(Locale locale) {
        Map<String, String> worldEditTranslations = WORLDEDIT_TRANSLATION.getTranslationMap(locale);
        Set<String> currentKeys = new HashSet<>(worldEditTranslations.keySet());
        WORLDGUARD_TRANSLATION.getTranslationMap(locale).forEach(worldEditTranslations::putIfAbsent);
        if (!currentKeys.containsAll(worldEditTranslations.keySet())) {
            saveWorldEditTranslations(locale, worldEditTranslations);
        }
    }

    private static void saveWorldEditTranslations(Locale locale, Map<String, String> translations) {

        try {
            Path path = WorldEdit.getInstance().getWorkingDirectoryPath("lang/" + TranslationManager.getLocalePath(locale));
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.copy(
                    new ByteArrayInputStream(TranslationManager.toJson(translations).getBytes(StandardCharsets.UTF_8)),
                    path,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
