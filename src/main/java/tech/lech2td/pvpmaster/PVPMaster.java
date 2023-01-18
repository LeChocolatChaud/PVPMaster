package tech.lech2td.pvpmaster;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class PVPMaster extends JavaPlugin implements Listener {
    // multiverse related
    private MultiverseCore mvCore;
    private MVWorldManager mvwm;

    // logic related
    private ArrayList<Player> players;
    private Scoreboard scoreboard;
    private ArrayList<Team> teams;
    private int timerTask;
    private int meetupTask;

    // constants
    private final NamedTextColor[] namedTextColors = new NamedTextColor[] {NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.YELLOW, NamedTextColor.GREEN};

    // configs
    private int seconds = 600;
    private int teamCount = 2;

    // prevent unexpected start
    private boolean running = false;

    @Override
    public void onEnable() {
        // Plugin startup logic

        // multiverse related
        mvCore = (MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");
        mvwm = mvCore.getMVWorldManager();

        // logic related inits
        players = new ArrayList<>();
        teams = new ArrayList<>();
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        // commands and listeners
        Bukkit.getPluginCommand("in").setExecutor(this);
        Bukkit.getPluginCommand("out").setExecutor(this);
        Bukkit.getPluginCommand("start").setExecutor(this);
        Bukkit.getPluginCommand("end").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            // enter for a game
            case "in":
                // fail
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be operated by players!");
                    return true;
                }
                if (!sender.hasPermission("pvpmaster.in")) {
                    sender.sendMessage(ChatColor.RED + "You don't have the permission to participate in a game!");
                    return true;
                }
                if (players.contains((Player) sender)) {
                    sender.sendMessage(ChatColor.RED + "You have already signed up for the game!");
                    return true;
                }
                // success
                players.add((Player) sender);
                sender.sendMessage(ChatColor.GREEN + "You will participate in the game!");
                break;
            // leave a game
            case "out":
                // fail
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be operated by players!");
                    return true;
                }
                if (!sender.hasPermission("pvpmaster.out")) {
                    sender.sendMessage(ChatColor.RED + "You don't have the permission to leave a game!");
                    return true;
                }
                if (!players.contains((Player) sender)) {
                    sender.sendMessage(ChatColor.RED + "You haven't signed up for the game yet!");
                    return true;
                }
                // success
                players.remove((Player) sender);
                sender.sendMessage(ChatColor.GREEN + "You will not participate in the game!");
                break;
            // start a game
            case "start":
                // fail
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be operated by players or console!");
                    return true;
                }
                if (!sender.hasPermission("pvpmaster.start")) {
                    sender.sendMessage(ChatColor.RED + "You don't have the permission to start a game!");
                    return true;
                }
                // success

                // configs
                if (args.length >= 1) {
                    seconds = Integer.parseInt(args[0]);
                }
                if (args.length >= 2) {
                    teamCount = Integer.parseInt(args[1]);
                    if (teamCount < 2 || teamCount > 4) {
                        sender.sendMessage(ChatColor.RED + "Team count is invalid! 2-4 is acceptable.");
                        return true;
                    }
                }

                // confirm to start
                running = true;

                // create world
                mvwm.addWorld("pvp", World.Environment.NORMAL, null, WorldType.NORMAL, true, null);
                break;
            // end a game
            case "end":
                // fail
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be operated by players or console!");
                    return true;
                }
                if (!sender.hasPermission("pvpmaster.end")) {
                    sender.sendMessage(ChatColor.RED + "You don't have the permission to end a game!");
                    return true;
                }
                // success
                running = false;

                // free up the resources
                Bukkit.getScheduler().cancelTask(timerTask);
                Bukkit.getScheduler().cancelTask(meetupTask);
                for (Team t: teams) {
                    t.unregister();
                }
                teams.clear();
                mvwm.deleteWorld("pvp", true, true);
                for (Player p: players) {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                break;
            // clear player list
            case "reset":
                // fail
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be operated by players or console!");
                    return true;
                }
                if (!sender.hasPermission("pvpmaster.reset")) {
                    sender.sendMessage(ChatColor.RED + "You don't have the permission to reset the player list!");
                    return true;
                }
                // success
                players.clear();
                sender.sendMessage(ChatColor.GREEN + "The player list has been cleared.");
        }
        return true;
    }

    @EventHandler
    private void onWorldLoad(WorldLoadEvent event) {
        // if world load finished
        if (event.getWorld().getName().equals("pvp")) {
            // prevent unwanted game start
            if (!running) return;

            // world settings
            MultiverseWorld world = mvwm.getMVWorld("pvp");
            world.setGameMode(GameMode.SURVIVAL);
            world.setDifficulty(Difficulty.NORMAL);

            // teaming (random)
            teams = new ArrayList<>();                      // init team array
            ArrayList<Player> toBeAdded = new ArrayList<>(players);             // copy player list
            int maxTeamSize = (int) Math.ceil(((double)toBeAdded.size()) / teamCount);      // get max team size
            Random random = new Random(ZonedDateTime.now().toEpochSecond());                // teaming random
            for (int i = 0; i < teamCount; i++) {                       // for every team:
                Team t = scoreboard.registerNewTeam("pvp" + i);             // register team
                t.color(namedTextColors[i]);                                // change color according to a given pattern
                for (int j = 0; j < Math.min(maxTeamSize, toBeAdded.size()); j++) {
                    int index = random.nextInt(toBeAdded.size());           // take a free player and add him to the team
                    t.addEntry(toBeAdded.get(index).getName());
                    toBeAdded.remove(index);                                // he's not free anymore!
                }
                teams.add(t);                                           // this team has done config, remove it.
            }

            // pre-teleport preparations
            Location spawnLoc = world.getSpawnLocation();
            int spawnX = spawnLoc.getBlockX();
            int spawnZ = spawnLoc.getBlockZ();

            // actual teleporting scripts
            ArrayList<CompletableFuture<Void>> teamFutures = new ArrayList<>();
            for (Team t: teams) {
                Random teamRandom = new Random(ZonedDateTime.now().toEpochSecond()); // team random
                int teamX = getOffsetedInt(teamRandom, spawnX, 100, 800);           // team center
                int teamZ = getOffsetedInt(teamRandom, spawnZ, 100, 800);
                teamFutures.add(spreadTeamPlayers(world, t, teamRandom, teamX, teamZ)); // spread team players from the team center
            }

            CompletableFuture<Void> gameFuture = CompletableFuture.allOf(teamFutures.toArray(new CompletableFuture[teamFutures.size()]));
                                                                                // check if all teams are ready
            gameFuture.whenComplete((void1, t1) -> {                            // start the timer after all teams are ready

                // meetup timer
                AtomicInteger countdownSeconds = new AtomicInteger(seconds);
                timerTask = (Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
                    for (Player p: players) {
                        p.sendActionBar(Component.text("").color(NamedTextColor.GREEN).append(Component.text("Time before meetup: " + countdownSeconds.getAndDecrement())));
                    }
                }, 0, 20));

                // meetup teleport task
                meetupTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                    Bukkit.getScheduler().cancelTask(timerTask);                // stop timer

                    // a small team & player spread
                    for (Team t: teams) {

                        // team center
                        Random newTeamRandom = new Random(ZonedDateTime.now().toEpochSecond());
                        int newTeamX = getOffsetedInt(newTeamRandom, spawnX, 70, 100);
                        int newTeamZ = getOffsetedInt(newTeamRandom, spawnZ, 70, 100);

                        for (Player p : t.getEntries().stream().map(Bukkit::getPlayer).toList()) {
                            // player center
                            int playerX = getOffsetedInt(newTeamRandom, newTeamX, 0, 3);
                            int playerZ = getOffsetedInt(newTeamRandom, newTeamZ, 0, 3);
                            final Location playerLoc = new Location(world.getCBWorld(), playerX, world.getCBWorld().getHighestBlockYAt(playerX, playerZ), playerZ);
                            world.getCBWorld().getChunkAtAsync(playerLoc, true).whenComplete((chunk, throwable1) -> {
                                p.teleport(playerLoc);
                                p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 4));
                            }); // just for safety
                        }
                    }
                }, seconds * 20L);
            });

        }
    }

    private CompletableFuture<Void> spreadTeamPlayers(MultiverseWorld world, Team t, Random teamRandom, int teamX, int teamZ) {

        ArrayList<CompletableFuture<?>> individualFutures = new ArrayList<>();                  // for team meetup task

        for (Player p: t.getEntries().stream().map(Bukkit::getPlayer).toList()) {

            int playerX = getOffsetedInt(teamRandom, teamX, 0, 3);                              // player center based on team center
            int playerZ = getOffsetedInt(teamRandom, teamZ, 0, 3);
            final Location playerLoc = new Location(world.getCBWorld(), playerX, world.getCBWorld().getHighestBlockYAt(playerX, playerZ), playerZ);

            CompletableFuture<Chunk> playerFuture = world.getCBWorld().getChunkAtAsync(playerLoc, true);        // load chunk before teleporting the player
            individualFutures.add(playerFuture);

            playerFuture.whenComplete((chunk, throwable) -> {                                   // once chunk is loaded

                p.teleport(playerLoc);                                                          // teleport him!

                Inventory inventory = p.getInventory();                                         // basic stuffs
                inventory.clear();
                inventory.addItem(
                        new ItemStack(Material.STONE_PICKAXE),
                        new ItemStack(Material.STONE_AXE),
                        new ItemStack(Material.STONE_SHOVEL),
                        new ItemStack(Material.COOKED_PORKCHOP, 64)
                );

                p.setScoreboard(scoreboard);                                                    // see the teammates and opponents

                p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 1200, 4));   // save him from accidents
            });
        }

        CompletableFuture<Void> teamFuture = CompletableFuture.allOf(individualFutures.toArray(new CompletableFuture[t.getSize()]));
                                                        // if all team members have completed their task, team task will trigger
        return teamFuture;
    }

    private int getOffsetedInt(Random random, int original, int start, int end) { // easy to understand, no comments
        int offset = random.nextInt(start, end);
        boolean minus = random.nextBoolean();
        if (minus) {
            return original - offset;
        } else {
            return original + offset;
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        // free up resources
        HandlerList.unregisterAll((Plugin) this);
    }
}
