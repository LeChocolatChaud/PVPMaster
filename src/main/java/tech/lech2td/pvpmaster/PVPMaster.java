package tech.lech2td.pvpmaster;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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
import org.jetbrains.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class PVPMaster extends JavaPlugin implements Listener {
    @SuppressWarnings("SpellCheckingInspection")
    private MVWorldManager mvwm;

    // logic related
    private ArrayList<Player> players;
    private HashMap<String, double[]> quitPlayerIds;
    private HashMap<String, Team> teams;
    private Scoreboard scoreboard;
    private int timerTask;
    private int meetupTask;

    // tab completes
    private final String[] defaultSubcommands = new String[] {"in", "out", "help"};
    private final String[] subcommands = new String[] {"in", "out", "start", "end", "clear", "register", "unregister", "help"};

    // configs
    private int seconds = 630;
    private int teamCount = 2;

    // some flags
    private boolean running = false;
    private boolean randomTeaming = false;

    @Override
    @SuppressWarnings("SpellCheckingInspection")
    public void onEnable() {
        // Plugin startup logic

        // multiverse related
        MultiverseCore mvCore = (MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");
        assert mvCore != null;
        mvwm = mvCore.getMVWorldManager();

        // logic related inits
        players = new ArrayList<>();
        quitPlayerIds = new HashMap<>();
        teams = new HashMap<>();
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        teams.put("red", scoreboard.registerNewTeam("pvpred"));
        teams.put("blue", scoreboard.registerNewTeam("pvpblue"));
        teams.put("yellow", scoreboard.registerNewTeam("pvpyellow"));
        teams.put("green", scoreboard.registerNewTeam("pvpgreen"));

        teams.get("red").color(NamedTextColor.RED);
        teams.get("blue").color(NamedTextColor.BLUE);
        teams.get("yellow").color(NamedTextColor.YELLOW);
        teams.get("green").color(NamedTextColor.GREEN);

        // commands and listeners
        PluginCommand command = Objects.requireNonNull(Bukkit.getPluginCommand("pvpmaster"));
        command.setExecutor(this);
        command.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private CheckResult checkSender(CommandSender sender, String subcommand) {
        if (!(sender.hasPermission("pvpmaster.in") ||
                sender.hasPermission("pvpmaster.out") ||
                sender.hasPermission("pvpmaster.start") ||
                sender.hasPermission("pvpmaster.end") ||
                sender.hasPermission("pvpmaster.clear") ||
                sender.hasPermission("pvpmaster.register") ||
                sender.hasPermission("pvpmaster.unregister") ||
                sender.hasPermission("pvpmaster.help")))
            return CheckResult.NO_PERMISSION;
        switch (subcommand) {
            case "in" -> {
                if (!(sender instanceof Player)) return CheckResult.PLAYER_ONLY;
                if (!sender.hasPermission("pvpmaster.in")) return CheckResult.NO_PERMISSION;
            }
            case "out" -> {
                if (!(sender instanceof Player)) return CheckResult.PLAYER_ONLY;
                if (!sender.hasPermission("pvpmaster.out")) return CheckResult.NO_PERMISSION;
            }
            case "start" -> {
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender))
                    return CheckResult.PLAYER_AND_CONSOLE_ONLY;
                if (!sender.hasPermission("pvpmaster.start")) return CheckResult.NO_PERMISSION;
            }
            case "end" -> {
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender))
                    return CheckResult.PLAYER_AND_CONSOLE_ONLY;
                if (!sender.hasPermission("pvpmaster.end")) return CheckResult.NO_PERMISSION;
            }
            case "clear" -> {
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender))
                    return CheckResult.PLAYER_AND_CONSOLE_ONLY;
                if (!sender.hasPermission("pvpmaster.clear")) return CheckResult.NO_PERMISSION;
            }
            case "register" -> {
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender))
                    return CheckResult.PLAYER_AND_CONSOLE_ONLY;
                if (!sender.hasPermission("pvpmaster.register")) return CheckResult.NO_PERMISSION;
            }
            case "unregister" -> {
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender))
                    return CheckResult.PLAYER_AND_CONSOLE_ONLY;
                if (!sender.hasPermission("pvpmaster.unregister")) return CheckResult.NO_PERMISSION;
            }
            case "help" -> {
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender))
                    return CheckResult.PLAYER_AND_CONSOLE_ONLY;
                if (!sender.hasPermission("pvpmaster.help")) return CheckResult.NO_PERMISSION;
            }
            case "" -> {
                if (!(sender instanceof Player || sender instanceof ConsoleCommandSender))
                    return CheckResult.PLAYER_AND_CONSOLE_ONLY;
            }
            default -> throw new IllegalArgumentException("Bad subcommand.");
        }
        return CheckResult.SUCCESS;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equals("pvpmaster")) return true;
        switch (checkSender(sender, args.length == 0 ? "" : args[0])) {
            case PLAYER_ONLY -> {
                sender.sendMessage(
                        Component.text("This command can only be performed by players!", NamedTextColor.RED)
                );
                return true;
            }
            case PLAYER_AND_CONSOLE_ONLY -> {
                sender.sendMessage(
                        Component.text("This command can only be performed by players and the console!", NamedTextColor.RED)
                );
                return true;
            }
            case NO_PERMISSION -> {
                sender.sendMessage(
                        Component.text("You don't have the permission to perform this command!", NamedTextColor.RED)
                );
                return true;
            }
        }
        if (args.length == 0) {
            showHelp(sender, label);
            return true;
        }
        switch (args[0]) {
            case "in" -> {
                if (args.length > 2) {
                    sender.sendMessage(
                            Component.text("Bad arguments. Usage: /" + label + " in [team]", NamedTextColor.RED)
                    );
                    return true;
                }
                Player p = (Player) sender;
                if (players.contains(p)) {
                    sender.sendMessage(
                            Component.text("You have already signed up for the game!", NamedTextColor.RED)
                    );
                    return true;
                }
                if (args.length == 2) {
                    if (teams.containsKey(args[1])) {
                        teams.get(args[1]).addPlayer(p);
                    } else {
                        sender.sendMessage(
                                Component.text("No team named " + args[1] + " exists!", NamedTextColor.YELLOW)
                        );
                        return true;
                    }
                }
                players.add(p);
                sender.sendMessage(
                        Component.text("You will participate in the game!", NamedTextColor.GREEN)
                );
            }
            case "out" -> {
                if (args.length != 1) {
                    sender.sendMessage(
                            Component.text("Bad arguments. Usage: /" + label + " out", NamedTextColor.RED)
                    );
                    return true;
                }
                Player p = (Player) sender;
                if (!players.contains(p)) {
                    sender.sendMessage(
                            Component.text("You haven't signed up for the game yet!", NamedTextColor.RED)
                    );
                    return true;
                }
                for (Team team : teams.values()) {
                    team.removePlayer(p);
                }
                players.remove(p);
                sender.sendMessage(
                        Component.text("You will not participate in the game!", NamedTextColor.GREEN)
                );
            }
            case "start" -> {
                if (args.length > 3) {
                    sender.sendMessage(
                            Component.text("Bad arguments. Usage: /" + label + " start [seconds] [teamCount]", NamedTextColor.RED)
                    );
                    return true;
                }
                switch (args.length) {
                    case 3:
                        teamCount = Integer.parseInt(args[2]);
                        if (teamCount < 2 || teamCount > 15) {
                            sender.sendMessage(
                                    Component.text("Invalid team count. 2-15 (inclusive) is acceptable.", NamedTextColor.RED)
                            );
                            return true;
                        }
                        if (players.size() < teamCount) {
                            sender.sendMessage(
                                    Component.text("There are more teams than players!", NamedTextColor.RED)
                            );
                            return true;
                        }
                        randomTeaming = true;
                    case 2:
                        seconds = Integer.parseInt(args[1]);
                }
                running = true;
                if (mvwm.isMVWorld("pvp")) {
                    if (mvwm.getUnloadedWorlds().contains("pvp")) mvwm.loadWorld("pvp");
                    Bukkit.getScheduler().runTaskLater(this, this::startGame, 30*20L);
                } else {
                    mvwm.addWorld("pvp", World.Environment.NORMAL, null, WorldType.NORMAL, true, null);
                }
            }
            // end a game
            case "end" -> {
                if (args.length != 1) {
                    sender.sendMessage(
                            Component.text("Bad arguments. Usage: /" + label + " end", NamedTextColor.RED)
                    );
                    return true;
                }
                // success
                running = false;

                // free up the resources
                Bukkit.getScheduler().cancelTask(timerTask);
                Bukkit.getScheduler().cancelTask(meetupTask);
                for (Team t : teams.values()) {
                    for (Player p: players) {
                        t.removePlayer(p);
                    }
                }
                mvwm.deleteWorld("pvp", true, true);
                for (Player p : players) {
                    p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    p.getInventory().clear();
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancement revoke @a everything");
                quitPlayerIds.clear();
            }
            // clear player list
            case "clear" -> {
                if (args.length != 1) {
                    sender.sendMessage(
                            Component.text("Bad arguments. Usage: /" + label + " clear", NamedTextColor.RED)
                    );
                    return true;
                }
                // success
                for (Team t : teams.values()) {
                    for (Player p: players) {
                        t.removePlayer(p);
                    }
                }
                players.clear();
                sender.sendMessage(
                        Component.text("The player list has been cleared.", NamedTextColor.GREEN)
                );
            }
            case "register" -> {
                if (args.length != 3) {
                    sender.sendMessage(
                            Component.text("Bad arguments. Usage: /" + label + " register <name> <color>", NamedTextColor.RED)
                    );
                    return true;
                }
                if (teams.containsKey(args[1])) {
                    sender.sendMessage(
                            Component.text("A team of this name already exists!", NamedTextColor.RED)
                    );
                    return true;
                }
                teams.put(args[1], scoreboard.registerNewTeam("pvp"+args[1]));
                teams.get(args[1]).color(NamedTextColor.NAMES.value(args[2]));
            }
            case "unregister" -> {
                if (args.length != 2) {
                    sender.sendMessage(
                            Component.text("Bad arguments. Usage: /" + label + " unregister <name>", NamedTextColor.RED)
                    );
                    return true;
                }
                if (!teams.containsKey(args[1])) {
                    sender.sendMessage(
                            Component.text("A team of this name doesn't exist!", NamedTextColor.RED)
                    );
                    return true;
                }
                teams.get(args[1]).unregister();
                teams.remove(args[1]);
            }
            case "help" -> {
                switch (args.length) {
                    case 1 -> showHelp(sender, label);
                    case 2 -> showHelp(sender, label, args[1]);
                }
            }
        }
        return true;
    }

    private void showHelp(@NotNull CommandSender sender, @NotNull String label) {
        if (sender instanceof Player) {
            StringBuilder sb = new StringBuilder("Usage: /");
            sb.append(label);
            sb.append(" (");
            if (sender.hasPermission("pvpmaster.in")) sb.append("in|");
            if (sender.hasPermission("pvpmaster.out")) sb.append("out|");
            if (sender.hasPermission("pvpmaster.start")) sb.append("start|");
            if (sender.hasPermission("pvpmaster.end")) sb.append("end|");
            if (sender.hasPermission("pvpmaster.clear")) sb.append("clear|");
            if (sender.hasPermission("pvpmaster.register")) sb.append("register|");
            if (sender.hasPermission("pvpmaster.unregister")) sb.append("unregister|");
            if (sender.hasPermission("pvpmaster.help")) sb.append("help|");
            sb.deleteCharAt(sb.length() - 1);
            sb.append(")");
            sender.sendMessage(
                    Component.text(sb.toString(), NamedTextColor.YELLOW)
            );
        } else {
            sender.sendMessage(
                    Component.text("Usage: /" + label + " (start|end|clear|register|unregister|help)", NamedTextColor.YELLOW)
            );
        }
    }

    private void showHelp(@NotNull CommandSender sender, @NotNull String label, @NotNull String subcommand) {
        switch (subcommand) {
            case "in" -> {
                if (!(sender instanceof Player)) return;
                sender.sendMessage(
                        Component.text("Usage: /" + label + " in [team]", NamedTextColor.YELLOW)
                );
            }
            case "out" -> {
                if (!(sender instanceof Player)) return;
                sender.sendMessage(
                        Component.text("Usage: /" + label + " out", NamedTextColor.YELLOW)
                );
            }
            case "start" -> sender.sendMessage(
                    Component.text("Usage: /" + label + " start [seconds] [teamCount]", NamedTextColor.YELLOW)
            );
            case "end" -> sender.sendMessage(
                    Component.text("Usage: /" + label + " end", NamedTextColor.YELLOW)
            );
            case "clear" -> sender.sendMessage(
                    Component.text("Usage: /" + label + " clear", NamedTextColor.YELLOW)
            );
            case "register" -> sender.sendMessage(
                    Component.text("Usage: /" + label + " register <name> <color>", NamedTextColor.YELLOW)
            );
            case "unregister" -> sender.sendMessage(
                    Component.text("Usage: /" + label + " unregister <name>", NamedTextColor.YELLOW)
            );
            case "help" -> sender.sendMessage(
                    Component.text("Usage: /" + label + " help [subcommand]", NamedTextColor.YELLOW)
            );
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        // if world load finished
        if (event.getWorld().getName().equals("pvp")) {
            Bukkit.getScheduler().runTaskLater(this, this::startGame, 30*20L);
        }
    }

    private void startGame() {
        // prevent unwanted game start
        if (!running) return;

        // world settings
        MultiverseWorld world = mvwm.getMVWorld("pvp");
        world.setGameMode(GameMode.SURVIVAL);
        world.setDifficulty(Difficulty.NORMAL);

        // random teaming
        if (randomTeaming) {
            for (Team t : teams.values()) {
                for (Player p: players) {
                    t.removePlayer(p);
                }
            }
            ArrayList<Player> toBeAdded = new ArrayList<>(players);
            Random teamingRandom = new Random(ZonedDateTime.now().toEpochSecond());
            Collections.shuffle(toBeAdded, teamingRandom);
            // evenly distribute first (ignore the remainders)
            int evenlyDistribute = players.size() / teamCount;
            if (teamCount > teams.size()) {
                Iterator<NamedTextColor> colors = NamedTextColor.NAMES.values().iterator();
                for (int i = 0; i < teamCount - teams.size(); i++) {
                    NamedTextColor color = colors.next();
                    if (teams.values().stream().map(Team::color).map(TextColor::value).anyMatch(v -> v == color.value())) {
                        continue;
                    }
                    teams.put(color.toString(), scoreboard.registerNewTeam("pvp" + color));
                    teams.get(color.toString()).color(color);
                }
            }
            Iterator<Team> teamIterator = teams.values().iterator();
            int index = 0;
            for (int i = 0; i < teamCount; i++) {
                Team team = teamIterator.next();
                for (int j = 0; j < evenlyDistribute; j++) {
                    team.addPlayer(toBeAdded.get(index));
                    index++;
                }
            }
            int remainder = players.size() % teamCount;
            teamIterator = teams.values().iterator();
            for (int i = 0; i < remainder; i++) {
                teamIterator.next().addPlayer(toBeAdded.get(index));
                index++;
            }
        }

        // pre-teleport preparations
        Location spawnLoc = world.getSpawnLocation();
        int spawnX = spawnLoc.getBlockX();
        int spawnZ = spawnLoc.getBlockZ();

        // actual teleporting scripts
        ArrayList<CompletableFuture<Void>> teamFutures = new ArrayList<>();
        for (Team team : teams.values()) {
            Random teamRandom = new Random(ZonedDateTime.now().toEpochSecond()); // team random
            int teamX = getOffsetInt(teamRandom, spawnX, 100, 800);           // team center
            int teamZ = getOffsetInt(teamRandom, spawnZ, 100, 800);
            teamFutures.add(spreadTeamPlayers(world, team, teamRandom, teamX, teamZ)); // spread team players from the team center
        }

        CompletableFuture<Void> gameFuture = CompletableFuture.allOf(teamFutures.toArray(new CompletableFuture[0]));
        // check if all teams are ready
        gameFuture.whenComplete((_void, _throwable) -> {                            // start the timer after all teams are ready

            // meetup timer
            AtomicInteger countdownSeconds = new AtomicInteger(seconds);
            timerTask = (Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
                for (Player p : players) {
                    p.sendActionBar(Component.text("").color(NamedTextColor.GREEN).append(Component.text("Time before meetup: " + countdownSeconds)));
                }
                countdownSeconds.decrementAndGet();
            }, 0, 20));

            // meetup teleport task
            meetupTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                Bukkit.getScheduler().cancelTask(timerTask);                // stop timer

                // a small team & player spread
                for (Team t : teams.values()) {

                    // team center
                    Random newTeamRandom = new Random(ZonedDateTime.now().toEpochSecond());
                    int newTeamX = getOffsetInt(newTeamRandom, spawnX, 70, 100);
                    int newTeamZ = getOffsetInt(newTeamRandom, spawnZ, 70, 100);

                    for (Player p : t.getEntries().stream().map((name) -> Objects.requireNonNull(Bukkit.getPlayer(name))).toList()) {
                        // player center
                        int playerX = getOffsetInt(newTeamRandom, newTeamX, 0, 3);
                        int playerZ = getOffsetInt(newTeamRandom, newTeamZ, 0, 3);
                        final int groundY = world.getCBWorld().getHighestBlockYAt(playerX, playerZ);
                        final Location playerLoc = new Location(world.getCBWorld(), playerX, groundY + 1, playerZ);
                        world.getCBWorld().getChunkAtAsyncUrgently(playerLoc, true).whenComplete((chunk, throwable1) -> {
                            Block groundBlock = new Location(world.getCBWorld(), playerX, groundY, playerZ).getBlock();
                            if (groundBlock.isLiquid() || groundBlock.isEmpty() || groundBlock.isPassable()) {
                                groundBlock.setType(Material.STONE);
                            }
                            p.teleport(playerLoc);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 4));
                            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 1000000, 1));
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute in minecraft:pvp run spawnpoint " + p.getName() + " " + spawnLoc.getBlockX() + " " + spawnLoc.getBlockY() + " " + spawnLoc.getBlockZ());
                        }); // just for safety
                    }
                }

                // set world border to 400*400
                WorldBorder border = world.getCBWorld().getWorldBorder();
                border.setCenter(spawnX, spawnZ);
                border.setSize(400);
            }, seconds * 20L);
        });
    }

    private CompletableFuture<Void> spreadTeamPlayers(MultiverseWorld world, Team team, Random teamRandom, int teamX, int teamZ) {

        ArrayList<CompletableFuture<?>> individualFutures = new ArrayList<>();                  // for team meetup task

        for (Player p : team.getEntries().stream().map((name) -> Objects.requireNonNull(Bukkit.getPlayer(name))).toList()) {

            int playerX = getOffsetInt(teamRandom, teamX, 0, 3);                              // player center based on team center
            int playerZ = getOffsetInt(teamRandom, teamZ, 0, 3);
            final int groundY = world.getCBWorld().getHighestBlockYAt(playerX, playerZ);
            final Location playerLoc = new Location(world.getCBWorld(), playerX, groundY + 1, playerZ);

            CompletableFuture<Chunk> playerFuture = world.getCBWorld().getChunkAtAsyncUrgently(playerLoc, true);        // load chunk before teleporting the player
            individualFutures.add(playerFuture);

            playerFuture.whenComplete((chunk, throwable) -> {                                   // once chunk is loaded

                Block groundBlock = new Location(world.getCBWorld(), playerX, groundY, playerZ).getBlock();
                if (groundBlock.isLiquid() || groundBlock.isEmpty() || groundBlock.isPassable()) {
                    groundBlock.setType(Material.STONE);
                }

                p.teleport(playerLoc);                                                          // teleport him!
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute in minecraft:pvp run spawnpoint " + p.getName() + " " + playerLoc.getBlockX() + " " + playerLoc.getBlockY() + " " + playerLoc.getBlockZ());

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

        // if all team members have completed their task, team task will trigger
        return CompletableFuture.allOf(individualFutures.toArray(new CompletableFuture[team.getSize()]));
    }

    private int getOffsetInt(Random random, int original, int start, int end) { // easy to understand, no comments
        int offset = random.nextInt(start, end);
        boolean minus = random.nextBoolean();
        if (minus) {
            return original - offset;
        } else {
            return original + offset;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        ArrayList<String> emptyList = new ArrayList<>();
        if (!command.getName().equals("pvpmaster")) return null;
        return switch (args.length) {
            case 1 ->
                sender.hasPermission("pvpmaster.admin") ?
                        Arrays.stream(subcommands).sorted().filter(new StartsWithPredicate(args[0])).toList() :
                        Arrays.stream(defaultSubcommands).sorted().filter(new StartsWithPredicate(args[0])).toList();
            case 2 ->
                switch (args[0]) {
                    case "in", "unregister" -> teams.keySet().stream().sorted().filter(new StartsWithPredicate(args[1])).toList();
                    case "help" -> Arrays.stream(subcommands).sorted().filter(new StartsWithPredicate(args[1])).toList();
                    default -> emptyList;
                };
            case 3 ->
                args[0].equals("register") ? NamedTextColor.NAMES.keys().stream().sorted().filter(new StartsWithPredicate(args[2])).toList() : emptyList;
            default -> emptyList;
        };
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!running) return;
        if (!event.getCause().equals(PlayerTeleportEvent.TeleportCause.COMMAND)) return;
        Player sender = event.getPlayer();
        ArrayList<Player> teleportToPlayers = new ArrayList<>();
        Location teleportToLocation = event.getTo();
        for (Player p : players) {
            if (p.getBoundingBox().contains(teleportToLocation.toVector()))
                teleportToPlayers.add(p);
        }
        if (!players.contains(event.getPlayer())) {
            if (teleportToPlayers.size() == 0) return;
            sender.sendMessage(
                    Component.text("You can't teleport to a player in the game!", NamedTextColor.RED)
            );
            event.setCancelled(true);
            return;
        }
        if (teleportToPlayers.size() == 0) {
            sender.sendMessage(
                    Component.text("You can't teleport to a player out of the game!", NamedTextColor.RED)
            );
            event.setCancelled(true);
            return;
        }
        boolean canTeleport = false;
        for (Player p: teleportToPlayers) {
            canTeleport = canTeleport || Objects.equals(scoreboard.getPlayerTeam(p), scoreboard.getPlayerTeam(sender));
        }
        if (!canTeleport) {
            sender.sendMessage(
                    Component.text("You can't teleport to a player out of your team!", NamedTextColor.RED)
            );
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!running) return;
        Player player = event.getPlayer();
        if (players.contains(player)) {
            players.remove(player);
            double[] locArray = {
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ(),
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
            };
            quitPlayerIds.put(player.getUniqueId().toString(), locArray);
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!running) return;
        Player player = event.getPlayer();
        if (quitPlayerIds.containsKey(player.getUniqueId().toString())) {
            double[] locArray = quitPlayerIds.get(player.getUniqueId().toString());
            Location loc = new Location(
                    mvwm.getMVWorld("pvp").getCBWorld(),
                    locArray[0],
                    locArray[1],
                    locArray[2],
                    (float) locArray[3],
                    (float) locArray[4]
            );
            player.teleport(loc);
            quitPlayerIds.remove(player.getUniqueId().toString());
            players.add(player);
            player.setScoreboard(scoreboard);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        // free up resources
        HandlerList.unregisterAll((Plugin) this);
    }

    private enum CheckResult {
        SUCCESS,
        NO_PERMISSION,
        PLAYER_ONLY,
        PLAYER_AND_CONSOLE_ONLY
    }

    private static class StartsWithPredicate implements Predicate<String> {
        String prefix;

        public StartsWithPredicate(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean test(String s) {
            return s.startsWith(prefix);
        }
    }
}
