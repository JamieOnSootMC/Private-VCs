package uk.jamieisgeek;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import org.jetbrains.annotations.NotNull;
import org.simpleyaml.configuration.file.YamlFile;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class PrivateVCs implements EventListener {
    private YamlFile config;
    private Map<Member, VoiceChannel> personalChannels;

    private void makeConfigFile() throws IOException {
        File folder = new File("plugins/PrivateVCs");

        if (!folder.exists()) folder.mkdir();

        File file = new File(folder.getAbsolutePath() + "/config.yml");

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        config = new YamlFile();
        config.load(file);

        if(config.get("host-vc") == null){
            config.set("host-vc", "123");
            try {
                config.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void load() throws IOException {
        System.out.println("Loading PrivateVSs...");
        this.makeConfigFile();
        this.personalChannels = new HashMap<>();
        System.out.println("PrivateVSs loaded!");
    }

    public void unload() {
        personalChannels.forEach((member, voiceChannel) -> voiceChannel.delete().complete());
        System.out.println("PrivateVSs unloaded!");
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if(event instanceof GuildVoiceJoinEvent vcJoinEvent) {
            Member member = vcJoinEvent.getMember();
            VoiceChannel channel = vcJoinEvent.getChannelJoined();

            if(!channel.getId().equals(config.getString("host-vc"))) return;

            VoiceChannel personalChannel = channel.getGuild().createVoiceChannel(member.getEffectiveName() + "'s VC")
                            .setParent(channel.getParent())
                            .addMemberPermissionOverride(member.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT), null)
                            .addRolePermissionOverride(channel.getGuild().getPublicRole().getIdLong(), null, EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT))
                            .complete();
            channel.getGuild().moveVoiceMember(member, personalChannel).queue();

            personalChannels.put(member, personalChannel);
        } else if (event instanceof GuildVoiceLeaveEvent vcLeaveEvent) {
            Member member = vcLeaveEvent.getMember();
            VoiceChannel channel = vcLeaveEvent.getChannelLeft();

            if(!personalChannels.containsKey(member)) return;

            if(channel.getId().equals(personalChannels.get(member).getId())) {
                personalChannels.get(member).delete().queue();
                personalChannels.remove(member);
            }
        } else if (event instanceof MessageReceivedEvent msgEvent) {
            if(msgEvent.getAuthor().isBot()) return;

            String[] args = msgEvent.getMessage().getContentRaw().split(" ");
            Member author = msgEvent.getMember();
            switch(args[0]) {
                case "^vcinvite" -> {
                    if(args.length < 2) {
                        msgEvent.getChannel().sendMessage("Please specify a user to invite!").queue();
                        return;
                    }

                    Member member = msgEvent.getGuild().getMemberById(args[1]);
                    if(member == null) {
                        msgEvent.getChannel().sendMessage("Invalid user!").queue();
                        return;
                    }

                    if(!author.getVoiceState().inVoiceChannel()) {
                        msgEvent.getChannel().sendMessage("You must be in a voice channel to invite someone!").queue();
                        return;
                    }

                    if(!author.getVoiceState().getChannel().getId().equals(personalChannels.get(author).getId())) {
                        msgEvent.getChannel().sendMessage("You must be in your personal voice channel to invite someone!").queue();
                        return;
                    }

                    member.getVoiceState().getChannel().createPermissionOverride(member).setAllow(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT).queue();
                    msgEvent.getChannel().sendMessage("Invited " + member.getAsMention() + " to your voice channel!").queue();
                }

                case "^vcremove" -> {
                    if(args.length < 2) {
                        msgEvent.getChannel().sendMessage("Please specify a user to remove!").queue();
                        return;
                    }

                    Member member = msgEvent.getGuild().getMemberById(args[1]);
                    if(member == null) {
                        msgEvent.getChannel().sendMessage("Invalid user!").queue();
                        return;
                    }

                    if(!author.getVoiceState().inVoiceChannel()) {
                        msgEvent.getChannel().sendMessage("You must be in a voice channel to remove someone!").queue();
                        return;
                    }

                    if(!author.getVoiceState().getChannel().getId().equals(personalChannels.get(author).getId())) {
                        msgEvent.getChannel().sendMessage("You must be in your personal voice channel to remove someone!").queue();
                        return;
                    }

                    if(!member.getVoiceState().inVoiceChannel()) {
                        msgEvent.getChannel().sendMessage("The user you specified is not in a voice channel!").queue();
                        return;
                    }

                    if(!member.getVoiceState().getChannel().getId().equals(personalChannels.get(author).getId())) {
                        msgEvent.getChannel().sendMessage("The user you specified is not in your voice channel!").queue();
                        return;
                    }

                    member.getVoiceState().getChannel().createPermissionOverride(member).setDeny(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT).queue();
                    member.getVoiceState().getChannel().getMembers().remove(member);
                    msgEvent.getChannel().sendMessage("Removed " + member.getAsMention() + " from your voice channel!").queue();
                }
            }
        }
    }
}