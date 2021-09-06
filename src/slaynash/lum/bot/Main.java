package slaynash.lum.bot;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ExceptionEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdatePendingEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateMaxMembersEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import slaynash.lum.bot.api.API;
import slaynash.lum.bot.discord.CommandManager;
import slaynash.lum.bot.discord.GuildConfigurations;
import slaynash.lum.bot.discord.JDAManager;
import slaynash.lum.bot.discord.Moderation;
import slaynash.lum.bot.discord.PrivateMessagesHandler;
import slaynash.lum.bot.discord.ReactionListener;
import slaynash.lum.bot.discord.ServerMessagesHandler;
import slaynash.lum.bot.discord.VRCApiVersionScanner;
import slaynash.lum.bot.discord.VerifyPair;
import slaynash.lum.bot.discord.commands.Slash;
import slaynash.lum.bot.discord.melonscanner.MLHashPair;
import slaynash.lum.bot.discord.melonscanner.MelonScanner;
import slaynash.lum.bot.log.LogSystem;
import slaynash.lum.bot.steam.Steam;
import slaynash.lum.bot.utils.ExceptionUtils;


public class Main extends ListenerAdapter {
    public static boolean isShuttingDown = false;

    public static void main(String[] args) throws LoginException, IllegalArgumentException, InterruptedException {
        System.out.println("Starting Lum...");
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> ExceptionUtils.reportException("Exception in thread " + thread.getName() + ":", throwable));
        LogSystem.init();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            isShuttingDown = true;

            JDA jda = JDAManager.getJDA();
            if (jda != null && jda.getSelfUser().getIdLong() == 275759980752273418L) // Lum (blue)
            jda
                .getGuildById(633588473433030666L)
                .getTextChannelById(808076226064941086L)
                .sendMessageEmbeds(JDAManager.wrapMessageInEmbed("Lum is shutting down", Color.orange))
                .complete();

