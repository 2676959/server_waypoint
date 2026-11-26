import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.client.multiplayer.chat.GuiMessage;

/** External acceptance controller. Never transforms the game or production mod. */
public final class LiveChecks {
    static Minecraft mc;
    static Path output;
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static void start(String home, String op, String path) {
        mc = Minecraft.getInstance(); output = Path.of(path);
        Thread worker = new Thread(() -> {
            try { run(op); Files.writeString(output,"PASS\n"); }
            catch(Throwable t) { var w=new java.io.StringWriter();t.printStackTrace(new java.io.PrintWriter(w));try{Files.writeString(output,"FAIL\n"+w);}catch(Exception ignored){} }
        },"private acceptance");worker.setDaemon(true);worker.start();
    }
    interface Task { void run() throws Exception; }
    static void client(Task task) throws Exception {
        var done=new CompletableFuture<Void>();mc.execute(() -> {try{task.run();done.complete(null);}catch(Throwable t){done.completeExceptionally(t);}});done.get(60,TimeUnit.SECONDS);
    }
    static Object field(Object owner,String name) throws Exception {
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static Object call(Object owner,String name,Object...args)throws Exception {
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())for(var method:type.getDeclaredMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length){method.setAccessible(true);return method.invoke(owner,args);}
        throw new NoSuchMethodException(name);
    }
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void snapshot()throws Exception{
        var messages=new ArrayList<Map<String,Object>>();
        for(Object entry:(List<?>)field(mc.gui.getChat(),"allMessages")){
            Component content=((GuiMessage)entry).content();var links=new ArrayList<String>();
            content.visit((style,text)->{var click=style.getClickEvent();if(click!=null){try{Object value=call(click,"command");links.add(value.toString());}catch(Exception ignored){}}return Optional.empty();},Style.EMPTY);
            messages.add(Map.of("text",content.getString(),"commands",links));
        }
        var data=new LinkedHashMap<String,Object>();data.put("chat",messages);
        if(mc.player!=null)data.put("position",List.of(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.level().dimension().identifier().toString()));
        data.put("screen",mc.screen==null?"null":mc.screen.getClass().getName());
        Files.writeString(output.resolveSibling(output.getFileName()+".json"),GSON.toJson(data));
    }
    static void run(String op)throws Exception{
        if(op.startsWith("connect:")){
            String address=op.substring(8);
            client(()->net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(),mc,
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                new net.minecraft.client.multiplayer.ServerData("Acceptance",address,net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null));
            long until=System.currentTimeMillis()+90000;while(mc.player==null&&System.currentTimeMillis()<until)Thread.sleep(100);
            require(mc.player!=null,"client did not join");Thread.sleep(1500);client(LiveChecks::snapshot);
        }else if(op.startsWith("cmd:")){
            client(()->{mc.gui.getChat().clearMessages(false);mc.player.connection.sendCommand(op.substring(4));});Thread.sleep(1000);client(LiveChecks::snapshot);
        }else if(op.startsWith("suggest:")){
            var future=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>>();
            client(()->{var connection=mc.getConnection();future.set(connection.getCommands().getCompletionSuggestions(connection.getCommands().parse(op.substring(8),connection.getSuggestionsProvider())));});
            var suggestions=future.get().get(20,TimeUnit.SECONDS);
            Files.writeString(output.resolveSibling(output.getFileName()+".json"),GSON.toJson(suggestions.getList().stream().map(s->s.getText()).toList()));
        }else if(op.equals("snapshot")){client(LiveChecks::snapshot);
        }else if(op.equals("clear")){client(()->mc.gui.getChat().clearMessages(false));
        }else if(op.equals("leave")){
            client(()->mc.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE));Thread.sleep(1200);
        }else if(op.startsWith("feature:")){
            Class.forName("FeatureChecks").getMethod("run",String.class,Path.class).invoke(null,op.substring(8),output);
        }else throw new IllegalArgumentException(op);
    }
    public static void quit(){Minecraft.getInstance().execute(Minecraft.getInstance()::stop);}
}
