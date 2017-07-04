/*
 * Copyright (C) 2017 AlternaCraft
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
package com.alternacraft.pvptitles.Main;

import com.alternacraft.pvptitles.Backend.ConfigDataStore;
import com.alternacraft.pvptitles.Exceptions.DBException;
import com.alternacraft.pvptitles.Exceptions.RanksException;
import com.alternacraft.pvptitles.Files.LangsFile;
import com.alternacraft.pvptitles.Files.LangsFile.LangType;
import com.alternacraft.pvptitles.Files.ModelsFile;
import com.alternacraft.pvptitles.Files.RewardsFile;
import com.alternacraft.pvptitles.Files.ServersFile;
import com.alternacraft.pvptitles.Files.TemplatesFile;
import com.alternacraft.pvptitles.Hooks.HolographicHook;
import static com.alternacraft.pvptitles.Main.CustomLogger.showMessage;
import static com.alternacraft.pvptitles.Main.DBLoader.tipo;
import static com.alternacraft.pvptitles.Main.PvpTitles.getPluginName;
import com.alternacraft.pvptitles.Managers.BoardsAPI.BoardData;
import com.alternacraft.pvptitles.Managers.BoardsAPI.BoardModel;
import com.alternacraft.pvptitles.Managers.BoardsAPI.ModelController;
import com.alternacraft.pvptitles.Managers.BoardsCustom.SignBoard;
import com.alternacraft.pvptitles.Managers.BoardsCustom.SignBoardData;
import static com.alternacraft.pvptitles.Managers.CleanTaskManager.TICKS;
import com.alternacraft.pvptitles.Managers.LeaderBoardManager;
import com.alternacraft.pvptitles.Managers.MovementManager;
import com.alternacraft.pvptitles.Managers.RankManager;
import com.alternacraft.pvptitles.Managers.TimerManager;
import com.alternacraft.pvptitles.Misc.Localizer;
import com.alternacraft.pvptitles.Misc.Rank;
import com.alternacraft.pvptitles.Misc.TimedPlayer;
import com.alternacraft.pvptitles.RetroCP.DBChecker;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class Manager {

    // <editor-fold defaultstate="collapsed" desc="VARIABLES AND CONSTRUCTOR + INSTANCE...">    
    // Variable que almacena el plugin
    private PvpTitles pvpTitles = null;
    // Instancia de la clase
    private static final Manager INSTANCE = new Manager();

    // Handlers
    public ConfigLoader ch = null;
    public DBLoader dbh = null;

    // Timers
    private MovementManager movementManager = null;
    private TimerManager timerManager = null;

    // Gestor de leaderboards
    private LeaderBoardManager lbm = null;

    // Modelos
    public ArrayList<BoardModel> modelos = null;
    // Recompensas - (Condicion => [(valor_condicion => (n_accion => accion))])
    public Map<String, List<Map<String, Map<String, Object>>>> rewards = null;
    // Templates
    public TemplatesFile templates = null;
    // Servers
    public HashMap<String, HashMap<Short, List<String>>> servers = null;
    // Configuracion
    public ConfigDataStore params = null;

    // Chat
    public static LangType messages = null;

    // Entero con el numero del evento
    private int eventoActualizador = -1;
    // Entero con el numero del evento
    private int eventoChecker = -1;

    /**
     * Contructor de la clase
     */
    private Manager() {
        modelos = new ArrayList();
        rewards = new HashMap();
        servers = new HashMap();
    }

    /**
     * Instancia de la clase
     * <p>
     * De esta forma evito que se creen diferentes objetos de la clase</p>
     *
     * @return Instancia de la clase
     */
    public static Manager getInstance() {
        return INSTANCE;
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="SETUP...">
    /**
     * Método para cargar el config principal
     *
     * @param plugin Objeto de la clase principal
     * @return boolean
     */
    public boolean setup(PvpTitles plugin) {
        this.pvpTitles = plugin;

        this.ch = new ConfigLoader(plugin);
        this.reloadConfig();
        
        this.lbm = new LeaderBoardManager(plugin);

        // Registro los managers del timing
        this.timerManager = new TimerManager(plugin);
        this.movementManager = new MovementManager(plugin);
        this.movementManager.updateTimeAFK();

        this.dbh = new DBLoader(plugin, this.ch.getConfig());
        this.dbh.selectDB();

        // RCP
        if (!new DBChecker(plugin).setup()) {
            return false;
        }

        this.loadLang();
        this.loadModels();
        this.loadSavedBoards();
        this.loadRewards();
        this.loadTemplates();

        if (tipo == DBLoader.DBTYPE.MYSQL) {
            this.loadServers();
        }

        this.loadBoardUpdater();
        this.loadRankTimeChecker();

        return true;
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="LOADING PLUGIN BASE...">   
    /**
     * Método para cargar los modelos del bloc de notas
     */
    public void reloadConfig() {
        this.params = this.ch.loadConfig();
    }
    
    public void loadModels() {
        ModelsFile contenido = new ModelsFile();

        String fichero = new StringBuilder().append(
                this.pvpTitles.getDataFolder()).append( // Ruta
                File.separator).append( // Separador
                        "models.txt").toString();

        try {
            modelos = contenido.readFile(fichero);
        } catch (IOException ex) {
            modelos = contenido.makeFile(this.pvpTitles);
        }

        showMessage(ChatColor.YELLOW + "" + modelos.size() + " models " + ChatColor.AQUA + "loaded correctly.");
    }

    /**
     * Método para guardar en memoria los scoreboards
     */
    public void loadSavedBoards() {
        List<SignBoardData> carteles = new ArrayList<>();
        try {
            carteles = pvpTitles.getManager().dbh.getDm().findBoards();
        } catch (DBException ex) {
            CustomLogger.logArrayError(ex.getCustomStackTrace());
        }

        lbm.vaciar(); // Evito duplicados

        // Signs
        for (BoardData cartel : carteles) {
            BoardModel bm = searchModel(cartel.getModelo());

            if (bm == null) {
                try {
                    pvpTitles.getManager().dbh.getDm().deleteBoard(cartel.getLocation());
                    showMessage(ChatColor.RED + "Sign '" + cartel.getNombre()
                            + "' removed because the model has not been found...");
                } catch (DBException ex) {
                    CustomLogger.logArrayError(ex.getCustomStackTrace());
                }

                continue;
            }

            ModelController mc = new ModelController();
            mc.preprocessUnit(bm.getParams());

            SignBoard cs = new SignBoard(cartel, bm, mc);

            cs.setLineas(new String[0]);
            cs.setMatSign(((SignBoardData) cartel).getSignMaterial());

            lbm.loadBoard(cs);
        }

        // Holograms
        if (HolographicHook.ISHDENABLED) {
            HolographicHook.loadHoloBoards();
        }

        showMessage(ChatColor.YELLOW + "" + this.lbm.getBoards().size()
                + " scoreboards " + ((HolographicHook.ISHDENABLED) ? "" : "per signs ")
                + ChatColor.AQUA + "loaded correctly."
        );
    }

    /**
     * Método para buscar un modelo de tabla de puntuaciones
     *
     * @param modelo String con el modelo a buscar
     * @return BoardModel con los datos del modelo
     */
    public BoardModel searchModel(String modelo) {
        for (BoardModel smc : modelos) {
            if (smc.getNombre().compareToIgnoreCase(modelo) == 0) {
                return smc;
            }
        }
        return null;
    }

    /**
     * Método para cargar los locales
     */
    public void loadLang() {
        File oldMessages = new File(PvpTitles.PLUGIN_DIR, "messages.yml");
        File newMessages = new File(PvpTitles.PLUGIN_DIR, "messages_old.yml");

        if (oldMessages.exists()) {
            oldMessages.renameTo(newMessages);
        }

        LangsFile.load();

        showMessage(ChatColor.YELLOW + "Locales " + ChatColor.AQUA + "loaded correctly.");
    }

    /**
     * Método para cargar el sistema de recompensas
     */
    public void loadRewards() {
        rewards = new HashMap();

        YamlConfiguration lp = new RewardsFile().load();

        List<String> activos = lp.getStringList("activeRewards");
        List<String> types = new ArrayList(
                Arrays.asList("onRank", "onFame", "onKillstreak", "onKill")
        );

        activos.forEach(reward -> {
            boolean nulos = true; // No entro en ninguno ergo es en onkill

            for (String type : types) {
                Map data = new HashMap();

                String value = lp.getString("Rewards." + reward + "." + type);

                if (value != null || (type.equals("onKill") && nulos)) {
                    nulos = false;

                    if (!rewards.containsKey(type)) {
                        rewards.put(type, new ArrayList());
                    }

                    HashMap<String, Map<String, Object>> actions = new HashMap();

                    // Valores de la recompensa
                    if (lp.contains("Rewards." + reward + ".money")) {
                        data.put("money", lp.getDouble("Rewards." + reward + ".money"));
                    }
                    if (lp.contains("Rewards." + reward + ".points")) {
                        data.put("points", lp.getInt("Rewards." + reward + ".points"));
                    }
                    if (lp.contains("Rewards." + reward + ".time")) {
                        data.put("points", lp.getLong("Rewards." + reward + ".time"));
                    }
                    // Permiso para utilizarla
                    if (lp.contains("Rewards." + reward + ".permission")
                            && lp.getBoolean("Rewards." + reward + ".permission")) {
                        data.put("permission", "pvptitles.rw." + reward);
                    }

                    data.put("commands", lp.getStringList("Rewards." + reward + ".command"));
                    
                    // Guardo en el mapa principal los valores para ese valor                    
                    actions.put(value, data);
                    rewards.get(type).add(actions);
                }
            }
        });

        showMessage(ChatColor.YELLOW + "" + activos.size() + " rewards " + ChatColor.AQUA + "loaded correctly.");
    }

    public void loadTemplates() {
        this.templates = new TemplatesFile();
        templates.load();
    }

    /**
     * Método para cargar los locales
     */
    public void loadServers() {
        YamlConfiguration sf = new ServersFile().load();

        Set<String> sfk = sf.getKeys(false);

        for (String srv : sfk) {
            // Fix para evitar campos innecesarios
            if (srv.equals("Worlds")) {
                break;
            }

            List<Short> serverIDs = sf.getShortList(srv);

            HashMap<Short, List<String>> server = new HashMap();

            serverIDs.forEach(serverID -> {
                if (sf.get("Worlds." + srv + "." + serverID) != null) {
                    server.put(serverID, sf.getStringList("Worlds." + srv + "." + serverID));
                } else if (sf.get("Worlds." + serverID) != null) {
                    server.put(serverID, sf.getStringList("Worlds." + serverID));
                } else {
                    server.put(serverID, new ArrayList());
                }
            });

            servers.put(srv, server);
        }

        showMessage(ChatColor.YELLOW + "" + servers.size() + " servers combined "
                + ChatColor.AQUA + "loaded correctly.");
    }

    /**
     * Método para ejecutar el evento que actualiza los carteles
     */
    public void loadBoardUpdater() {
        // Elimino el evento en caso de que estuviera ya creado
        if (this.eventoActualizador != -1) {
            pvpTitles.getServer().getScheduler().cancelTask(eventoActualizador);
        }

        // Optimizador
        if (this.params.getLBRefresh() == -1) {
            return;
        }

        this.eventoActualizador = pvpTitles.getServer().getScheduler().scheduleSyncRepeatingTask(pvpTitles, 
                getLbm()::updateBoards, TICKS * 5L, TICKS * (this.params.getLBRefresh() * 60L));

        showMessage(ChatColor.YELLOW + "Refresh event [" + this.params.getLBRefresh()
                + " min]" + ChatColor.AQUA + " loaded correctly."
        );
    }

    /**
     * Método para comprobar el rango según el tiempo
     */
    public void loadRankTimeChecker() {
        // Elimino el evento en caso de que estuviera ya creado
        if (this.eventoChecker != -1) {
            pvpTitles.getServer().getScheduler().cancelTask(eventoChecker);
        }

        // Optimizador
        if (this.params.getRankChecker() == -1) {
            return;
        }

        this.eventoChecker = pvpTitles.getServer().getScheduler()
                .scheduleSyncRepeatingTask(pvpTitles, () -> {
            Set<TimedPlayer> tp = getTimerManager().getTimedPlayers();
            
            for (TimedPlayer timedPlayer : tp) {
                OfflinePlayer opl = timedPlayer.getOfflinePlayer();
                
                // Fix para evitar nullpointerexception
                if (!timedPlayer.hasSession() || !opl.isOnline()) {
                    continue;
                }
                
                Player pl = opl.getPlayer();
                
                int actualFame;
                try {
                    actualFame = dbh.getDm().loadPlayerFame(timedPlayer.getUniqueId(), null);
                } catch (DBException ex) {
                    CustomLogger.logArrayError(ex.getCustomStackTrace());
                    return;
                }
                
                long savedTimeB;
                try {
                    savedTimeB = dbh.getDm().loadPlayedTime(timedPlayer.getUniqueId());
                } catch (DBException ex) {
                    CustomLogger.logArrayError(ex.getCustomStackTrace());
                    return;
                }
                
                try {
                    long savedTimeA = savedTimeB + timedPlayer.getTotalOnline();
                    
                    Rank rankB = RankManager.getRank(actualFame, savedTimeB, opl);
                    Rank rankA = RankManager.getRank(actualFame, savedTimeA, opl);
                    // Actualizo el tiempo del jugador en el server
                    if (!rankB.similar(rankA)) {
                        try {
                            dbh.getDm().savePlayedTime(pl.getUniqueId(),
                                    timedPlayer.getTotalOnline());
                        } catch (DBException ex) {
                            CustomLogger.logArrayError(ex.getCustomStackTrace());
                            continue;
                        }
                        
                        timedPlayer.removeSessions(); // Reinicio el tiempo a cero
                        timedPlayer.startSession(); // Nueva sesion
                        
                        pl.sendMessage(getPluginName()
                                + LangsFile.PLAYER_NEW_RANK.getText(Localizer.getLocale(pl))
                                        .replace("%newRank%", rankA.getDisplay()));
                    }
                } catch (RanksException ex) {
                    CustomLogger.logArrayError(ex.getCustomStackTrace());
                }
            }
        }, TICKS * 5L /* Tiempo para prevenir fallos */, TICKS * this.params.getRankChecker());

        showMessage(ChatColor.YELLOW + "Rank Checker event [" + this.params.getRankChecker()
                + " sec]" + ChatColor.AQUA + " loaded correctly."
        );
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="SOME GETTERS...">
    /**
     * Metódo para devolver el detector de AFK's
     *
     * @return MovementManager
     */
    public MovementManager getMovementManager() {
        return this.movementManager;
    }

    /**
     * Metódo para devolver el gestor de tiempos de los jugadores
     *
     * @return TimerManager
     */
    public TimerManager getTimerManager() {
        return this.timerManager;
    }

    /**
     * Método para recibir el plugin
     *
     * @return PvpTitles object
     */
    public PvpTitles getPvpTitles() {
        return pvpTitles;
    }

    /**
     * Método para recibir el gestor de carteles
     *
     * @return LeaderBoardManager
     */
    public LeaderBoardManager getLbm() {
        return lbm;
    }

    /**
     * Método para devolver el handler del config principal
     *
     * @return ConfigHandler
     */
    public ConfigLoader getCh() {
        return ch;
    }

    /**
     * Método para devolver el handler de la base de datos
     *
     * @return DBHandler
     */
    public DBLoader getDbh() {
        return dbh;
    }
    // </editor-fold>
}
