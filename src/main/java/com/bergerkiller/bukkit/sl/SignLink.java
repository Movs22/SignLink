package com.bergerkiller.bukkit.sl;

import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.PluginBase;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.protocol.PacketBlockStateChangeListener;
import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.sl.API.Variables;
import com.bergerkiller.bukkit.sl.impl.VariableMap;

public class SignLink extends PluginBase {
    public static SignLink plugin;
    public static boolean updateSigns = false;
    private Task updatetask;
    private Task updateordertask;
    private Task timetask;
    private Task papi_auto_task = null;
    private final SLListener listener = new SLListener();

    @Override
    public int getMinimumLibVersion() {
        return Common.VERSION;
    }

    /**
     * Gets the player by name, case-insensitive. The Bukkit getPlayer() has some
     * (performance) flaws, and on older versions of Minecraft the exact getter
     * wasn't case-insensitive at all.
     *
     * @param name Player name, must be all-lowercase
     * @return Player matching this name, or null if not online right now
     */
    public Player getPlayerByLowercase(String name) {
        return this.listener.getPlayerByLowercase(name);
    }

    /**
     * Whether all signs on the server are routinely checked for sign text changes.
     * When a sign suddenly has a variable displayed on it, this will make it change that
     * into a variable.
     * 
     * @return True when sign changes are automatically discovered
     */
    public boolean discoverSignChanges() {
        return false;
    }

    @Override
    public void enable() {
        plugin = this;

        this.register((Listener) this.listener);
        this.register(new SLBlockStateChangeListener(), PacketBlockStateChangeListener.LISTENED_TYPES);
        this.register("togglesignupdate", "reloadsignlink", "variable");


        VirtualSign.init();

        updateSigns = true;

        //Start updating
        updateordertask = new SignUpdateOrderTask(this).start(20, 20);
        updatetask = new SignUpdateTextTask(this).start(20, 20);

        // Load all signs in all worlds already loaded right now
        this.loadSigns();
    }

    @Override
    public void disable() {
        Task.stop(timetask);
        Task.stop(updatetask);
        Task.stop(updateordertask);
        Task.stop(papi_auto_task);

        VariableMap.INSTANCE.deinit();
        VirtualSign.deinit();
    }

    public void loadSigns() {
        for (World world : WorldUtil.getWorlds()) {
            this.listener.loadSigns(WorldUtil.getBlockStates(world));
        }
    }

    

    @Override
    public void permissions() {
        this.loadPermissions(Permission.class);
    }


    @Override
    public boolean command(CommandSender sender, String cmdLabel, String[] args) {
        // Toggle sign updating on/off
        if (cmdLabel.equalsIgnoreCase("togglesignupdate")) {
            Permission.TOGGLEUPDATE.handle(sender);
            updateSigns = !updateSigns;
            if (updateSigns) {
                sender.sendMessage(ChatColor.GREEN + "Signs are now being updated!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Signs are now no longer being updated!");
            }
            return true;
        }
        // Reload SignLink configuration
        if (cmdLabel.equalsIgnoreCase("reloadsignlink")) {
            Permission.RELOAD.handle(sender);
            loadSigns();
            sender.sendMessage(ChatColor.GREEN + "SignLink reloaded the Variable values");
            return true;
        }
        return false;
    }
    
    private static class SignUpdateOrderTask extends Task {
        public SignUpdateOrderTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            try {
                VirtualSignStore.globalUpdateSignOrders();
            } catch (Throwable t) {
                SignLink.plugin.log(Level.SEVERE, "An error occured while updating sign order:");
                SignLink.plugin.handle(t);
            }
        }
    }

    private static class SignUpdateTextTask extends Task {
        public SignUpdateTextTask(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public void run() {
            try {
                Variables.updateTickers();
                VirtualSignStore.forEachSign(VirtualSign::update);
            } catch (Throwable t) {
                SignLink.plugin.log(Level.SEVERE, "An error occured while updating sign text:");
                SignLink.plugin.handle(t);
            }
        }
    }

}
