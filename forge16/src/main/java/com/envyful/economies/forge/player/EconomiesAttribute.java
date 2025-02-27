package com.envyful.economies.forge.player;

import com.envyful.api.forge.player.ForgeEnvyPlayer;
import com.envyful.api.forge.player.attribute.AbstractForgeAttribute;
import com.envyful.api.player.EnvyPlayer;
import com.envyful.economies.api.Bank;
import com.envyful.economies.api.Economy;
import com.envyful.economies.forge.EconomiesForge;
import com.envyful.economies.forge.config.EconomiesConfig;
import com.envyful.economies.forge.config.EconomiesQueries;
import com.envyful.economies.forge.impl.ForgeBank;
import com.google.common.collect.Maps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class EconomiesAttribute extends AbstractForgeAttribute<EconomiesForge> {

    private Map<String, Bank> bankAccounts = Maps.newHashMap();

    public EconomiesAttribute(EconomiesForge manager, EnvyPlayer<?> parent) {
        super(manager, (ForgeEnvyPlayer) parent);
    }

    public Bank getAccount(Economy economy) {
        return this.bankAccounts.get(economy.getId());
    }

    @Override
    public void load() {
        if (OfflinePlayerManager.isCached(this.parent.getUuid())) {
            OfflinePlayerData offlinePlayerData = OfflinePlayerManager.removeCache(this.parent.getUuid());

            for (String s : EconomiesForge.getInstance().getConfig().getEconomies().keySet()) {
                Economy economy = EconomiesForge.getInstance().getConfig().getEconomies().get(s).getEconomy();
                this.bankAccounts.put(economy.getId(), offlinePlayerData.getBalance(economy));
            }

            return;
        }

        try (Connection connection = this.manager.getDatabase().getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(EconomiesQueries.LOAD_USER)) {
            preparedStatement.setString(1, this.parent.getUuid().toString());

            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                Economy economy = getEconomy(resultSet.getString("economy"));

                if (economy == null) {
                    continue;
                }

                this.bankAccounts.put(economy.getId(), new ForgeBank(
                        this.parent.getUuid(),
                        economy,
                        resultSet.getDouble("balance")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (String s : EconomiesForge.getInstance().getConfig().getEconomies().keySet()) {
            Economy economy = EconomiesForge.getInstance().getConfig().getEconomies().get(s).getEconomy();

            if (this.bankAccounts.containsKey(economy.getId())) {
                continue;
            }

            this.bankAccounts.put(economy.getId(), new ForgeBank(this.parent.getUuid(), economy,
                                                             economy.getDefaultValue()));
        }
    }

    private Economy getEconomy(String id) {
        for (EconomiesConfig.ConfigEconomy value : EconomiesForge.getInstance().getConfig().getEconomies().values()) {
            if (value.getEconomy().getEconomyIdentifier().equals(id)) {
                return value.getEconomy();
            }
        }
        return null;
    }

    @Override
    public void save() {
        try (Connection connection = this.manager.getDatabase().getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(EconomiesQueries.CREATE_OR_UPDATE_ACCOUNT)) {

            for (Map.Entry<String, Bank> value : this.bankAccounts.entrySet()) {
                preparedStatement.setString(1, this.parent.getUuid().toString());
                preparedStatement.setString(2, this.parent.getName());
                preparedStatement.setString(3, value.getValue().getEconomyId().getEconomyIdentifier());
                preparedStatement.setDouble(4, value.getValue().getBalance());
                preparedStatement.addBatch();
            }

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
