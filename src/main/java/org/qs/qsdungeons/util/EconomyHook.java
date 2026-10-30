package org.qs.qsdungeons.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Acceso opcional a la economia de Vault por reflexion, para no depender de
 * Vault al compilar. Si no esta instalado, todo devuelve "sin economia".
 */
public final class EconomyHook {

    private Object economy;
    private Method has;
    private Method withdraw;
    private Method deposit;

    public boolean isAvailable() {
        if (economy != null) {
            return true;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return false;
        }
        try {
            Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(type);
            if (provider == null) {
                return false;
            }
            economy = provider.getProvider();
            has = type.getMethod("has", OfflinePlayer.class, double.class);
            withdraw = type.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = type.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            economy = null;
            return false;
        }
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (!isAvailable()) {
            return false;
        }
        try {
            return (boolean) has.invoke(economy, player, amount);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (!isAvailable()) {
            return false;
        }
        try {
            withdraw.invoke(economy, player, amount);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (!isAvailable()) {
            return false;
        }
        try {
            deposit.invoke(economy, player, amount);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public static String format(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 0.0001D) {
            return String.valueOf((long) Math.rint(amount));
        }
        return String.format(Locale.ROOT, "%.2f", amount);
    }
}
