package io.nexstudios.compactors.command;

import io.nexstudios.compactors.inventory.CategoryInventory;
import io.nexstudios.nexus.libs.commands.BaseCommand;
import io.nexstudios.nexus.libs.commands.annotation.CommandAlias;
import io.nexstudios.nexus.libs.commands.annotation.CommandPermission;
import io.nexstudios.nexus.libs.commands.annotation.Description;
import io.nexstudios.nexus.libs.commands.annotation.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("compactor")
public class CategoryInvCommand extends BaseCommand {


    @Subcommand("category")
    @CommandPermission("nexcompactors.command.category")
    @Description("Open the category menu")
    public void openCategory(CommandSender sender) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only Players can execute this command!");
            return;
        }

        CategoryInventory categoryInventory = new CategoryInventory();
        categoryInventory.openInventory(p);

    }
}
