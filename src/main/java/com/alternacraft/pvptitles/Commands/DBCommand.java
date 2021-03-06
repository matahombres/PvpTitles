/*
 * Copyright (C) 2018 AlternaCraft
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.alternacraft.pvptitles.Commands;

import com.alternacraft.pvptitles.Backend.DatabaseManager;
import com.alternacraft.pvptitles.Exceptions.DBException;
import com.alternacraft.pvptitles.Files.LangsFile;
import com.alternacraft.pvptitles.Main.CustomLogger;
import com.alternacraft.pvptitles.Main.Manager;
import com.alternacraft.pvptitles.Main.PvpTitles;
import com.alternacraft.pvptitles.Misc.Localizer;
import com.alternacraft.pvptitles.Misc.PluginLog;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static com.alternacraft.pvptitles.Main.PvpTitles.getPluginName;

public class DBCommand implements CommandExecutor {

    private static final String FILENAME_EXPORT_IMPORT = "database";
    
    private PvpTitles pvpTitles = null;

    public DBCommand(PvpTitles pvpTitles) {
        this.pvpTitles = pvpTitles;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String arg, String[] args) {
        LangsFile.LangType messages = (sender instanceof Player) ? Localizer.getLocale((Player) sender) : Manager.messages;

        if (args.length == 0) {
            sender.sendMessage(getPluginName() + LangsFile.COMMAND_ARGUMENTS.getText(messages));
            return false;
        }

        DatabaseManager dm = pvpTitles.getManager().getDBH().getDM();

        String filename;

        switch (args[0]) {
            case "export":
                if (args.length > 1) {
                    filename = args[1];
                } else {
                    filename = FILENAME_EXPORT_IMPORT;
                }
                filename += ".sql";

                try {
                    dm.DBExport(filename);
                    sender.sendMessage(getPluginName() + ChatColor.YELLOW + "Exported correctly");
                } catch (DBException ex) {
                    CustomLogger.logArrayError(ex.getCustomStackTrace());
                }

                break;
            case "import":
                if (args.length > 1) {
                    filename = args[1];
                } else {
                    filename = FILENAME_EXPORT_IMPORT;
                }
                filename += ".sql";

                try {
                    if (dm.DBImport(filename)) {
                        sender.sendMessage(getPluginName() + ChatColor.YELLOW + "Imported correctly");
                    }
                    else {
                        sender.sendMessage(getPluginName() + ChatColor.RED + "File '"
                            + filename + "' not found...");
                    }                    
                } catch (DBException ex) {
                    CustomLogger.logArrayError(ex.getCustomStackTrace());
                }               

                break;
            case "repair":
                try {
                    int changes = dm.repair();

                    if (changes > 0) {
                        sender.sendMessage(getPluginName() + ChatColor.YELLOW
                                + "Check out to the db_changes.txt file (Inside of "
                                + PluginLog.getLogsFolder() + ") to see the fixes");
                    }

                    sender.sendMessage(getPluginName() + LangsFile.DB_REPAIR_RESULT
                            .getText(messages).replace("%cant%", String.valueOf(changes)));

                } catch (DBException ex) {
                    CustomLogger.logArrayError(ex.getCustomStackTrace());
                }

                break;
            default:
                return false;
        }

        return true;
    }
}
