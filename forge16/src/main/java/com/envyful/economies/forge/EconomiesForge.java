package com.envyful.economies.forge;

import com.envyful.api.concurrency.UtilConcurrency;
import com.envyful.api.config.yaml.YamlConfigFactory;
import com.envyful.api.database.Database;
import com.envyful.api.database.impl.SimpleHikariDatabase;
import com.envyful.api.forge.command.ForgeCommandFactory;
import com.envyful.api.forge.player.ForgeEnvyPlayer;
import com.envyful.api.forge.player.ForgePlayerManager;
import com.envyful.economies.api.Economy;
import com.envyful.economies.api.platform.PlatformController;
import com.envyful.economies.forge.command.BalanceCommand;
import com.envyful.economies.forge.command.BaltopCommand;
import com.envyful.economies.forge.command.EconomiesCommand;
import com.envyful.economies.forge.command.PayCommand;
import com.envyful.economies.forge.config.EconomiesConfig;
import com.envyful.economies.forge.config.EconomiesLocale;
import com.envyful.economies.forge.config.EconomiesQueries;
import com.envyful.economies.forge.impl.EconomyTabCompleter;
import com.envyful.economies.forge.player.EconomiesAttribute;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Mod("economies")
@Mod.EventBusSubscriber
public class EconomiesForge {

    protected static final String VERSION = "1.7.2";

    private static EconomiesForge instance;

    private ForgePlayerManager playerManager = new ForgePlayerManager();
    private ForgeCommandFactory commandFactory = new ForgeCommandFactory();

    private EconomiesConfig config;
    private EconomiesLocale locale;
    private Database database;
    private PlatformController platformController;

    public EconomiesForge() {
        instance = this;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPreInit(FMLServerAboutToStartEvent event) {
        instance = this;

        this.loadConfig();

        UtilConcurrency.runAsync(() -> {
            this.database = new SimpleHikariDatabase(this.config.getDatabase());

            try (Connection connection = this.database.getConnection();
                 PreparedStatement preparedStatement = connection.prepareStatement(EconomiesQueries.CREATE_TABLE);
                 PreparedStatement updateStatement = connection.prepareStatement(EconomiesQueries.UPDATE_TABLE)) {
                preparedStatement.executeUpdate();
                updateStatement.executeUpdate();
            } catch (SQLException e) {
                /*e.printStackTrace();*/
            }
        });
    }

    @SubscribeEvent
    public void onInit(FMLServerStartingEvent event) {
        this.playerManager.registerAttribute(this, EconomiesAttribute.class);

        this.commandFactory.registerInjector(Economy.class, (sender, args) -> {
            if (args[0].equalsIgnoreCase("default")) {
                for (EconomiesConfig.ConfigEconomy value : this.getConfig().getEconomies().values()) {
                    if (value.getEconomy().isDefault()) {
                        return value.getEconomy();
                    }
                }
            }

            Economy economyFromConfig = this.getEconomyFromConfig(args[0]);

            if (economyFromConfig == null) {
                return null;
            }

            return economyFromConfig;
        });

        this.commandFactory.registerCompleter(new EconomyTabCompleter());
    }

    private Economy getEconomyFromConfig(String name) {
        for (EconomiesConfig.ConfigEconomy value : this.config.getEconomies().values()) {
            if (value.getEconomy().getId().equals(name)) {
                return value.getEconomy();
            }
        }

        return null;
    }

    public void loadConfig() {
        try {
            this.config = YamlConfigFactory.getInstance(EconomiesConfig.class);
            this.locale = YamlConfigFactory.getInstance(EconomiesLocale.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onServerStart(FMLServerStartedEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        this.commandFactory.registerCommand(server, new EconomiesCommand());
        this.commandFactory.registerCommand(server, new PayCommand());
        this.commandFactory.registerCommand(server, new BalanceCommand());
        this.commandFactory.registerCommand(server, new BaltopCommand());
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        for (ForgeEnvyPlayer onlinePlayer : this.playerManager.getOnlinePlayers()) {
            EconomiesAttribute attribute = onlinePlayer.getAttribute(EconomiesForge.class);

            if (attribute != null) {
                attribute.save();
            }
        }
    }

    public static EconomiesForge getInstance() {
        return instance;
    }

    public EconomiesConfig getConfig() {
        return this.config;
    }

    public Database getDatabase() {
        return this.database;
    }

    public ForgePlayerManager getPlayerManager() {
        return this.playerManager;
    }

    public EconomiesLocale getLocale() {
        return this.locale;
    }

    public PlatformController getPlatformController() {
        return this.platformController;
    }

    public void setPlatformController(PlatformController platformController) {
        this.platformController = platformController;
    }
}
