package dev.felnull.ttsvoice;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.felnull.ttsvoice.audio.loader.VoiceLoaderManager;
import dev.felnull.ttsvoice.tts.BotAndGuild;
import dev.felnull.ttsvoice.tts.TTSListener;
import dev.felnull.ttsvoice.tts.TTSManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SAVE_FILE = new File("./save.json");
    private static final File SERVER_CONFIG_FOLDER = new File("./server_config");
    public static final SaveData SAVE_DATA = new SaveData();
    private static final Map<Long, ServerConfig> SERVER_CONFIGS = new HashMap<>();
    private static final List<JDA> JDAs = new ArrayList<>();
    public static Config CONFIG;
    public static String VERSION;

    public static void main(String[] args) throws Exception {
        VERSION = Main.class.getPackage().getImplementationVersion();
        if (VERSION == null) VERSION = "None";

        LOGGER.info("The Ikisugi Discord TTS BOT v" + VERSION);
        LOGGER.info("--System info--");
        LOGGER.info("Java version: " + System.getProperty("java.version"));
        LOGGER.info("Java vm name: " + System.getProperty("java.vm.name"));
        LOGGER.info("Java vm version: " + System.getProperty("java.vm.version"));
        LOGGER.info("OS: " + System.getProperty("os.name"));
        LOGGER.info("Arch: " + System.getProperty("os.arch"));
        LOGGER.info("Available Processors: " + Runtime.getRuntime().availableProcessors());
        LOGGER.info("---------------");

        var configFile = new File("./config.json");

        if (configFile.exists()) {
            try (Reader reader = new BufferedReader(new FileReader(configFile))) {
                CONFIG = Config.of(GSON.fromJson(reader, JsonObject.class));
            }
            LOGGER.info("Config file was loaded");
        } else {
            LOGGER.warn("No config file, generate default config");
            CONFIG = Config.createDefault();
            try (Writer writer = new BufferedWriter(new FileWriter(configFile))) {
                GSON.toJson(CONFIG.toJson(), writer);
            }
            return;
        }

        try {
            CONFIG.check();
        } catch (Exception ex) {
            LOGGER.error("Config is incorrect: " + ex.getMessage());
            return;
        }

        LOGGER.info("Completed config check");

        if (SAVE_FILE.exists()) {
            JsonObject jo;
            try (Reader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(SAVE_FILE)))) {
                jo = GSON.fromJson(reader, JsonObject.class);
            }
            SAVE_DATA.load(jo);
            LOGGER.info("Completed load data");
        }

        if (SERVER_CONFIG_FOLDER.exists() && SERVER_CONFIG_FOLDER.isDirectory()) {
            var files = SERVER_CONFIG_FOLDER.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".json")) {
                        var name = file.getName();
                        name = name.substring(0, name.length() - ".json".length());
                        try {
                            long id = Long.parseLong(name);
                            JsonObject jo;
                            try (Reader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(file)))) {
                                jo = GSON.fromJson(reader, JsonObject.class);
                            }
                            var sc = new ServerConfig();
                            sc.load(jo);
                            synchronized (SERVER_CONFIGS) {
                                SERVER_CONFIGS.put(id, sc);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            LOGGER.info("Completed load server config");
        }

        Timer timer = new Timer();
        TimerTask saveTask = new TimerTask() {
            public void run() {
                if (SAVE_DATA.isDirty()) {
                    var jo = SAVE_DATA.save();
                    try (Writer writer = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(SAVE_FILE)))) {
                        GSON.toJson(jo, writer);
                        LOGGER.info("Completed to save data");
                    } catch (Exception ex) {
                        LOGGER.error("Failed to save data", ex);
                    }
                    SAVE_DATA.setDirty(false);
                }
                synchronized (SERVER_CONFIGS) {
                    SERVER_CONFIGS.forEach((id, config) -> {
                        if (config.isDirty()) {
                            var jo = new JsonObject();
                            config.save(jo);
                            if (!SERVER_CONFIG_FOLDER.exists() && !SERVER_CONFIG_FOLDER.mkdirs()) {
                                LOGGER.error("Failed to create server config folder");
                            }
                            try (Writer writer = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(SERVER_CONFIG_FOLDER.toPath().resolve(id + ".json").toFile())))) {
                                GSON.toJson(jo, writer);
                                LOGGER.info("Completed to server config");
                            } catch (Exception ex) {
                                LOGGER.error("Failed to server config", ex);
                            }
                            config.setDirty(false);
                        }
                    });
                }
            }
        };
        timer.scheduleAtFixedRate(saveTask, 0, 30 * 1000);

        VoiceLoaderManager.getInstance().init();

        int num = 0;
        for (String botToken : CONFIG.botTokens()) {
            var jda = JDABuilder.createDefault(botToken).addEventListeners(new TTSListener(num)).enableIntents(GatewayIntent.MESSAGE_CONTENT).build();
            JDAs.add(jda);
            num++;
        }

        var join = Commands.slash("join", "????????????BOT???VC???????????????").addOptions(new OptionData(OptionType.CHANNEL, "channel", "?????????????????????").setChannelTypes(ImmutableList.of(ChannelType.VOICE, ChannelType.STAGE)));
        var leave = Commands.slash("leave", "????????????BOT???VC????????????");
        var reconnect = Commands.slash("reconnect", "????????????BOT???VC????????????");
        var voice = Commands.slash("voice", "?????????????????????????????????")
                .addSubcommands(new SubcommandData("check", "?????????????????????????????????????????????").addOptions(new OptionData(OptionType.USER, "user", "??????????????????")))
                .addSubcommands(new SubcommandData("list", "??????????????????????????????????????????"))
                .addSubcommands(new SubcommandData("change", "????????????????????????????????????").addOptions(new OptionData(OptionType.STRING, "voice_category", "?????????????????????????????????").setAutoComplete(true).setRequired(true)).addOptions(new OptionData(OptionType.STRING, "voice_type", "???????????????????????????").setAutoComplete(true).setRequired(true)).addOptions(new OptionData(OptionType.USER, "user", "??????????????????")));
        var deny = Commands.slash("deny", "????????????????????????").addSubcommands(new SubcommandData("list", "????????????????????????")).addSubcommands(new SubcommandData("add", "???????????????????????????").addOptions(new OptionData(OptionType.USER, "user", "??????????????????").setRequired(true))).addSubcommands(new SubcommandData("remove", "???????????????????????????").addOptions(new OptionData(OptionType.USER, "user", "??????????????????").setRequired(true)));
        var inm = Commands.slash("inm", "INM??????").addOptions(new OptionData(OptionType.STRING, "search", "??????").setAutoComplete(true).setRequired(true));
        var cookie = Commands.slash("cookie", "?????????????????????").addOptions(new OptionData(OptionType.STRING, "search", "??????").setAutoComplete(true).setRequired(true));
        var config = Commands.slash("config", "??????????????????")
                .addSubcommands(new SubcommandData("need-join", "VC??????????????????????????????").addOptions(new OptionData(OptionType.BOOLEAN, "enable", "??????????????????").setRequired(true)))
                .addSubcommands(new SubcommandData("overwrite-aloud", "????????????????????????").addOptions(new OptionData(OptionType.BOOLEAN, "enable", "??????????????????").setRequired(true)))
                .addSubcommands(new SubcommandData("inm-mode", "INM?????????").addOptions(new OptionData(OptionType.BOOLEAN, "enable", "??????????????????").setRequired(true)))
                .addSubcommands(new SubcommandData("cookie-mode", "????????????????????????").addOptions(new OptionData(OptionType.BOOLEAN, "enable", "??????????????????").setRequired(true)))
                .addSubcommands(new SubcommandData("join-say-name", "VC????????????????????????????????????").addOptions(new OptionData(OptionType.BOOLEAN, "enable", "??????????????????").setRequired(true)))
                .addSubcommands(new SubcommandData("read-around-limit", "???????????????????????????").addOptions(new OptionData(OptionType.INTEGER, "max-count", "???????????????").setMinValue(1).setRequired(true)))
                .addSubcommands(new SubcommandData("non-reading-prefix", "???????????????????????????????????????????????????").addOptions(new OptionData(OptionType.STRING, "prefix", "?????????").setRequired(true)))
                .addSubcommands(new SubcommandData("show", "?????????????????????????????????"));
        var vnick = Commands.slash("vnick", "??????????????????????????????").addOptions(new OptionData(OptionType.STRING, "name", "??????").setRequired(true)).addOptions(new OptionData(OptionType.USER, "user", "??????????????????"));
        JDAs.forEach(jda -> jda.updateCommands().addCommands(join, leave, reconnect, voice, deny, inm, cookie, config, vnick).queue());

        TimerTask updatePresenceTask = new TimerTask() {
            public void run() {
                long ct = TTSManager.getInstance().getTTSCount();
                synchronized (JDAs) {
                    String vstr = "v" + VERSION;

                    JDAs.forEach(jda -> {
                        if (ct > 0) {
                            jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.listening(vstr + " - " + ct + "????????????????????????????????????"));
                        } else {
                            jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.playing(vstr + " - " + "??????"));
                        }
                    });
                }
            }
        };
        timer.scheduleAtFixedRate(updatePresenceTask, 1000 * 30, 1000 * 30);

        Thread reconecter = new Thread(() -> {
            try {
                Thread.sleep(1000 * 10);
            } catch (InterruptedException ignored) {
            }

            synchronized (SERVER_CONFIGS) {
                SERVER_CONFIGS.forEach((g, c) -> {
                    synchronized (JDAs) {
                        for (JDA jda : JDAs) {
                            long id = jda.getSelfUser().getIdLong();
                            var lj = c.getLastJoinChannel(id);
                            if (lj != null) {
                                try {
                                    var guild = jda.getGuildById(g);
                                    var audioManager = guild.getAudioManager();
                                    var achn = guild.getChannelById(AudioChannel.class, lj.audioChannel());
                                    if (achn == null) continue;
                                    var tch = guild.getChannelById(TextChannel.class, lj.ttsChannel());
                                    if (tch == null) continue;
                                    audioManager.openAudioConnection(achn);
                                    TTSManager.getInstance().connect(new BotAndGuild(getJDABotNumber(jda), guild.getIdLong()), tch.getIdLong(), achn.getIdLong());
                                } catch (Exception ex) {
                                    LOGGER.error("Reconnection failed", ex);
                                }
                            }
                        }
                    }
                });

                LOGGER.info("Reconnect bot");
            }

        });
        reconecter.start();
    }

    public static ServerConfig getServerConfig(long guildId) {
        ServerConfig cfg;
        synchronized (SERVER_CONFIGS) {
            cfg = SERVER_CONFIGS.computeIfAbsent(guildId, n -> new ServerConfig());
        }
        return cfg;
    }

    public static JDA getJDA(int botNumber) {
        synchronized (JDAs) {
            return JDAs.get(botNumber);
        }
    }

    public static int getJDABotNumber(JDA jda) {
        synchronized (JDAs) {
            return JDAs.indexOf(jda);
        }
    }

    public static JDA getJDAByID(long userId) {
        synchronized (JDAs) {
            return JDAs.stream().filter(n -> n.getSelfUser().getIdLong() == userId).findFirst().orElse(null);
        }
    }
}