            MelonScanner.shutdown();
        }));

        ConfigManager.init();
        Localization.init();

        DBConnectionManagerShortUrls.init();

        new Steam().start();

        loadLogchannelList();
        loadVerifychannelList();
        loadReactionsList();
        loadScreeningRolesList();
        loadMelonLoaderVersions();
        loadMLHashes();
        loadMLVRCHash();
        loadMLReportChannels();
        loadVRCBuild();
        loadGuildConfigs();
        loadAPChannels();
        loadReplies();

        API.start();

        MelonScanner.init();

        CommandManager.init();
        JDAManager.init(ConfigManager.discordToken);

        JDAManager.getJDA().getPresence().setActivity(Activity.watching("melons getting loaded"));

        if (JDAManager.getJDA().getSelfUser().getIdLong() == 275759980752273418L) // Lum (blue)
            JDAManager.getJDA()
                .getGuildById(633588473433030666L)
                .getTextChannelById(808076226064941086L)
                .sendMessageEmbeds(JDAManager.wrapMessageInEmbed("Lum restarted successfully !", Color.green))
                .queue();

        VRCApiVersionScanner.init();
        UnityVersionMonitor.start();

        //registerCommands();

        System.out.println("Connected to " + JDAManager.getJDA().getGuilds().size() + " Guilds!");
        System.out.println("LUM Started!");

        scanNoRoles(600298024425619456L /* emmVRC */);
        scanNoRoles(439093693769711616L /* VRCMG */);
        scanNoRoles(663449315876012052L /* MelonLoader */);
    }

    private static void loadReactionsList() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("rolereactions.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ", 4);
                if (parts.length == 3 && parts[0].matches("^\\d+$") && parts[2].matches("^\\d+$")) {
                    CommandManager.reactionListeners.add(new ReactionListener(parts[0], parts[1], parts[2]));
                }
            }
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load RoleReactions", e);
        }
    }

    private static void loadScreeningRolesList() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("rolescreening.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2 && parts[0].matches("^\\d+$") && parts[1].matches("^\\d+$")) {
                    CommandManager.autoScreeningRoles.put(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
                }
                else System.err.println("loadScreeningRolesList is formated badly");
            }
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load ScreeningRoles", e);
        }
    }

    private static void loadMLHashes() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("mlhashes.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.equals("")) {
                    String[] parts = line.split(" ", 3);
                    if (parts[0].equals("r"))
                        CommandManager.melonLoaderHashes.add(new MLHashPair(parts[1], parts[2]));
                    else
                        CommandManager.melonLoaderAlphaHashes.add(new MLHashPair(parts[1], parts[2]));

                }
            }
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load MelonLoader Hashes", e);
        }
    }

    private static void loadMLVRCHash() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("mlvrchash.txt"));
            CommandManager.melonLoaderVRCHash = reader.readLine();
            CommandManager.melonLoaderVRCMinDate = reader.readLine();
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load VRChat Hash", e);
        }
    }

    private static void loadVRCBuild() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("vrchatbuild.txt"));
            String line = reader.readLine();
            if (line != null)
                CommandManager.vrchatBuild = line.trim();
            else
                CommandManager.vrchatBuild = "1";
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load VRChat Build infos", e);
        }
    }

    private static void loadMLReportChannels() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("mlreportchannels.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ", 4);
                if (parts.length == 2 && parts[0].matches("^\\d+$") && parts[1].matches("^\\d+$")) {
                    CommandManager.mlReportChannels.put(Long.parseLong(parts[0]), parts[1]);
                }
            }
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load MelonLoader Report Channels", e);
        }
    }

    private static void loadLogchannelList() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("logchannels.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ", 4);
                if (parts.length == 2 && parts[0].matches("^\\d+$") && parts[1].matches("^\\d+$")) {
                    CommandManager.logChannels.put(Long.parseLong(parts[0]), parts[1]);
                }
            }
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load Log Channels", e);
        }
    }

    private static void loadVerifychannelList() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("verifychannels.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ", 5);
                if (parts.length == 3 && parts[0].matches("^\\d+$") && parts[1].matches("^\\d+$") && parts[2].matches("^\\d+$")) {
                    CommandManager.verifyChannels.put(Long.parseLong(parts[0]), new VerifyPair(parts[1], parts[2]));
                }
            }
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load Verify Channels", e);
        }
    }

    private static void loadMelonLoaderVersions() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("melonloaderversions.txt"));
            MelonScanner.latestMLVersionRelease = reader.readLine().trim();
            MelonScanner.latestMLVersionAlpha = reader.readLine().trim();
            reader.close();
        }
        catch (Exception e) {
            ExceptionUtils.reportException("Failed to load MelonLoader Versions", e);
        }
    }

    private static void loadGuildConfigs() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("guildconfigurations.txt"));
            String line;
            HashMap<Long, boolean[]> tempMap = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                String[] broke = line.split(" ");
                boolean[] tempBooleans = new boolean[GuildConfigurations.ConfigurationMap.values().length];
                for (int i = 1; i < broke.length; i++)
                    tempBooleans[i - 1] = Boolean.parseBoolean(broke[i]);
                if (broke.length - 1/*starts from 2*/ < tempBooleans.length/*starts from 1*/)
                    tempBooleans[GuildConfigurations.ConfigurationMap.LOGSCAN.ordinal()] = true;
                tempMap.put(Long.parseLong(broke[0]), tempBooleans);
            }
            reader.close();
            GuildConfigurations.configurations = tempMap;
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load Guild Configs", e);
        }
    }

    private static void loadAPChannels() {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("autopublishchannels.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                CommandManager.apChannels.add(Long.parseLong(line));
            }
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load Auto Publish Channels", e);
        }
    }

    private static void loadReplies() {
        BufferedReader reader;
        Map<String, String> tempReplies = new HashMap<>();
        try {
            reader = new BufferedReader(new FileReader("replies.txt"));
            String line;
            String[] parts;
            while ((line = reader.readLine()) != null) {
                parts = line.split(",", 2);
                if (parts.length > 1) {
                    tempReplies.put(parts[0], parts[1]);
                }
                else {
                    CommandManager.guildReplies.put(Long.parseLong(parts[0]), tempReplies);
                    tempReplies = new HashMap<>();
                }
            }
            reader.close();
        }
        catch (IOException e) {
            ExceptionUtils.reportException("Failed to load Replies", e);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isFromType(ChannelType.PRIVATE)) {
            PrivateMessagesHandler.handle(event);
        }
        else {
            ServerMessagesHandler.handle(event);
        }
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (event.isFromType(ChannelType.PRIVATE)) {
            PrivateMessagesHandler.handle(event);
        }
        else {
            ServerMessagesHandler.handle(event);
        }
    }

    @Override
    public void onGuildMessageReactionAdd(@NotNull GuildMessageReactionAddEvent event) {
        //System.out.println("[" + event.getGuild().getName() + "] [#" + event.getChannel().getName() + "] " + event.getUser().getName() + " reacted with " + event.getReactionEmote().getName() + " (isEmote: " + event.getReactionEmote().isEmote() + ", is Emoji: " + EmojiUtils.containsEmoji(event.getReactionEmote().getName()) + ")");
        for (ReactionListener rl : CommandManager.reactionListeners) {
            if (event.getMessageId().equals(rl.messageId) && (event.getReactionEmote().isEmote() ? event.getReactionEmote().getEmote().getId().equals(rl.emoteId) : event.getReactionEmote().getName().equals(rl.emoteId))) {
                Role role = event.getGuild().getRoleById(rl.roleId);
                if (role != null) {
                    event.getGuild().addRoleToMember(event.getMember(), role).queue();
                    writeLogMessage(event.getGuild(), "Added role `" + role.getName() + "` to " + event.getUser().getAsMention());
                }
                return;
            }
        }
    }

    private void writeLogMessage(Guild guild, String message) {
        String channelId;
        if ((channelId = CommandManager.logChannels.get(guild.getIdLong())) != null) {
            for (TextChannel c : guild.getTextChannels()) {
                if (c.getId().equals(channelId)) {
                    c.sendMessageEmbeds(JDAManager.wrapMessageInEmbed(message, Color.gray)).queue();
                    break;
                }
            }
        }
    }

    @Override
    public void onGuildMessageReactionRemove(@NotNull GuildMessageReactionRemoveEvent event) {
        for (ReactionListener rl : CommandManager.reactionListeners) {
            if (event.getMessageId().equals(rl.messageId) && (event.getReactionEmote().isEmote() ? event.getReactionEmote().getEmote().getId().equals(rl.emoteId) : event.getReactionEmote().getName().equals(rl.emoteId))) {
                Role role = event.getGuild().getRoleById(rl.roleId);
                if (role != null) {
                    event.getGuild().removeRoleFromMember(event.getUserId(), role).queue();
                    writeLogMessage(event.getGuild(), "Removed role `" + role.getName() + "` from " + event.getUser().getAsMention());
                }
                return;
            }
        }
    }

    @Override
    public void onGuildMemberUpdatePending(GuildMemberUpdatePendingEvent event) {
        long targetRoleId = CommandManager.autoScreeningRoles.getOrDefault(event.getGuild().getIdLong(), 0L);
        if (targetRoleId > 0L) {
            Role role = event.getGuild().getRoleById(targetRoleId);
            if (role != null)
                event.getGuild().addRoleToMember(event.getMember(), role).queue();
        }
    }

    @Override
    public void onGuildVoiceJoin(@NotNull GuildVoiceJoinEvent event) {
        Moderation.voiceJoin(event);
    }

    @Override
    public void onGuildVoiceLeave(@NotNull GuildVoiceLeaveEvent event) {
        Moderation.voiceLeave(event);
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        int guildSize = JDAManager.getJDA().getGuilds().size();
        System.out.println("Joined " + event.getGuild().getName() + ", connected to " + guildSize + " guilds");
        if (guildSize % 50 == 0)
            JDAManager.getJDA().getGuildById(633588473433030666L).getTextChannelById(876466104036393060L).sendMessage("I joined my " + guildSize + "th guild <:Neko_cat_woah:851935805874110504>").queue();
        try {
            event.getGuild().getOwner().getUser().openPrivateChannel().flatMap(channel -> channel.sendMessage(
                "Thank you for using Lum!\nLum has a few features that can be enabled like the Scam Shield.\n"
                + "If you would like any of these enabled use the command `/config` or contact us in Slaynash's server <https://discord.gg/akFkAG2>")).queue(null, m -> System.out.println("Failed to open dms with guild owner"));
            event.getGuild().upsertCommand("config", "send server config buttons for this guild").setDefaultEnabled(false)
                .queueAfter(10, TimeUnit.SECONDS, g -> event.getGuild().updateCommandPrivilegesById(g.getId(), Moderation.getAdminsPrivileges(event.getGuild())).queue(null, e -> ExceptionUtils.reportException("An error has occurred on guild join:", e))); // register Guild command for newly joined server
        }
        catch (Exception e) {
            ExceptionUtils.reportException("An error has occurred on guild join:", e);
        }
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        System.out.println("Left " + event.getGuild().getName() + ", now connected to " + JDAManager.getJDA().getGuilds().size() + " guilds");
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Slash.slashRun(event);
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event) {
        Slash.buttonUpdate(event);
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        ExceptionUtils.processExceptionQueue();
    }

    private static void registerCommands() {
        Guild loopGuild = null;
        try {
            JDAManager.getJDA().setRequiredScopes("applications.commands"); // incase we use getInviteUrl(permissions)
            JDAManager.getJDA().updateCommands().addCommands(new CommandData("configs", "send server config buttons")
                .addOption(OptionType.STRING, "guild", "Enter Guild ID", true)).queue(); // Global/DM command
            for (Guild tempGuild : JDAManager.getJDA().getGuilds()) {
                loopGuild = tempGuild;
                try {
                    long cmdID = loopGuild.upsertCommand("config", "send server config buttons for this guild").setDefaultEnabled(false).complete().getIdLong(); // Guild command
                    loopGuild.updateCommandPrivilegesById(cmdID, Moderation.getAdminsPrivileges(loopGuild)).queue();
                }
                catch (Exception e) {
                    System.err.println("Failed to register slash command for: " + loopGuild.getName());
                }
            }
        }
        catch (Exception e) {
            ExceptionUtils.reportException(
                "Error registering command for " + loopGuild.getName() + " :", e);
        }
    }

    @Override
    public void onException(@NotNull ExceptionEvent event) {
        try {
            ExceptionUtils.reportException(
                "Exception while handling JDA event:", event.getCause());
        }
        catch (Exception e) {
            System.err.println("Failed to report exception:");
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildUpdateMaxMembers(GuildUpdateMaxMembersEvent event) {
        if (event.getGuild().getSystemChannel().canTalk()) {
            if (event.getNewMaxMembers() > event.getOldMaxMembers()) {
                event.getGuild().getSystemChannel().sendMessage("Yay, the max guild member has been increased to " + event.getNewMaxMembers() + " from " + event.getOldMaxMembers()).queue();
            }
            else {
                event.getGuild().getSystemChannel().sendMessage("The max guild member has been lowered to " + event.getNewMaxMembers() + " from " + event.getOldMaxMembers()).queue();
            }
        }
    }

    private static void scanNoRoles(long guild) {
        new Thread(() -> {
            JDA jda = JDAManager.getJDA();
            List<Member> noRoles = new ArrayList<>();
            noRoles.addAll(jda.getGuildById(guild).getMemberCache().asList());
            noRoles.removeIf(m -> m.getRoles().size() > 0 || m.getTimeJoined().isBefore(OffsetDateTime.now().minusWeeks(1)));
            System.out.println("Guild " + jda.getGuildById(guild).getName() + " has " + noRoles.size() + " members that haven't accepted the rules");
        }).start();
    }
}
