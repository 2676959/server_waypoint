import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
public final class AccessProbe extends JavaPlugin {
    public void onEnable(){getServer().getMessenger().registerOutgoingPluginChannel(this,"server_waypoint:server_handshake");getCommand("acceptance").setExecutor((sender,command,label,args)->{
        if(!(sender instanceof org.bukkit.command.ConsoleCommandSender)||args.length<2)return false;
        Player player=getServer().getPlayerExact(args[1]);if(player==null){sender.sendMessage("PROBE missing player");return true;}
        player.getScheduler().run(this,task->{
            if(args[0].equals("permission")){player.addAttachment(this,"server_waypoint.command."+args[2],Boolean.parseBoolean(args[3]));player.updateCommands();sender.sendMessage("PROBE permission "+player.getName()+" "+args[2]+" "+args[3]);}
            else if(args[0].equals("handshake")){player.sendPluginMessage(this,"server_waypoint:server_handshake",java.nio.ByteBuffer.allocate(9).putInt(3).putInt(Integer.parseInt(args[2])).put((byte)1).array());sender.sendMessage("PROBE late handshake "+args[2]);}
            else if(args[0].equals("position")){var p=player.getLocation();sender.sendMessage("PROBE position "+player.getName()+" "+p.getWorld().getKey()+" "+p.getX()+" "+p.getY()+" "+p.getZ());}
        },null);return true;
    });}
}
