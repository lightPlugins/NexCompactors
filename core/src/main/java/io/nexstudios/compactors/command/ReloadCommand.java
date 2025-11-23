package io.nexstudios.compactors.command;

import io.nexstudios.compactors.NexCompactors;
import io.nexstudios.nexus.libs.commands.BaseCommand;
import io.nexstudios.nexus.libs.commands.annotation.CommandAlias;
import io.nexstudios.nexus.libs.commands.annotation.CommandPermission;
import io.nexstudios.nexus.libs.commands.annotation.Description;
import io.nexstudios.nexus.libs.commands.annotation.Subcommand;
import org.bukkit.command.CommandSender;

@CommandAlias("nexcompactors")
public class ReloadCommand extends BaseCommand {

    @Subcommand("reload")
    @CommandPermission("nexcompactors.command.admin.reload")
    @Description("Reloads the plugin configuration and settings.")
    public void onReload(CommandSender sender) {

        NexCompactors.getInstance().onReload();
        NexCompactors.getInstance().messageSender.send(sender, "general.reload");

    }

}
