/*
 * Copyright (C) 2016 AlternaCraft
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
package es.jlh.pvptitles.Commands;

import es.jlh.pvptitles.Backend.Exceptions.DBException;
import es.jlh.pvptitles.Events.Handlers.HandlePlayerFame;
import es.jlh.pvptitles.Files.LangsFile;
import es.jlh.pvptitles.Files.LangsFile.LangType;
import es.jlh.pvptitles.Files.TemplatesFile.FILES;
import static es.jlh.pvptitles.Files.TemplatesFile.FAME_TITLE_TAG;
import static es.jlh.pvptitles.Files.TemplatesFile.FAME_VALUE_TAG;
import static es.jlh.pvptitles.Files.TemplatesFile.KS_TITLE_TAG;
import static es.jlh.pvptitles.Files.TemplatesFile.KS_VALUE_TAG;
import static es.jlh.pvptitles.Files.TemplatesFile.NEXT_RANK_TAG;
import static es.jlh.pvptitles.Files.TemplatesFile.PLUGIN_TAG;
import static es.jlh.pvptitles.Files.TemplatesFile.RANK_TITLE_TAG;
import static es.jlh.pvptitles.Files.TemplatesFile.RANK_VALUE_TAG;
import es.jlh.pvptitles.Main.Manager;
import es.jlh.pvptitles.Main.PvpTitles;
import static es.jlh.pvptitles.Main.PvpTitles.PLUGIN;
import es.jlh.pvptitles.Misc.Localizer;
import es.jlh.pvptitles.Misc.Ranks;
import es.jlh.pvptitles.Misc.StrUtils;
import static es.jlh.pvptitles.Misc.StrUtils.splitToComponentTimes;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static es.jlh.pvptitles.Files.TemplatesFile.VETO_TAG;

public class RankCommand implements CommandExecutor {

    private PvpTitles pt = null;

    public RankCommand(PvpTitles pt) {
        this.pt = pt;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String arg, String[] args) {
        LangsFile.LangType messages = (sender instanceof Player) ? Localizer.getLocale((Player) sender) : Manager.messages;

        if (!(sender instanceof Player)) {
            sender.sendMessage(PLUGIN + LangsFile.COMMAND_FORBIDDEN.getText(messages));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            this.HandleRankCmd(player);
            return true;
        }

        return false;
    }

    /**
     * Método para enviar los datos del rango de un jugador
     *
     * @param player Jugador que consulta los datos
     */
    private void HandleRankCmd(Player player) {
        String uuid = player.getUniqueId().toString();

        int fame = 0;
        try {
            fame = pt.manager.dbh.getDm().loadPlayerFame(player.getUniqueId(), null);
        } catch (DBException ex) {
            PvpTitles.logError(ex.getCustomMessage(), null);
        }

        int racha = HandlePlayerFame.getKillStreakFrom(uuid);

        int seconds = 0;
        try {
            seconds = pt.manager.dbh.getDm().loadPlayedTime(player.getUniqueId())
                    + pt.getTimerManager().getPlayer(pt.getServer().getOfflinePlayer(player.getUniqueId())).getTotalOnline();
        } catch (DBException ex) {
            PvpTitles.logError(ex.getCustomMessage(), null);
        }

        String rank = Ranks.getRank(fame, seconds);

        int rankup = Ranks.fameToRankUp();
        int timeup = Ranks.nextRankTime();

        String nextRank = Ranks.nextRankTitle();
        String tag = pt.manager.params.getTag();

        LangType lang = Localizer.getLocale(player);

        String[] lines = this.pt.manager.templates.getFileContent(FILES.RANK_COMMAND);

        for (String line : lines) {
            String msg = line;

            if (!line.isEmpty()) {
                msg = msg
                        .replace(PLUGIN_TAG, PLUGIN)
                        .replace(RANK_TITLE_TAG, LangsFile.RANK_INFO_TITLE.getText(lang))
                        .replace(RANK_VALUE_TAG, rank)
                        .replace(FAME_TITLE_TAG, LangsFile.RANK_INFO_TAG.getText(lang)
                                .replace("%tag%", tag))
                        .replace(FAME_VALUE_TAG, String.valueOf(fame))
                        .replace(KS_TITLE_TAG, LangsFile.RANK_INFO_KS.getText(lang))
                        .replace(KS_VALUE_TAG, String.valueOf(racha));

                if (rankup > 0 || timeup > 0) {
                    msg = msg
                            .replace(NEXT_RANK_TAG, LangsFile.RANK_INFO_NEXTRANK.getText(lang)
                                    .replace("%rankup%", String.valueOf(rankup))
                                    .replace("%timeup%", StrUtils.splitToComponentTimes(timeup))
                                    .replace("%tag%", tag)
                                    .replace("%nextRank%", nextRank));
                } else if (msg.contains(NEXT_RANK_TAG)) {
                    continue;
                }

                if (HandlePlayerFame.getAfm().isVetado(player.getUniqueId().toString())) {
                    msg = msg
                            .replace(VETO_TAG, LangsFile.VETO_STARTED.getText(Localizer.getLocale(player))
                                    .replace("%tag%", pt.manager.params.getTag())
                                    .replace("%time%", splitToComponentTimes(HandlePlayerFame.getAfm().getVetoTime(uuid))));
                } else if (msg.contains(VETO_TAG)) {
                    continue;
                }
            }

            player.sendMessage(msg);
        }
    }
}
