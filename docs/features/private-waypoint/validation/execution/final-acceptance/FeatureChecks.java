import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import _959.server_waypoint.common.client.*;
import _959.server_waypoint.common.client.gui.screens.*;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.*;
import _959.server_waypoint.core.edit.*;
import _959.server_waypoint.util.NamespacedId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;

public final class FeatureChecks {
    static final String DIM="minecraft:overworld";
    static WaypointClientMod mod(){return WaypointClientMod.getInstance();}
    static ClientWaypointSelection selection(ClientWaypointSource source,String list,String name){return mod().access().capture(source,DIM,list,name);}
    static void require(boolean x,String why){LiveChecks.require(x,why);}
    static void client(LiveChecks.Task task)throws Exception{LiveChecks.client(task);}
    static void state(Path output)throws Exception{
        var data=new LinkedHashMap<String,Object>();
        for(var source:ClientWaypointSource.values()){
            var rows=new ClientWaypointQuery(mod().collections()).query(ClientWaypointQuery.View.valueOf(source.name()),null,
                new WaypointQueryEngine.Query("",WaypointSorting.SortMode.DEFAULT,null,DIM,false));
            data.put(source.name(),Map.of("state",mod().collections().state(source).name(),"rows",rows.rows().stream().map(r->Map.of("dimension",r.selection().dimensionName(),"list",r.list().name(),"name",r.waypoint().name(),"description",r.waypoint().description(),"x",r.waypoint().x())).toList()));
        }
        data.put("profile",mod().localProfiles().active());
        data.put("remoteCount",mod().remoteCatalogs().snapshot().size());
        Files.writeString(output.resolveSibling(output.getFileName()+".json"),LiveChecks.GSON.toJson(data));
    }
    static boolean click(Screen screen,AbstractWidget widget){
        double x=widget.getX()+widget.getWidth()/2.0,y=widget.getY()+widget.getHeight()/2.0;
        return screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x,y,new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
    }
    static SimpleWaypoint rich(){return new SimpleWaypoint("Rich","Rich home","RH",new WaypointPos(-12,80,30),0x123456,90,true,List.of("home","test"),"Complete copy metadata",NamespacedId.parse("minecraft:diamond"));}
    static void metadata(SimpleWaypoint actual){var wanted=rich();require(actual!=null,"copy absent");require(actual.name().equals(wanted.name())&&Objects.equals(actual.displayNameOverride(),wanted.displayNameOverride())&&actual.initials().equals(wanted.initials())&&actual.pos().equals(wanted.pos())&&actual.rgb()==wanted.rgb()&&actual.yaw()==wanted.yaw()&&actual.global()==wanted.global()&&actual.keywords().equals(wanted.keywords())&&actual.description().equals(wanted.description())&&Objects.equals(actual.icon(),wanted.icon()),"copy metadata differs: "+actual);}
    public static void run(String op,Path output)throws Exception{
        if(op.equals("ready")){
            long until=System.currentTimeMillis()+30000;
            while(!mod().collections().ready(ClientWaypointSource.PRIVATE)&&System.currentTimeMillis()<until)Thread.sleep(100);
            client(()->{require(mod().collections().ready(ClientWaypointSource.PRIVATE),"private never ready");state(output);});
        }else if(op.equals("state")){client(()->state(output));
        }else if(op.equals("request-private")){
            client(()->mod().sendChunkedMessageToServer(mod().collections().updateRequest(ClientWaypointSource.PRIVATE)));Thread.sleep(1000);client(()->state(output));
        }else if(op.equals("seed-local")){
            client(()->{var a=mod().access();require(a.add(selection(ClientWaypointSource.LOCAL,"Homes",null),new SimpleWaypoint("Base","L",new WaypointPos(12,80,-30),0x884422,0,true)).status()==ClientWaypointAccess.Status.SAVED,"local seed");require(a.add(selection(ClientWaypointSource.LOCAL,"Source",null),rich()).status()==ClientWaypointAccess.Status.SAVED,"rich seed");state(output);});
        }else if(op.equals("copy")){
            client(()->{var a=mod().access();var src=selection(ClientWaypointSource.LOCAL,"Source","Rich");
                for(var source:List.of(ClientWaypointSource.SHARED,ClientWaypointSource.PRIVATE)){
                    var dest=selection(source,"Copies",null);var draft=a.copyDraft(src,dest);
                    MinecraftClientHelper.setScreen(Minecraft.getInstance(),WaypointAddScreen.fromCopy(null,a,draft));
                    var screen=Minecraft.getInstance().screen;require(click(screen,(AbstractWidget)LiveChecks.field(screen,"cancelButton")),"copy cancel");require(a.list(dest)==null,"cancel created list");
                    require(a.addCopy(draft).status()==ClientWaypointAccess.Status.AWAITING_SERVER,"copy submit "+source);
                }
            });Thread.sleep(1800);
            client(()->{for(var source:ClientWaypointSource.values())metadata(mod().access().waypoint(selection(source,source==ClientWaypointSource.LOCAL?"Source":"Copies","Rich")));state(output);});
        }else if(op.equals("copy-conflict")){
            client(()->{var a=mod().access();var src=selection(ClientWaypointSource.LOCAL,"Source","Rich");var draft=a.copyDraft(src,selection(ClientWaypointSource.PRIVATE,"Copies",null));require(a.addCopy(draft).status()==ClientWaypointAccess.Status.REJECTED,"copy conflict accepted");metadata(a.waypoint(src));metadata(a.waypoint(selection(ClientWaypointSource.PRIVATE,"Copies","Rich")));state(output);});
        }else if(op.equals("manager")){
            client(()->{
                var mc=Minecraft.getInstance();var query=new ClientWaypointQuery(mod().collections());var options=new WaypointQueryEngine.Query("Base",WaypointSorting.SortMode.NAME,new WaypointPos(0,80,0),DIM,false);
                var result=query.query(ClientWaypointQuery.View.COMBINED,DIM,options);require(result.rows().size()==3,"Combined Base count "+result.rows());require(result.rows().stream().map(ClientWaypointQuery.Row::selection).distinct().count()==3,"merged identities");
                for(var view:ClientWaypointQuery.View.values()){
                    WaypointClientMod.getClientConfig().setWaypointManagerView(view);var screen=new WaypointManagerScreen(mod(),null);MinecraftClientHelper.setScreen(mc,screen);
                    var list=(WaypointListWidget)LiveChecks.field(screen,"waypointListWidget");
                    for(boolean grouped:List.of(false,true)){list.setGroupByLists(grouped);list.setSearchQuery("Base");list.setSortMode(WaypointSorting.SortMode.NAME);list.refreshView();require(!list.getSearchSuggestions().isEmpty(),"search empty "+view);}
                    var add=(AbstractWidget)LiveChecks.field(screen,"addWaypointButton");require(click(screen,add),"Add button");
                    var selected=(ClientWaypointSelection)LiveChecks.field(mc.screen,"selection");require(selected.source()==view.destination(),"default add destination "+view+" "+selected);
                    require(click(mc.screen,(AbstractWidget)LiveChecks.field(mc.screen,"cancelButton")),"Add cancel");
                }
                state(output);
            });
        }else if(op.equals("edit-captured")){
            client(()->{var a=mod().access();var selected=selection(ClientWaypointSource.PRIVATE,"Homes","Base");var screen=new WaypointEditScreen(null,a,selected,"Homes",a.waypoint(selected));MinecraftClientHelper.setScreen(Minecraft.getInstance(),screen);WaypointClientMod.getClientConfig().setWaypointManagerView(ClientWaypointQuery.View.LOCAL);((EditBox)LiveChecks.field(screen,"descriptionEditBox")).setValue("Captured Private edit");require(click(screen,(AbstractWidget)LiveChecks.field(screen,"saveButton")),"Edit save click");});Thread.sleep(1600);
            client(()->{require(mod().access().waypoint(selection(ClientWaypointSource.PRIVATE,"Homes","Base")).description().equals("Captured Private edit"),"Private edit missing");require(!mod().access().waypoint(selection(ClientWaypointSource.SHARED,"Homes","Base")).description().equals("Captured Private edit"),"Shared edited");require(!mod().access().waypoint(selection(ClientWaypointSource.LOCAL,"Homes","Base")).description().equals("Captured Private edit"),"Local edited");state(output);});
        }else if(op.equals("stale-form")){
            client(()->{var a=mod().access();var source=selection(ClientWaypointSource.LOCAL,"Source","Rich");var destination=selection(ClientWaypointSource.LOCAL,"Stale",null);var draft=a.copyDraft(source,destination);var form=WaypointAddScreen.fromCopy(null,a,draft);MinecraftClientHelper.setScreen(Minecraft.getInstance(),form);var original=mod().localProfiles().active();var other=mod().localProfiles().create("Temporary form target");require(a.addCopy(draft).status()==ClientWaypointAccess.Status.OBSOLETE,"stale copy rerouted");require(((EditBox)LiveChecks.field(form,"nameEditBox")).getValue().equals("Rich"),"draft lost");mod().localProfiles().select(original.id());mod().localProfiles().delete(other.id());state(output);});
        }else if(op.equals("scene")){
            client(()->{mod().refreshCurrentCollections();var scene=LiveChecks.field(mod(),"scene");var visible=(Map<?,?>)LiveChecks.field(scene,"visible");var tracked=(Set<?>)staticField(OptimizedWaypointRenderer.class,"trackedWaypointRefs");var bases=new ArrayList<SimpleWaypoint>();
                for(var source:ClientWaypointSource.values()){var wp=mod().access().waypoint(selection(source,"Homes","Base"));require(wp!=null,"Base missing "+source);require(((Map<?,?>)visible.get(source)).containsValue(wp),"scene missing "+source);require(tracked.contains(wp),"renderer missing "+source);bases.add(wp);}
                require(new HashSet<>(bases).size()==3,"marker references merged");mod().setMarkerVisible(selection(ClientWaypointSource.PRIVATE,"Homes","Base"),false);require(((Map<?,?>)visible.get(ClientWaypointSource.LOCAL)).containsValue(bases.get(2)),"Local marker hidden by Private");require(((Map<?,?>)visible.get(ClientWaypointSource.SHARED)).containsValue(bases.get(0)),"Shared marker hidden by Private");mod().setMarkerVisible(selection(ClientWaypointSource.PRIVATE,"Homes","Base"),true);
                Files.writeString(output.resolveSibling("marker-identities.txt"),bases.stream().map(w->w.name()+" "+System.identityHashCode(w)).toList().toString());state(output);
            });
        }else if(op.equals("resync")){client(()->mod().requestUpdates());Thread.sleep(1600);run("scene",output);
        }else if(op.equals("tombstone")){
            client(()->{require(mod().access().remove(selection(ClientWaypointSource.PRIVATE,"Homes","Base")).status()==ClientWaypointAccess.Status.AWAITING_SERVER,"private remove rejected");});Thread.sleep(1500);
            client(()->{require(mod().access().waypoint(selection(ClientWaypointSource.PRIVATE,"Homes","Base"))==null,"private tombstone missing");var visible=(Map<?,?>)LiveChecks.field(LiveChecks.field(mod(),"scene"),"visible");for(var source:List.of(ClientWaypointSource.LOCAL,ClientWaypointSource.SHARED)){var wp=mod().access().waypoint(selection(source,"Homes","Base"));require(wp!=null&&((Map<?,?>)visible.get(source)).containsValue(wp),"tombstone removed "+source);}state(output);});
        }else if(op.equals("locales")){
            var mc=Minecraft.getInstance();
            for(String locale:List.of("en_us","zh_cn","zh_tw","zh_hk","es_es","he_il")){
                var reload=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>();
                client(()->{mc.getLanguageManager().setSelected(locale);mc.options.languageCode=locale;reload.set(mc.reloadResourcePacks());});reload.get().get(60,TimeUnit.SECONDS);Thread.sleep(3000);
                for(var view:ClientWaypointQuery.View.values()){
                    client(()->{WaypointClientMod.getClientConfig().setWaypointManagerView(view);var screen=new WaypointManagerScreen(mod(),null);MinecraftClientHelper.setScreen(mc,screen);mc.options.guiScale().set(4);mc.resizeGui();mc.options.hideGui=true;mc.getToastManager().clear();
                        var dropdown=(IconDropdownMenu)LiveChecks.field(screen,"collectionDropdown");require(click(screen,dropdown)&&dropdown.isExpanded(),"popup did not open "+locale+" "+view);var narration=new ArrayList<String>();
                        var collector=new net.minecraft.client.gui.narration.NarrationElementOutput(){public void add(net.minecraft.client.gui.narration.NarratedElementType type,net.minecraft.client.gui.narration.NarrationThunk contents){contents.getText(value->narration.add(value.toString()));}public net.minecraft.client.gui.narration.NarrationElementOutput nest(){return this;}};
                        LiveChecks.call(dropdown,"updateWidgetNarration",collector);require(!narration.isEmpty(),"collection narration absent "+locale);Files.writeString(output.resolveSibling(locale+"-"+view+"-narration.txt"),String.join("\n",narration));
                        screen.mouseScrolled(dropdown.getX(),dropdown.getY(),0,-1);require(dropdown.isExpanded(),"wheel closed popup");require(screen.keyPressed(256,0,0)&&!dropdown.isExpanded()&&mc.screen==screen,"Escape closed manager with popup");
                    });
                    Thread.sleep(300);client(()->net.minecraft.client.Screenshot.grab(mc.gameDirectory,"clear-"+locale+"-"+view+".png",mc.getMainRenderTarget(),1,message->{}));
                }
                client(()->MinecraftClientHelper.setScreen(mc,new LocalWaypointProfilesScreen(null,mod())));Thread.sleep(300);client(()->net.minecraft.client.Screenshot.grab(mc.gameDirectory,"clear-"+locale+"-profiles.png",mc.getMainRenderTarget(),1,message->{}));
            }
            client(()->state(output));
        }else if(op.equals("remote-and-collapse")){
            client(()->{
                var mc=Minecraft.getInstance();WaypointClientMod.getClientConfig().setWaypointManagerView(ClientWaypointQuery.View.COMBINED);var screen=new WaypointManagerScreen(mod(),null);MinecraftClientHelper.setScreen(mc,screen);
                var list=(WaypointListWidget)LiveChecks.field(screen,"waypointListWidget");list.setSearchQuery("Base");list.setGroupByLists(true);list.setSelectedDimension(DIM);list.refreshView();
                var shared=mod().access().list(selection(ClientWaypointSource.SHARED,"Homes",null));var own=mod().access().list(selection(ClientWaypointSource.PRIVATE,"Homes",null));var local=mod().access().list(selection(ClientWaypointSource.LOCAL,"Homes",null));
                boolean beforePrivate=own.isExpand(),beforeLocal=local.isExpand();
                for(int i=0;i<20;i++){Object entry=LiveChecks.call(list,"getEntryAtRow",i);if(entry==null)break;Object row=LiveChecks.call(entry,"value");if(row.getClass().getSimpleName().equals("ListNode")&&LiveChecks.call(row,"waypointList")==shared){LiveChecks.call(list,"setExpanded",row,!shared.isExpand());break;}}
                require(own.isExpand()==beforePrivate&&local.isExpand()==beforeLocal,"collapse leaked collection");shared.setExpand(true);list.refreshView();
                var query=new ClientWaypointQuery(mod().collections());var options=new WaypointQueryEngine.Query("",WaypointSorting.SortMode.DEFAULT,null,DIM,false);int count=query.query(ClientWaypointQuery.View.COMBINED,null,options).rows().size();
                var cache=mod().remoteCatalogs();cache.clear();var request=cache.poll();var id=new _959.server_waypoint.crossserver.RemoteServerId("fixture-remote");
                var waypoint=new _959.server_waypoint.crossserver.RemoteWaypointSnapshot("RemoteOnly","R",new WaypointPos(1,80,2),0x123456,0,true,List.of(),"Read-only fixture",null);
                var snapshot=new _959.server_waypoint.crossserver.RemoteCatalogSnapshot(id,new _959.server_waypoint.crossserver.RemoteRevision(1),Map.of("custom:remote",Map.of("Remote",new _959.server_waypoint.crossserver.RemoteListSnapshot("Remote",new _959.server_waypoint.crossserver.RemoteRevision(1),Map.of("RemoteOnly",waypoint)))),java.time.Instant.EPOCH);
                require(cache.apply(new _959.server_waypoint.core.network.message.RemoteCatalogMessage(request.requestId(),_959.server_waypoint.crossserver.RemoteCatalogState.AVAILABLE,Map.of(id,new _959.server_waypoint.crossserver.catalog.CatalogReceiver.View(snapshot,_959.server_waypoint.crossserver.RemoteCatalogState.AVAILABLE,"Remote fixture",null,"minecraft:compass")))),"remote fixture rejected");
                require(query.query(ClientWaypointQuery.View.COMBINED,null,options).rows().size()==count,"Remote entered Combined");
                screen.tick();LiveChecks.call(screen,"updateRemoteServerAvailability");var toggle=(AbstractWidget)LiveChecks.field(screen,"serverScopeToggle");require(toggle.visible&&click(screen,toggle),"remote toggle inactive");
                require((boolean)LiveChecks.field(screen,"showingRemote"),"did not enter Remote");for(String name:List.of("addWaypointButton","copyButton","profileButton","collectionDropdown"))require(!((AbstractWidget)LiveChecks.field(screen,name)).visible,"Remote exposed "+name);
                state(output);
            });
        }else if(op.equals("copy-denied")){
            client(()->{var a=mod().access();var src=selection(ClientWaypointSource.LOCAL,"Source","Rich");var dst=selection(ClientWaypointSource.PRIVATE,"DeniedCopies",null);var draft=a.copyDraft(src,dst);require(a.addCopy(draft).status()==ClientWaypointAccess.Status.DENIED,"copy did not observe permission denial");require(a.list(dst)==null,"denied copy created list");metadata(a.waypoint(src));state(output);});
        }else if(op.equals("copy-quota")){
            client(()->{var a=mod().access();var src=selection(ClientWaypointSource.LOCAL,"Source","Rich");var dst=selection(ClientWaypointSource.PRIVATE,"QuotaCopies",null);require(a.addCopy(a.copyDraft(src,dst)).status()==ClientWaypointAccess.Status.AWAITING_SERVER,"quota copy not submitted");});Thread.sleep(1600);client(()->{require(mod().access().list(selection(ClientWaypointSource.PRIVATE,"QuotaCopies",null))==null,"quota created destination");metadata(mod().access().waypoint(selection(ClientWaypointSource.LOCAL,"Source","Rich")));state(output);});
        }else if(op.equals("bossbar-count")){
            client(()->Files.writeString(output.resolveSibling(output.getFileName()+".json"),LiveChecks.GSON.toJson(Map.of("count",((Map<?,?>)LiveChecks.field(Minecraft.getInstance().gui.getBossOverlay(),"events")).size()))));
        }else if(op.equals("local-marker")){
            client(()->{require(mod().collections().ready(ClientWaypointSource.LOCAL),"Local unavailable");require(!mod().collections().ready(ClientWaypointSource.SHARED)&&!mod().collections().ready(ClientWaypointSource.PRIVATE),"server unexpectedly supported");var wp=mod().access().waypoint(selection(ClientWaypointSource.LOCAL,"Homes","Base"));require(wp!=null,"Local Base lost");mod().refreshCurrentCollections();require(((Set<?>)staticField(OptimizedWaypointRenderer.class,"trackedWaypointRefs")).contains(wp),"Local renderer lost waypoint");state(output);});
        }else if(op.equals("world-frame")){
            client(()->{var mc=Minecraft.getInstance();MinecraftClientHelper.setScreen(mc,null);mc.player.setYRot(0);mc.player.setXRot(0);mc.options.hideGui=false;mc.gui.getChat().clearMessages(false);mod().refreshCurrentCollections();});Thread.sleep(1500);client(()->net.minecraft.client.Screenshot.grab(Minecraft.getInstance().gameDirectory,"visible-collection-markers.png",Minecraft.getInstance().getMainRenderTarget(),1,message->{}));
        }else if(op.equals("new-profile")){
            client(()->{mod().localProfiles().create("Manual late-handshake profile");state(output);});
        }else if(op.equals("open-manager")){
            client(()->{var mc=Minecraft.getInstance();WaypointClientMod.getClientConfig().setWaypointManagerView(ClientWaypointQuery.View.COMBINED);MinecraftClientHelper.setScreen(mc,new WaypointManagerScreen(mod(),null));mc.options.guiScale().set(4);mc.resizeGui();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToastManager().clear();});
        }else if(op.equals("capture-transfer")){
            client(()->Files.writeString(output.getParent().resolve("captured-selection.json"),LiveChecks.GSON.toJson(selection(ClientWaypointSource.LOCAL,"StaleTransfer",null))));
        }else if(op.equals("stale-transfer")){
            client(()->{var captured=LiveChecks.GSON.fromJson(Files.readString(output.getParent().resolve("captured-selection.json")),ClientWaypointSelection.class);require(mod().access().add(captured,rich()).status()==ClientWaypointAccess.Status.OBSOLETE,"transfer saved old selection");state(output);});
        }else throw new IllegalArgumentException(op);
    }
    static Object staticField(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
}
