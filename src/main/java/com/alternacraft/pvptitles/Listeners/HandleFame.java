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
package com.alternacraft.pvptitles.Listeners;

import com.alternacraft.pvptitles.Events.FameAddEvent;
import com.alternacraft.pvptitles.Events.FameEvent;
import com.alternacraft.pvptitles.Events.FameSetEvent;
import com.alternacraft.pvptitles.Events.RankChangedEvent;
import com.alternacraft.pvptitles.Exceptions.DBException;
import com.alternacraft.pvptitles.Exceptions.RanksException;
import com.alternacraft.pvptitles.Files.LangsFile;
import com.alternacraft.pvptitles.Hooks.VaultHook;
import com.alternacraft.pvptitles.Main.CustomLogger;
import com.alternacraft.pvptitles.Main.Manager;
import com.alternacraft.pvptitles.Main.PvpTitles;
import com.alternacraft.pvptitles.Managers.RankManager;
import com.alternacraft.pvptitles.Misc.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.alternacraft.pvptitles.Main.PvpTitles.getPluginName;

public class HandleFame implements Listener {

    private final PvpTitles pt;
    private final Manager dm;

    /**
     * Contructor de la clase
     *
     * @param pt Plugin
     */
    public HandleFame(PvpTitles pt) {
        this.pt = pt;
        this.dm = pt.getManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFame(FameEvent e) {
        OfflinePlayer pl = e.getOfflinePlayer();

        if (pl == null) {
            e.setCancelled(true);
            return;
        }

        long seconds = 0;
        try {
            seconds = dm.getDBH().getDM().loadPlayedTime(pl.getUniqueId());
        } catch (DBException ex) {
            CustomLogger.logArrayError(ex.getCustomStackTrace());
        }
        
        boolean higher = e.getFameIncr() >= 0;
        
        // Avoid rewards for victims
        if (higher) {
            //<editor-fold defaultstate="collapsed" desc="REWARDS FILTERS">        
            if (!(e instanceof FameSetEvent) && !(e instanceof FameAddEvent)) {
                List<Map<String, Map<String, Object>>> kills = pt.getManager().rewards.get("onKill");
                if (kills != null) {
                    kills
                            .stream()
                            .filter(kill -> (kill != null && hasPermission(kill.get(null), pl)))
                            .forEachOrdered(kill -> {
                                setValues(kill.get(null), e.getOfflinePlayer(), e.getFameTotal());
                            });
                }
            }

            List<Map<String, Map<String, Object>>> fame = pt.getManager().rewards.get("onFame");
            if (fame != null) {
                fame
                        .stream()
                        .filter(Objects::nonNull)
                        .forEachOrdered(famee -> {
                            famee.keySet()
                                    .stream()
                                    .filter(cantidad -> (e.getFame() < Integer.parseInt(cantidad)
                                                && (e.getFameTotal()) >= Integer.valueOf(cantidad)
                                                && hasPermission(famee.get(cantidad), pl)))
                                    .forEachOrdered(cantidad -> {
                                        setValues(famee.get(cantidad), e.getOfflinePlayer(), 
                                                e.getFameTotal());
                                    });
                        });
            }

            List<Map<String, Map<String, Object>>> rank = pt.getManager().rewards.get("onRank");
            if (rank != null) {
                for (Map<String, Map<String, Object>> rankk : rank) {
                    if (rankk != null) {
                        for (String rango : rankk.keySet()) {
                            try {
                                Rank oldRank = RankManager.getRank(e.getFame(), seconds, pl);
                                Rank newRank = RankManager.getRank(e.getFameTotal(), seconds, pl);
                                if (rango.equals(newRank.getId()) && !rango.equals(oldRank.getId())
                                        && hasPermission(rankk.get(rango), pl)) {
                                    setValues(rankk.get(rango), e.getOfflinePlayer(), e.getFameTotal());
                                }
                            } catch (RanksException ex) {
                                CustomLogger.logArrayError(ex.getCustomStackTrace());
                            }
                        }
                    }
                }
            }

            List<Map<String, Map<String, Object>>> killstreak = pt.getManager().rewards.get("onKillstreak");
            if (killstreak != null) {
                killstreak
                        .stream()
                        .filter(Objects::nonNull)
                        .forEachOrdered(ks -> {
                            ks.keySet()
                                    .stream()
                                    .filter(kss -> (e.getKillstreak() == Integer.valueOf(kss)
                                                && hasPermission(ks.get(kss), pl)))
                                    .forEachOrdered(kss -> {
                                        setValues(ks.get(kss), e.getOfflinePlayer(), e.getFameTotal());
                                    });
                        });
            }
            //</editor-fold>
        }

        // Nuevo rango
        if (e.getOfflinePlayer().isOnline()) {
            int fameA = e.getFame();
            int fameD = e.getFameTotal();

            TimedPlayer tp = pt.getManager().getTimerManager().getPlayer(pl);
            long totalTime = seconds + ((tp == null) ? 0 : tp.getTotalOnline());

            try {
                Rank actualRank = RankManager.getRank(fameA, totalTime, pl);
                Rank newRank = RankManager.getRank(fameD, totalTime, pl);

                // Ha conseguido otro rango
                if (!actualRank.similar(newRank)) {
                    pt.getServer().getPluginManager().callEvent(new RankChangedEvent(
                            pl, actualRank.getId(), newRank.getId(), higher));
                }
            } catch (RanksException ex) {
                CustomLogger.logArrayError(ex.getCustomStackTrace());
            }
        }
    }

    //<editor-fold defaultstate="collapsed" desc="INNER CODE">   
    private void setValues(Map<String, Object> data, OfflinePlayer pl, int fame) {
        if (VaultHook.ECONOMY_ENABLED) {
            Economy economy = VaultHook.economy;
            if (economy != null && data.containsKey("money")) {
                double money = Math.round((double) data.get("money")
                        * Manager.getInstance().params.getMultiplier("RMoney", pl));
                economy.depositPlayer(pl, money);
            }
        }

        if (data.containsKey("points")) {
            int points = (int) Math.round((int) data.get("points")
                    * Manager.getInstance().params.getMultiplier("RPoints", pl));

            FameAddEvent event = new FameAddEvent(pl, fame, points);
            event.setSilent(true);
            try {
                this.dm.getDBH().getDM().savePlayerFame(pl.getUniqueId(),
                        event.getFameTotal(), null);
            } catch (DBException ex) {
                CustomLogger.logArrayError(ex.getCustomStackTrace());
                event.setCancelled(true);
            }
            fameLogic(event);
        }
        if (data.containsKey("time")) {
            long time = Math.round((long) data.get("time") * Manager.getInstance().params.getMultiplier("RTime", pl));
            TimedPlayer aux = Manager.getInstance().getTimerManager().getPlayer(pl);
            if (aux != null) aux.addSession(new Session(0L, time));
            if (pl.isOnline()) {
                pl.getPlayer().sendMessage(getPluginName()
                        + LangsFile.PLAYEDTIME_CHANGE_PLAYER.getText(Localizer.getLocale(pl.getPlayer()))
                                .replace("%time%", StrUtils.splitToComponentTimes(time))
                );
            }
        }
        if (data.containsKey("commands")) {
            ((List<String>) data.get("commands"))
                    .stream()
                    .map(cmd -> cmd.replaceAll("<[pP]layer>", pl.getName()))
                    .map(StrUtils::translateColors)
                    .forEachOrdered(cmd -> {
                        pt.getServer().dispatchCommand(pt.getServer().getConsoleSender(), cmd);
                    });
        }
    }

    public boolean hasPermission(Map<String, Object> data, OfflinePlayer pl) {
        if (!data.containsKey("permission")) {
            return true;
        } else if (!pl.isOnline()) {
            return false;
        }
        String perm = (String) data.get("permission");
        return VaultHook.hasPermission(perm, pl.getPlayer());
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="MANAGING MESSAGES">
    @EventHandler(ignoreCancelled = true)
    public void onSetFame(FameSetEvent e) {
        fameLogic(e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAddFame(FameAddEvent e) {
        fameLogic(e);
    }

    private void fameLogic(FameEvent e) {
        if (!e.getOfflinePlayer().isOnline()) {
            return;
        }

        Player pl = (Player) e.getOfflinePlayer();
        long seconds = 0;
        try {
            seconds = dm.getDBH().getDM().loadPlayedTime(e.getOfflinePlayer().getUniqueId());
        } catch (DBException ex) {
            CustomLogger.logArrayError(ex.getCustomStackTrace());
        }

        if (!e.isSilent()) {
            Rank rank = null;
            try {
                rank = RankManager.getRank(e.getFameTotal(), seconds, pl);

                if (e.getWorldname() != null) {
                    pl.sendMessage(getPluginName() + LangsFile.FAME_MW_CHANGE_PLAYER.getText(Localizer.getLocale(pl))
                            .replace("%fame%", String.valueOf(e.getFameTotal()))
                            .replace("%rank%", rank.getDisplay())
                            .replace("%world%", e.getWorldname())
                            .replace("%tag%", this.dm.params.getTag())
                    );
                } else {
                    pl.sendMessage(getPluginName() + LangsFile.FAME_CHANGE_PLAYER.getText(Localizer.getLocale(pl))
                            .replace("%fame%", String.valueOf(e.getFameTotal()))
                            .replace("%rank%", rank.getDisplay())
                            .replace("%tag%", this.dm.params.getTag())
                    );
                }
            } catch (RanksException ex) {
                CustomLogger.logArrayError(ex.getCustomStackTrace());
            }
        }
    }
    //</editor-fold>
}
