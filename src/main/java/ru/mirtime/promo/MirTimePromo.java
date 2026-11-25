package ru.mirtime.promo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimePromo extends JavaPlugin implements CommandExecutor, TabCompleter {

    private File usedFile;
    private YamlConfiguration usedConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadUsedData();

        if (getCommand("promo") != null) {
            getCommand("promo").setExecutor(this);
            getCommand("promo").setTabCompleter(this);
        }
        getLogger().info("MirTimePromo v1.0.0 (Гибкий config.yml + защита от твинков) успешно запущен!");
    }

    private void loadUsedData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        usedFile = new File(getDataFolder(), "used.yml");
        if (!usedFile.exists()) {
            try { usedFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        usedConfig = YamlConfiguration.loadConfiguration(usedFile);
    }

    private void saveUsedData() {
        if (usedConfig == null || usedFile == null) return;
        try { usedConfig.save(usedFile); } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // Админ-команда: перезагрузка config.yml без рестарта сервера
        if (sub.equals("reload")) {
            if (!sender.hasPermission("mirtime.promo.admin")) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cУ вас нет прав!"));
                return true;
            }
            reloadConfig();
            loadUsedData();
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &#00FF66Конфигурация config.yml успешно перезагружена!"));
            return true;
        }

        // Админ-команда: список всех промокодов из config.yml
        if (sub.equals("list")) {
            if (!sender.hasPermission("mirtime.promo.admin")) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cУ вас нет прав!"));
                return true;
            }
            ConfigurationSection promos = getConfig().getConfigurationSection("promos");
            if (promos == null || promos.getKeys(false).isEmpty()) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &7Активных промокодов пока нет."));
                return true;
            }
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lСПИСОК ПРОМОКОДОВ:"));
            for (String code : promos.getKeys(false)) {
                int max = promos.getInt(code + ".max-uses", -1);
                int used = usedConfig.getStringList("used." + code + ".uuids").size();
                String limitStr = (max < 0) ? "&#00FF66∞ (безлимит)" : "&#00E5FF" + used + "&8/&f" + max;
                String disp = promos.getString(code + ".display", code);
                sender.sendMessage(color(" &8▪ &#00FF66&l" + code + " &8(" + limitStr + "&8) &7— " + disp));
            }
            return true;
        }

        // Админ-команда: создать промокод прямо в игре (/promo create <код> <лимит> <команда>)
        if (sub.equals("create")) {
            if (!sender.hasPermission("mirtime.promo.admin")) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cУ вас нет прав!"));
                return true;
            }
            if (args.length < 4) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &fИспользование: &#00FF66/promo create <код> <лимит (-1 безлимит)> <команда>"));
                return true;
            }
            String code = args[1].toUpperCase();
            int maxUses;
            try {
                maxUses = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cЛимит должен быть числом (например: 50 или -1)!"));
                return true;
            }
            String rewardCmd = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            getConfig().set("promos." + code + ".max-uses", maxUses);
            getConfig().set("promos." + code + ".display", "&#00E5FFПромокод " + code);
            getConfig().set("promos." + code + ".commands", List.of(rewardCmd));
            saveConfig();
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &#00FF66Промокод &l" + code + " &#00FF66создан и сохранён в config.yml!"));
            return true;
        }

        // Админ-команда: добавить ещё одну награду к существующему промокоду (/promo addcmd <код> <команда>)
        if (sub.equals("addcmd")) {
            if (!sender.hasPermission("mirtime.promo.admin")) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cУ вас нет прав!"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &fИспользование: &#00FF66/promo addcmd <код> <команда>"));
                return true;
            }
            String code = findPromoKey(args[1]);
            if (code == null) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cПромокод не найден в config.yml!"));
                return true;
            }
            String rewardCmd = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            List<String> cmds = new ArrayList<>(getConfig().getStringList("promos." + code + ".commands"));
            cmds.add(rewardCmd);
            getConfig().set("promos." + code + ".commands", cmds);
            saveConfig();
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &#00FF66Команда добавлена в промокод &l" + code + "&#00FF66!"));
            return true;
        }

        // Админ-команда: удалить промокод (/promo delete <код>)
        if (sub.equals("delete") || sub.equals("remove")) {
            if (!sender.hasPermission("mirtime.promo.admin")) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cУ вас нет прав!"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &fИспользование: &#00FF66/promo delete <код>"));
                return true;
            }
            String code = findPromoKey(args[1]);
            if (code == null) {
                sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cПромокод не найден!"));
                return true;
            }
            getConfig().set("promos." + code, null);
            saveConfig();
            sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &eПромокод &l" + code + " &eудалён из config.yml."));
            return true;
        }
        // Активация промокода игроком: /promo <код>
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Эту команду может использовать только игрок!");
            return true;
        }

        String code = findPromoKey(args[0]);
        if (code == null) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cТакого промокода не существует или его срок истёк!"));
            return true;
        }

        String path = "promos." + code;
        int maxUses = getConfig().getInt(path + ".max-uses", -1);
        String display = getConfig().getString(path + ".display", code);
        List<String> commands = getConfig().getStringList(path + ".commands");

        // 1. Проверка: активировал ли этот игрок (UUID) данный промокод ранее
        List<String> usedUuids = new ArrayList<>(usedConfig.getStringList("used." + code + ".uuids"));
        String uuidStr = p.getUniqueId().toString();
        if (usedUuids.contains(uuidStr)) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cВы уже активировали этот промокод!"));
            return true;
        }

        // 2. Проверка общего лимита активаций промокода
        if (maxUses >= 0 && usedUuids.size() >= maxUses) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cЛимит активаций этого промокода уже исчерпан!"));
            return true;
        }

        // 3. Проверка минимального отыгранного времени (защита от ботов и пустых твинков)
        int minMinutes = getConfig().getInt("settings.min-playtime-minutes", 10);
        if (minMinutes > 0 && !p.hasPermission("mirtime.promo.admin")) {
            int playedTicks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
            int playedMinutes = playedTicks / (20 * 60);
            if (playedMinutes < minMinutes) {
                int left = minMinutes - playedMinutes;
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cДля активации промокода нужно отыграть минимум &#00FF66" + minMinutes + " мин. &c(осталось: &e" + left + " мин.&c)!"));
                return true;
            }
        }

        // 4. Проверка по IP-адресу (защита от накрутки с твинков)
        int maxPerIp = getConfig().getInt("settings.max-uses-per-ip", 1);
        String ip = (p.getAddress() != null && p.getAddress().getAddress() != null)
                ? p.getAddress().getAddress().getHostAddress() : "unknown";
        String ipKey = ip.replace(".", "_").replace(":", "_");
        int ipUses = usedConfig.getInt("used." + code + ".ips." + ipKey, 0);

        if (maxPerIp > 0 && !ip.equals("unknown") && !p.hasPermission("mirtime.promo.admin")) {
            if (ipUses >= maxPerIp) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &cС вашего IP-адреса этот промокод уже был активирован!"));
                return true;
            }
        }

        // Сохраняем факт активации в used.yml
        usedUuids.add(uuidStr);
        usedConfig.set("used." + code + ".uuids", usedUuids);
        if (!ip.equals("unknown")) {
            usedConfig.set("used." + code + ".ips." + ipKey, ipUses + 1);
        }
        saveUsedData();

        // Выполняем все команды выдачи наград из config.yml
        for (String rewardCmd : commands) {
            if (rewardCmd != null && !rewardCmd.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rewardCmd.replace("%player%", p.getName()));
            }
        }

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ &8» &#00FF66&lУСПЕШНО! &fВы активировали промокод: " + display));
        return true;
    }

    private String findPromoKey(String input) {
        ConfigurationSection promos = getConfig().getConfigurationSection("promos");
        if (promos == null) return null;
        for (String key : promos.getKeys(false)) {
            if (key.equalsIgnoreCase(input)) return key;
        }
        return null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lПРОМОКОДЫ MIR-TIME:"));
        sender.sendMessage(color(" &8▪ &#00FF66/promo <код> &7— активировать промокод и получить награду"));
        if (sender.hasPermission("mirtime.promo.admin")) {
            sender.sendMessage(color(" &8▪ &#00E5FF/promo create <код> <лимит> <команда> &7— создать промокод"));
            sender.sendMessage(color(" &8▪ &#00E5FF/promo addcmd <код> <команда> &7— добавить ещё награду в код"));
            sender.sendMessage(color(" &8▪ &#00E5FF/promo delete <код> &7— удалить промокод"));
            sender.sendMessage(color(" &8▪ &#00E5FF/promo list &7— список кодов и статистика"));
            sender.sendMessage(color(" &8▪ &#00E5FF/promo reload &7— перезагрузить config.yml"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("mirtime.promo.admin")) {
            return List.of("create", "addcmd", "delete", "list", "reload");
        }
        if (args.length == 2 && sender.hasPermission("mirtime.promo.admin")
                && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("addcmd"))) {
            ConfigurationSection promos = getConfig().getConfigurationSection("promos");
            if (promos != null) return new ArrayList<>(promos.getKeys(false));
        }
        return Collections.emptyList();
    }

    public static String color(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder rep = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) rep.append('§').append(c);
            m.appendReplacement(sb, rep.toString());
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
