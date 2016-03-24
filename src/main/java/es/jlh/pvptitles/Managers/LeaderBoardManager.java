package es.jlh.pvptitles.Managers;

import es.jlh.pvptitles.Events.BoardEvent;
import es.jlh.pvptitles.Files.HologramsFile;
import es.jlh.pvptitles.Files.LangFile;
import es.jlh.pvptitles.Main.PvpTitles;
import static es.jlh.pvptitles.Main.PvpTitles.PLUGIN;
import es.jlh.pvptitles.Managers.BoardsCustom.SignBoard;
import es.jlh.pvptitles.Managers.BoardsAPI.Board;
import es.jlh.pvptitles.Misc.Localizer;
import es.jlh.pvptitles.Objects.CustomLocation;
import es.jlh.pvptitles.Objects.PlayerFame;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/**
 *
 * @author AlternaCraft
 */
public class LeaderBoardManager {

    private PvpTitles pt = null;
    private List<Board> boards = null;

    public LeaderBoardManager(PvpTitles pt) {
        this.boards = new ArrayList() {
            @Override
            public boolean contains(Object o) {
                if (o instanceof Board) {
                    Location l = ((Board) o).getData().getLocation();

                    for (Iterator iterator = this.iterator(); iterator.hasNext();) {
                        Board next = (Board) iterator.next();

                        if (l.equals(next.getData().getLocation())) {
                            // MultiServer
                            if (((Board) o).getData().getServer() != null
                                    && next.getData().getServer() != null) {
                                if (!((Board) o).getData().getServer().equals(next.getData().getServer())) {
                                    return false;
                                }
                            }
                            return true;
                        }
                    }
                }

                return false;
            }
        };
        this.pt = pt;
    }

    public boolean addBoard(Board b, Player pl) {
        if (!boards.contains(b)) {
            // Compruebo si ya hay algo ocupando el sitio            
            ArrayList<PlayerFame> pf = this.pt.cm.dbh.getDm().getTopPlayers(b.getModel().getCantidad(), b.getData().getServer());
            short jugadores = (short) pf.size();

            if (!b.isMaterializable(jugadores)) {
                pl.sendMessage(PLUGIN + LangFile.BOARD_CANT_BE_PLACED.getText(Localizer.getLocale(pl)));
                return false;
            }

            if (b instanceof SignBoard) {
                if (!pt.cm.dbh.getDm().registraBoard((SignBoard) b)) {
                    PvpTitles.logError("Error saving sign board", null);
                    return false;
                }
            } else {
                HologramsFile.saveHologram(b.getData());
            }

            b.materialize(pf);
            boards.add(b);

            pt.getServer().getPluginManager().callEvent(new BoardEvent(pl, b.getData().getFullLocation()));
            
            pl.sendMessage(PLUGIN + LangFile.BOARD_CREATED_CORRECTLY.
                    getText(Localizer.getLocale(pl)).replace("%name%", b.getData().getNombre()));
        } else {
            return false;
        }

        return true;
    }

    public void loadBoard(Board cs) {
        if (!boards.contains(cs)) {
            cs.materialize(pt.cm.dbh.getDm().getTopPlayers(
                    cs.getModel().getCantidad(), cs.getData().getServer()
            ));
            boards.add(cs);
        }
    }

    public void updateBoards() {
        for (Board board : boards) {
            ArrayList<PlayerFame> pf = pt.cm.dbh.getDm().getTopPlayers(
                    board.getModel().getCantidad(), board.getData().getServer());
            
            board.dematerialize((short) pf.size());
            board.materialize(pf);
        }
    }

    public void deleteBoard(Location l, Object o) {
        for (Board bo : boards) {
            if (bo.getData().getLocation().equals(CustomLocation.toCustomLocation(l))) {
                short jugadores = (short) pt.cm.dbh.getDm().getTopPlayers(
                        bo.getModel().getCantidad(), bo.getData().getServer()).size();

                Player pl = null;

                // Modulo Signs
                if (o != null) {
                    if (o instanceof BlockBreakEvent) {
                        BlockBreakEvent event = (BlockBreakEvent) o;
                        pl = event.getPlayer();

                        if (!pl.hasPermission("pvptitles.managesign")) {
                            pl.sendMessage(PLUGIN + LangFile.COMMAND_NO_PERMISSIONS.getText(Localizer.getLocale(pl)));
                            event.setCancelled(true);
                            return;
                        }
                    } else {
                        pl = (Player) o;

                        if (!pl.hasPermission("pvptitles.managesign")) {
                            pl.sendMessage(PLUGIN + LangFile.COMMAND_NO_PERMISSIONS.getText(Localizer.getLocale(pl)));
                            return;
                        }
                    }
                }

                if (bo instanceof SignBoard) {
                    if (!pt.cm.dbh.getDm().borraBoard(bo.getData().getLocation())) {
                        PvpTitles.logError("Error deleting sign board", null);
                        return;
                    }
                } else {
                    HologramsFile.removeHologram(bo.getData().getLocation());
                }

                bo.dematerialize(jugadores);
                boards.remove(bo);

                pt.getServer().getPluginManager().callEvent(new BoardEvent(pl, bo.getData().getFullLocation()));
                
                if (pl != null) {
                    pl.sendMessage(PLUGIN + LangFile.BOARD_DELETED.getText(Localizer.getLocale(pl)));
                }

                break;
            }
        }
    }

    public List<Board> getBoards() {
        return boards;
    }

    public void vaciar() {
        this.boards.clear();
    }
}
