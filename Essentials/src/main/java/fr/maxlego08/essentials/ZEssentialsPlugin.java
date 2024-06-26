package fr.maxlego08.essentials;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.FoliaImplementation;
import com.tcoded.folialib.impl.ServerImplementation;
import fr.maxlego08.essentials.api.Configuration;
import fr.maxlego08.essentials.api.ConfigurationFile;
import fr.maxlego08.essentials.api.EssentialsPlugin;
import fr.maxlego08.essentials.api.commands.CommandManager;
import fr.maxlego08.essentials.api.database.MigrationManager;
import fr.maxlego08.essentials.api.economy.EconomyProvider;
import fr.maxlego08.essentials.api.modules.ModuleManager;
import fr.maxlego08.essentials.api.placeholders.Placeholder;
import fr.maxlego08.essentials.api.placeholders.PlaceholderRegister;
import fr.maxlego08.essentials.api.server.EssentialsServer;
import fr.maxlego08.essentials.api.server.ServerType;
import fr.maxlego08.essentials.api.storage.Persist;
import fr.maxlego08.essentials.api.storage.ServerStorage;
import fr.maxlego08.essentials.api.storage.StorageManager;
import fr.maxlego08.essentials.api.storage.adapter.LocationAdapter;
import fr.maxlego08.essentials.api.user.User;
import fr.maxlego08.essentials.api.utils.EssentialsUtils;
import fr.maxlego08.essentials.api.utils.Warp;
import fr.maxlego08.essentials.buttons.ButtonHomes;
import fr.maxlego08.essentials.buttons.ButtonPayConfirm;
import fr.maxlego08.essentials.buttons.ButtonTeleportationConfirm;
import fr.maxlego08.essentials.commands.CommandLoader;
import fr.maxlego08.essentials.commands.ZCommandManager;
import fr.maxlego08.essentials.commands.commands.essentials.CommandEssentials;
import fr.maxlego08.essentials.database.ZMigrationManager;
import fr.maxlego08.essentials.economy.EconomyManager;
import fr.maxlego08.essentials.hooks.VaultEconomy;
import fr.maxlego08.essentials.listener.PlayerListener;
import fr.maxlego08.essentials.loader.ButtonWarpLoader;
import fr.maxlego08.essentials.messages.MessageLoader;
import fr.maxlego08.essentials.module.ZModuleManager;
import fr.maxlego08.essentials.module.modules.HomeModule;
import fr.maxlego08.essentials.placeholders.DistantPlaceholder;
import fr.maxlego08.essentials.placeholders.LocalPlaceholder;
import fr.maxlego08.essentials.server.PaperServer;
import fr.maxlego08.essentials.server.redis.RedisServer;
import fr.maxlego08.essentials.storage.ConfigStorage;
import fr.maxlego08.essentials.storage.ZStorageManager;
import fr.maxlego08.essentials.storage.adapter.UserTypeAdapter;
import fr.maxlego08.essentials.user.UserPlaceholders;
import fr.maxlego08.essentials.user.ZUser;
import fr.maxlego08.essentials.zutils.ZPlugin;
import fr.maxlego08.essentials.zutils.utils.CommandMarkdownGenerator;
import fr.maxlego08.essentials.zutils.utils.PlaceholderMarkdownGenerator;
import fr.maxlego08.essentials.zutils.utils.ZServerStorage;
import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.pattern.PatternManager;
import fr.maxlego08.menu.button.loader.NoneLoader;
import org.bukkit.Location;
import org.bukkit.permissions.Permissible;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ZEssentialsPlugin extends ZPlugin implements EssentialsPlugin {

    private final UUID consoleUniqueId = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private ServerStorage serverStorage = new ZServerStorage();
    private InventoryManager inventoryManager;
    private ButtonManager buttonManager;
    private PatternManager patternManager;
    private EssentialsServer essentialsServer = new PaperServer();

    @Override
    public void onEnable() {

        this.saveDefaultConfig();

        FoliaLib foliaLib = new FoliaLib(this);
        this.serverImplementation = foliaLib.getImpl();

        this.migrationManager = new ZMigrationManager(this);
        this.migrationManager.registerMigration();

        this.placeholder = new LocalPlaceholder(this);
        DistantPlaceholder distantPlaceholder = new DistantPlaceholder(this, this.placeholder);
        distantPlaceholder.register();

        this.economyProvider = new EconomyManager(this);

        this.inventoryManager = this.getProvider(InventoryManager.class);
        this.buttonManager = this.getProvider(ButtonManager.class);
        this.patternManager = this.getProvider(PatternManager.class);
        this.registerButtons();

        this.moduleManager = new ZModuleManager(this);

        this.gson = getGsonBuilder().create();
        this.persist = new Persist(this);

        // Configurations files
        this.registerConfiguration(new MessageLoader(this));
        this.registerConfiguration(this.configuration = new MainConfiguration(this));

        // Load configuration files
        this.configurationFiles.forEach(ConfigurationFile::load);
        ConfigStorage.getInstance().load(getPersist());

        // Commands
        this.commandManager = new ZCommandManager(this);
        this.registerCommand("zessentials", new CommandEssentials(this), "ess");

        CommandLoader commandLoader = new CommandLoader(this);
        commandLoader.loadCommands(this.commandManager);

        this.getLogger().info("Create " + this.commandManager.countCommands() + " commands.");

        // Essentials Server
        if (this.configuration.getServerType() == ServerType.REDIS) {
            this.essentialsServer = new RedisServer(this);
            this.getLogger().info("Using Redis server.");
        }

        this.essentialsServer.onEnable();

        // Storage
        this.storageManager = new ZStorageManager(this);
        this.registerListener(this.storageManager);
        this.storageManager.onEnable();

        this.moduleManager.loadModules();

        this.registerListener(new PlayerListener(this));
        this.registerPlaceholder(UserPlaceholders.class);

        this.generateDocs();
    }

    @Override
    public void onLoad() {

        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
            new VaultEconomy(this);
            getLogger().info("Register Vault Economy.");
        } catch (final ClassNotFoundException ignored) {
            ignored.printStackTrace();
        }

    }

    @Override
    public void onDisable() {

        // Storage
        if (this.storageManager != null) this.storageManager.onDisable();
        if (this.persist != null) ConfigStorage.getInstance().save(this.persist);

        this.essentialsServer.onDisable();

    }

    private void registerButtons() {

        this.buttonManager.register(new NoneLoader(this, ButtonTeleportationConfirm.class, "essentials_teleportation_confirm"));
        this.buttonManager.register(new NoneLoader(this, ButtonPayConfirm.class, "essentials_pay_confirm"));
        this.buttonManager.register(new NoneLoader(this, ButtonHomes.class, "essentials_homes"));
        this.buttonManager.register(new ButtonWarpLoader(this));

    }

    @Override
    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    @Override
    public List<ConfigurationFile> getConfigurationFiles() {
        return this.configurationFiles;
    }

    @Override
    public Gson getGson() {
        return this.gson;
    }

    @Override
    public Persist getPersist() {
        return this.persist;
    }

    @Override
    public ServerImplementation getScheduler() {
        return this.serverImplementation;
    }

    @Override
    public ModuleManager getModuleManager() {
        return this.moduleManager;
    }

    @Override
    public InventoryManager getInventoryManager() {
        return this.inventoryManager;
    }

    @Override
    public ButtonManager getButtonManager() {
        return this.buttonManager;
    }

    @Override
    public PatternManager getPatternManager() {
        return this.patternManager;
    }

    @Override
    public Placeholder getPlaceholder() {
        return this.placeholder;
    }

    @Override
    public StorageManager getStorageManager() {
        return this.storageManager;
    }

    private GsonBuilder getGsonBuilder() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.VOLATILE).registerTypeAdapter(Location.class, new LocationAdapter(this)).registerTypeAdapter(User.class, new UserTypeAdapter(this)).registerTypeAdapter(ZUser.class, new UserTypeAdapter(this));
    }

    private void registerPlaceholder(Class<? extends PlaceholderRegister> placeholderClass) {
        try {
            PlaceholderRegister placeholderRegister = placeholderClass.getConstructor().newInstance();
            placeholderRegister.register(this.placeholder, this);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    @Override
    public MigrationManager getMigrationManager() {
        return this.migrationManager;
    }

    @Override
    public boolean isEconomyEnable() {
        return this.economyProvider.isEnable();
    }

    @Override
    public EconomyProvider getEconomyProvider() {
        return this.economyProvider;
    }

    @Override
    public UUID getConsoleUniqueId() {
        return this.consoleUniqueId;
    }

    private void generateDocs() {
        CommandMarkdownGenerator commandMarkdownGenerator = new CommandMarkdownGenerator();
        PlaceholderMarkdownGenerator placeholderMarkdownGenerator = new PlaceholderMarkdownGenerator();

        File fileCommand = new File(getDataFolder(), "commands.md");
        File filePlaceholder = new File(getDataFolder(), "placeholders.md");
        try {
            commandMarkdownGenerator.generateMarkdownFile(this.commandManager.getCommands(), fileCommand.toPath());
            getLogger().info("Markdown 'commands.md' file successfully generated!");
        } catch (IOException exception) {
            getLogger().severe("Error while writing the file commands: " + exception.getMessage());
            exception.printStackTrace();
        }

        try {
            placeholderMarkdownGenerator.generateMarkdownFile(((LocalPlaceholder) this.placeholder).getAutoPlaceholders(), filePlaceholder.toPath());
            getLogger().info("Markdown 'placeholders.md' file successfully generated!");
        } catch (IOException exception) {
            getLogger().severe("Error while writing the file placeholders: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    @Override
    public ServerStorage getServerStorage() {
        return serverStorage;
    }

    @Override
    public void setServerStorage(ServerStorage serverStorage) {
        this.serverStorage = serverStorage;
    }

    @Override
    public boolean isFolia() {
        return this.serverImplementation instanceof FoliaImplementation;
    }

    @Override
    public List<Warp> getWarps() {
        return ConfigStorage.warps;
    }

    @Override
    public Optional<Warp> getWarp(String name) {
        return getWarps().stream().filter(warp -> warp.name().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public int getMaxHome(Permissible permissible) {
        return this.moduleManager.getModule(HomeModule.class).getMaxHome(permissible);
    }

    @Override
    public User getUser(UUID uniqueId) {
        return this.storageManager.getStorage().getUser(uniqueId);
    }

    @Override
    public EssentialsServer getEssentialsServer() {
        return this.essentialsServer;
    }

    @Override
    public EssentialsUtils getUtils() {
        return this.essentialsUtils;
    }

    @Override
    public void debug(String string) {
        if (this.configuration.isEnableDebug()) {
            this.getLogger().info(string);
        }
    }
}
