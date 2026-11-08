package com.projectrebirth.client.codex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.mojang.logging.LogUtils;
import com.projectrebirth.codex.CodexBook;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Which codex entries this player has opened, so the book can mark the new ones. Kept on the player's own computer (a
 * line per entry in config/projectrebirth-codex-read.txt): it is only a reading aid, the server needs nothing of it.
 */
final class CodexReadState {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE = "projectrebirth-codex-read.txt";
    private static Set<String> read;

    private CodexReadState() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE);
    }

    private static Set<String> read() {
        if (read == null) {
            read = new LinkedHashSet<>();
            Path file = file();
            if (Files.exists(file)) {
                try {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (!line.isBlank()) {
                            read.add(line.strip());
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warn("Could not read {}", file, e);
                }
            }
        }
        return read;
    }

    static boolean isRead(String entryId) {
        return read().contains(entryId);
    }

    static boolean anyUnread(CodexBook.Category category) {
        return category.entries().stream().anyMatch(entry -> !isRead(entry.id()));
    }

    static void markRead(String entryId) {
        if (read().add(entryId)) {
            try {
                Files.write(file(), read, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("Could not save {}", file(), e);
            }
        }
    }
}
