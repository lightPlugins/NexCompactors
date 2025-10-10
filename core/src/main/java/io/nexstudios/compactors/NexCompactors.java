package io.nexstudios.compactors;

import io.nexstudios.compactors.register.CompactorRegistry;
import io.nexstudios.nexus.bukkit.files.NexusFile;
import io.nexstudios.nexus.bukkit.files.NexusFileReader;
import io.nexstudios.nexus.bukkit.handler.MessageSender;
import io.nexstudios.nexus.bukkit.inv.api.InvService;
import io.nexstudios.nexus.bukkit.inv.renderer.DefaultNexItemRenderer;
import io.nexstudios.nexus.bukkit.language.NexusLanguage;
import io.nexstudios.nexus.bukkit.utils.NexusLogger;
import io.nexstudios.nexus.libs.commands.PaperCommandManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class NexCompactors extends JavaPlugin {

    @Getter
    private static NexCompactors instance;
    public PaperCommandManager commandManager;
    public static NexusLogger nexusLogger;
    public NexusFile settingsFile;
    public NexusFileReader languageFiles;
    public NexusFileReader inventoryFiles;
    public NexusLanguage nexusLanguage;
    public MessageSender messageSender;

    private CompactorRegistry compactorRegistry;
    private InvService invService;


    @Override
    public void onLoad() {
        instance = this;
        if(!checkPluginRequirements()) return;
        nexusLogger = new NexusLogger("<reset>[<purple>NexCompactors<reset>]", true, 99, "<purple>");
        nexusLogger.info("Loading <purple>NexDrops <reset>...");
    }


    @Override
    public void onEnable() {
        nexusLogger.info("Starting up ...");
        nexusLogger.info("Register commands ...");
        commandManager = new PaperCommandManager(this);
        registerCommands();
        nexusLogger.info("Register events ...");
        registerEvents();

        compactorRegistry = new CompactorRegistry(this);
        compactorRegistry.loadAndRegisterAll();

        this.invService = new InvService(this, new DefaultNexItemRenderer(), nexusLanguage);
        this.invService.registerNamespace("nexcompactors", inventoryFiles);
        nexusLogger.info("Successfully started up.");
    }

    @Override
    public void onDisable() {

        if (compactorRegistry != null) {
            compactorRegistry.shutdown();
        }

        nexusLogger.info("Successfully disabled NexDrops");
    }

    public void onReload() {

        messageSender = new MessageSender(nexusLanguage);

        if (compactorRegistry != null) {
            compactorRegistry.reload();
        }
    }

    public void registerCommands() {
        int size = commandManager.getRegisteredRootCommands().size();
        nexusLogger.info("Successfully registered " + size  + " command(s).");
    }

    public void registerEvents() {

    }

    private void loadNexusFiles() {
        settingsFile = new NexusFile(this, "settings.yml", nexusLogger, true);

        new NexusFile(this, "languages/english.yml", nexusLogger, true);
        nexusLogger.setDebugEnabled(settingsFile.getBoolean("logging.debug.enable", true));
        nexusLogger.setDebugLevel(settingsFile.getInt("logging.debug.level", 3));

        inventoryFiles = new NexusFileReader("inventories", this);
        languageFiles = new NexusFileReader("languages", this);
        nexusLanguage = new NexusLanguage(languageFiles, nexusLogger);

        nexusLogger.info("All Nexus files have been (re)loaded successfully.");
    }

    private boolean checkPluginRequirements() {
        if(Bukkit.getPluginManager().getPlugin("Nexus") == null) {
            getLogger().severe("NexDrops requires the Nexus plugin to be installed.");
            getLogger().severe("Please download the plugin from https://www.spigotmc.org/resources/nexus.10000/");
            getLogger().severe("Disabling NexDrops ...");
            Bukkit.getPluginManager().disablePlugin(this);
            return false;
        }
        return true;
    }

}
