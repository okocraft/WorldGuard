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

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.util.gson.GsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Handles translations for the plugin.
 *
 * <p>
 * These should be in the following format:
 * plugin.component.message[.meta]*
 * </p>
 *
 * <p>
 * Where,
 * plugin = worldedit
 * component = The part of the plugin, eg expand
 * message = A descriptor for which message, eg, expanded
 * meta = Any extra information such as plural/singular (Can have none to infinite)
 * </p>
 */
public class TranslationManager {

    private static final Gson GSON = GsonUtil.createBuilder().setPrettyPrinting().create();
    private static final Type STRING_MAP_TYPE = new TypeToken<HashMap<String, String>>() {
    }.getType();

    public static String toJson(Map<String, String> map) {
        String json = GSON.toJson(map, STRING_MAP_TYPE);
        @SuppressWarnings("unchecked")
        TreeMap<String, Object> tree = GSON.fromJson(json, TreeMap.class);
        return GSON.toJson(tree);
    }

    private final Function<String, Path> localResourceLoader;
    private final Class<?> resourceHoldingClass;

    public TranslationManager(Class<?> resourceHoldingClass, Function<String, Path> localResourceLoader) {
        this.resourceHoldingClass = resourceHoldingClass;
        checkNotNull(localResourceLoader);
        this.localResourceLoader = localResourceLoader;
    }

    private Map<String, String> filterTranslations(Map<String, String> translations) {
        return translations.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> Maps.immutableEntry(e.getKey(), e.getValue().replace("'", "''")))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, String> parseTranslationFile(InputStream inputStream) throws IOException {
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return filterTranslations(GSON.fromJson(reader, STRING_MAP_TYPE));
        }
    }

    private Map<String, String> loadTranslationFile(String filename) {
        Map<String, String> baseTranslations = new ConcurrentHashMap<>();

        try {
            JarFile jar = new JarFile(resourceHoldingClass.getProtectionDomain().getCodeSource().getLocation().getPath());
            JarEntry stringsJson = jar.getJarEntry("lang/" + filename);
            if (stringsJson != null) {
                baseTranslations = parseTranslationFile(jar.getInputStream(stringsJson));
            }
        } catch (IOException e) {
            // Seem to be missing base. If the user has provided a file use that.
        }

        Path localFile = localResourceLoader.apply("lang/" + filename);
        if (Files.exists(localFile)) {
            try (InputStream stream = Files.newInputStream(localFile)) {
                baseTranslations.putAll(parseTranslationFile(stream));
            } catch (IOException e) {
                // Failed to parse custom language file. Worth printing.
                e.printStackTrace();
            }
        }

        return baseTranslations;
    }

    public Map<String, String> getTranslationMap(Locale locale) {
        Map<String, String> langData = new ConcurrentHashMap<>();

        // for missing translations, added default.
        if (!locale.equals(getDefaultLocale())) {
            langData.putAll(getTranslationMap(getDefaultLocale()));
        }

        // load
        if (!locale.getCountry().isEmpty()) {
            langData.putAll(loadTranslationFile(locale.getLanguage() + "-" + locale.getCountry() + "/strings.json"));
        }
        if (langData.isEmpty()) {
            langData = loadTranslationFile(locale.getLanguage() + "/strings.json");
        }

        // normally return here.
        if (langData.isEmpty()) {
            if (locale.equals(getDefaultLocale())) {
                langData.putAll(loadTranslationFile("strings.json"));
            }
        }

        return langData;
    }

    static String getLocalePath(Locale locale) {
        if (getDefaultLocale().equals(locale)) {
            return "strings.json";
        }
        String country = locale.getCountry().isEmpty() ? "" : "-" + locale.getCountry();
        return locale.getLanguage() + country + "/strings.json";
    }

    /**
     * Get worldedit default locale.
     *
     * @return The default locale of worldedit.
     */
    public static Locale getDefaultLocale() {
        com.sk89q.worldedit.util.translation.TranslationManager tm = WorldEdit.getInstance().getTranslationManager();

        try {
            Field defaultLocale = tm.getClass().getDeclaredField("defaultLocale");
            defaultLocale.setAccessible(true);
            return ((Locale) defaultLocale.get(tm));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
            return Locale.ENGLISH;
        }
    }

    @SuppressWarnings("unchecked")
    public static Set<Locale> getLoadedLocales() {
        com.sk89q.worldedit.util.translation.TranslationManager tm = WorldEdit.getInstance().getTranslationManager();

        try {
            Field loadedLocales = tm.getClass().getDeclaredField("loadedLocales");
            loadedLocales.setAccessible(true);
            return Collections.unmodifiableSet((Set<Locale>) loadedLocales.get(tm));
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
        }

        // for FastAsyncWorldEdit
        try {
            Field checkedLocales = tm.getClass().getDeclaredField("checkedLocales");
            checkedLocales.setAccessible(true);
            return Collections.unmodifiableSet((Set<Locale>) checkedLocales.get(tm));
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
        }

        return Collections.emptySet();
    }

}