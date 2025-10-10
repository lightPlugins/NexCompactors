package io.nexstudios.compactors.config;

import io.nexstudios.compactors.NexCompactors;
import io.nexstudios.nexus.bukkit.files.NexusFileReader;
import io.nexstudios.nexus.bukkit.utils.NexusLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class CompactorFileReader {

    private final NexCompactors plugin;
    private final NexusLogger logger;

    public List<CompactorConfig> readAll() {
        File folder = new File(plugin.getDataFolder(), "compactors");
        if (!folder.exists() && !folder.mkdirs()) {
            logger.error("Could not create 'compactors' folder.");
        }
        NexusFileReader nfr = new NexusFileReader("compactors", plugin);
        List<CompactorConfig> result = new ArrayList<>();
        nfr.getFiles().forEach((fileName) -> {
            logger.info("Parsing compactor file '" + fileName + "'");
            String id = fileName.getName().replace(".yml", ""); // remove .yml
            try {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(fileName);
                CompactorConfig parsed = CompactorYamlParser.parse(id, cfg, logger);
                result.add(parsed);
                logger.info("Parsed compactor file '" + fileName + "'");
            } catch (Exception ex) {
                logger.error("Failed to parse compactor file '" + fileName + "': " + ex.getMessage());
            }
        });
        return result;
    }
}