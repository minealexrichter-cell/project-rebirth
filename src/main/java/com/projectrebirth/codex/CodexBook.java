package com.projectrebirth.codex;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projectrebirth.ProjectRebirth;

/**
 * The contents of the Rebirth Codex, read from the mod's own files ({@code assets/projectrebirth/codex/}): {@code index.json}
 * lists the categories and their entries, {@code entries/<id>.json} holds an entry's place on the map, the entries it
 * follows and its pages. All text is in the language files: the title of a category is
 * {@code codex.projectrebirth.category.<id>}, of an entry {@code codex.projectrebirth.entry.<id>}, each page names its own key.
 * Plain data, no client classes: the screen draws it and the tests check it.
 */
public final class CodexBook {
    private static final String ROOT = "/assets/" + ProjectRebirth.MOD_ID + "/codex/";

    /**
     * One page. Types: {@code text}; {@code item} (an item drawn large), {@code block} (the 3D model of a block),
     * {@code entity} (a body, an armor), {@code recipe} (a crafting or station recipe, by its id), each with optional text
     * under it; {@code note} (a framed remark). Text that does not fit goes on to the next page.
     */
    public record Page(String type, @Nullable String value, @Nullable String text) {
    }

    public record Entry(String id, String category, String icon, int x, int y, List<String> parents, List<Page> pages) {
        public String titleKey() {
            return "codex." + ProjectRebirth.MOD_ID + ".entry." + id;
        }
    }

    public record Category(String id, String icon, List<Entry> entries) {
        public String titleKey() {
            return "codex." + ProjectRebirth.MOD_ID + ".category." + id;
        }
    }

    private static List<Category> loaded;

    private CodexBook() {
    }

    /** The whole book, read once. */
    public static synchronized List<Category> categories() {
        if (loaded == null) {
            loaded = load();
        }
        return loaded;
    }

    /** Every entry by its id. */
    public static Map<String, Entry> entries() {
        Map<String, Entry> all = new LinkedHashMap<>();
        for (Category category : categories()) {
            for (Entry entry : category.entries()) {
                all.put(entry.id(), entry);
            }
        }
        return all;
    }

    private static JsonObject read(String path) {
        InputStream stream = CodexBook.class.getResourceAsStream(ROOT + path);
        if (stream == null) {
            throw new IllegalStateException("Codex file missing: " + path);
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Codex file unreadable: " + path, e);
        }
    }

    private static String string(JsonObject json, String key, @Nullable String fallback) {
        JsonElement value = json.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static List<Category> load() {
        List<Category> categories = new ArrayList<>();
        for (JsonElement element : read("index.json").getAsJsonArray("categories")) {
            JsonObject json = element.getAsJsonObject();
            String id = json.get("id").getAsString();
            List<Entry> entries = new ArrayList<>();
            for (JsonElement entryId : json.getAsJsonArray("entries")) {
                entries.add(entry(entryId.getAsString(), id));
            }
            categories.add(new Category(id, json.get("icon").getAsString(), List.copyOf(entries)));
        }
        return List.copyOf(categories);
    }

    private static Entry entry(String id, String category) {
        JsonObject json = read("entries/" + id + ".json");
        List<String> parents = new ArrayList<>();
        JsonArray parentArray = json.getAsJsonArray("parents");
        if (parentArray != null) {
            parentArray.forEach(parent -> parents.add(parent.getAsString()));
        }
        List<Page> pages = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("pages")) {
            JsonObject page = element.getAsJsonObject();
            pages.add(new Page(page.get("type").getAsString(), string(page, "value", null), string(page, "text", null)));
        }
        return new Entry(id, category, json.get("icon").getAsString(), json.get("x").getAsInt(), json.get("y").getAsInt(),
                List.copyOf(parents), List.copyOf(pages));
    }
}
