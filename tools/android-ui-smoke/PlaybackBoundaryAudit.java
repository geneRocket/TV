import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Diagnostic probe for VOD entry boundaries; --expect-fixed exits nonzero while defects remain. */
public final class PlaybackBoundaryAudit {
    static int defects;
    static int checks = 3;
    static final String P = "com.fongmi.android.tv.player.";
    static Class<?> type(String name) throws Exception { return Class.forName(name); }
    static Object call(Object target, String name, Object... args) throws Exception {
        Class<?> owner = target instanceof Class ? (Class<?>) target : target.getClass();
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == args.length) {
                boolean matches = true;
                for (int i=0;i<args.length;i++) if (args[i]!=null && !method.getParameterTypes()[i].isPrimitive() && !method.getParameterTypes()[i].isInstance(args[i])) matches=false;
                if (matches) return method.invoke(target instanceof Class ? null : target, args);
            }
        }
        throw new NoSuchMethodException(owner + "." + name);
    }
    static Object make(String name, Object... args) throws Exception {
        for (Constructor<?> c : type(name).getDeclaredConstructors()) if (c.getParameterTypes().length == args.length) {
            c.setAccessible(true);
            return c.newInstance(args);
        }
        throw new NoSuchMethodException(name);
    }
    static Object enumValue(String name, String value) throws Exception { return Enum.valueOf((Class)type(name),value); }
    static Object proxy(String name, InvocationHandler handler) throws Exception { Class<?> t=type(name); return Proxy.newProxyInstance(t.getClassLoader(),new Class[]{t},handler); }
    static void field(Object owner, String name, Object value) throws Exception {
        Class<?> t=owner instanceof Class?(Class<?>)owner:owner.getClass();
        Field f=t.getDeclaredField(name); f.setAccessible(true); f.set(owner instanceof Class?null:owner,value);
    }
    static Object allocate(String name) throws Exception {
        Class<?> u=type("sun.misc.Unsafe"); Field f=u.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return u.getMethod("allocateInstance",Class.class).invoke(f.get(null),type(name));
    }
    static Object item() throws Exception {
        return make(P+"session.PlaybackItem","episode-1","https://example.com/video.mp4",Collections.emptyMap(),null,null,Collections.emptyList(),30000L,false);
    }
    static Object fakeEngine(Object[] requestAndListener) throws Exception {
        return proxy(P+"engine.PlayerEngine",(p,m,a)->{
            if(m.getName().equals("prepare")){requestAndListener[0]=a[0];requestAndListener[1]=a[1];}
            if(m.getName().equals("snapshot"))return type(P+"engine.EngineSnapshot").getField("EMPTY").get(null);
            return null;
        });
    }
    static void emitReady(Object[] pair) throws Exception {
        Object id=call(pair[0],"getSessionId");
        Object event=call(type(P+"engine.EngineEvent"),"state",id,enumValue(P+"engine.EnginePhase","READY"),30000L,100000L);
        type(P+"engine.PlayerEngine$Listener").getMethod("onEngineEvent",type(P+"engine.EngineEvent")).invoke(pair[1],event);
    }
    public static void main(String[] args) throws Exception {
        Looper.prepare();
        Object app=allocate("com.fongmi.android.tv.App");
        field(app,"handler",new Handler(Looper.myLooper())); field(type("com.fongmi.android.tv.App"),"instance",app);
        pausedSubtitle();
        replacementAfterPrepare();
        exoPreparingSeek();
        transportEntries();
        System.out.println("RESULT checks="+checks+" defects="+defects+"; no application Activity started or data changed");
        System.exit(args.length > 0 && args[0].equals("--expect-fixed") && defects > 0 ? 1 : 0);
    }
    static void pausedSubtitle() throws Exception {
        Object oldItem=item(); Object paused=enumValue(P+"session.PlaybackPhase","PAUSED");
        Object state=make(P+"session.PlaybackState",1L,paused,oldItem,30000L,100000L,null);
        Object snapshot=make(P+"engine.EngineSnapshot",1L,enumValue(P+"engine.EnginePhase","PAUSED"),30000L,100000L,30000L,false,1.0f,0,0);
        Object[] prepared={null};
        Object session=proxy(P+"session.PlayerSession",(p,m,a)->{
            if(m.getName().equals("state"))return state;
            if(m.getName().equals("snapshot"))return snapshot;
            if(m.getName().equals("prepare")){prepared[0]=a[0]; return 2L;}
            return null;
        });
        Object controller=allocate(P+"PlaybackController");
        field(controller,"player",2); field(controller,"playbackSession",session);
        field(controller,"url","https://example.com/video.mp4"); field(controller,"headers",Collections.emptyMap());
        field(controller,"subs",Collections.emptyList()); field(controller,"timeout",60000); field(controller,"runnable",(Runnable)()->{});
        field(controller,"errors",make(P+"PlaybackErrors",(Executor)Runnable::run,(Consumer<Object>)e->{}));
        field(controller,"sourceResolver",make(P+"resolver.PlaybackSourceResolver"));
        Object subtitle=call(type("com.fongmi.android.tv.bean.Sub"),"from","https://example.com/sub.srt");
        call(controller,"setSub",subtitle);
        if(prepared[0]==null)throw new AssertionError("subtitle did not prepare");
        boolean autoplay=(Boolean)call(prepared[0],"isPlayWhenReady");
        if(autoplay) defects++;
        System.out.println((autoplay?"BUG":"PASS")+" paused subtitle reload: expected playWhenReady=false; actual="+autoplay);
    }
    static void replacementAfterPrepare() throws Exception {
        Object[] firstPair=new Object[2],nextPair=new Object[2];
        Object first=make(P+"session.DefaultPlayerSession",fakeEngine(firstPair));
        Object wrapper=make(P+"session.SwitchablePlayerSession",first);
        Object resolver=proxy(P+"resolver.PlaybackResolver",(p,m,a)->{
            Object id=call(a[0],"getRequestId");
            Object result=call(type("com.fongmi.android.tv.bean.Result"),"empty");
            type(P+"resolver.PlaybackResolver$Callback").getMethod("onResolved",type(P+"resolver.ResolveResult"))
                    .invoke(a[1],make(P+"resolver.ResolveResult",id,result));
            return proxy(P+"resolver.ResolveHandle",(q,n,b)->n.getName().equals("getRequestId")?id:null);
        });
        Object factory=proxy(P+"vod.PlaybackItemFactory",(p,m,a)->item());
        Object coordinator=make(P+"vod.VodPlaybackCoordinator",resolver,factory,wrapper);
        Object episode=make(P+"vod.VodEpisode","episode-1","site","flag","one","https://example.com/video.mp4");
        call(coordinator,"setEpisodes",Collections.singletonList(episode));
        call(coordinator,"play",0,enumValue(P+"vod.StartPolicy","FRESH")); emitReady(firstPair);
        Object port=proxy(P+"vod.VodPlaybackFlow$Port",(p,m,a)->{
            if(m.getName().equals("isCurrent") || m.getName().equals("selectSubtitle")) return true;
            if(m.getName().equals("speed")) return 1.0f;
            if(m.getName().equals("attachSubtitleSession")) {
                call(first,"release");
                Object next=make(P+"session.DefaultPlayerSession",fakeEngine(nextPair));
                call(wrapper,"replace",next);
            }
            return null;
        });
        Object flow=make(P+"vod.VodPlaybackFlow",coordinator,port);
        Object subtitle=call(type("com.fongmi.android.tv.bean.Sub"),"from","https://example.com/sub.srt");
        call(flow,"dispatch",call(type(P+"PlaybackInput"),"subtitle",subtitle));
        emitReady(nextPair);
        Object actual=call(call(coordinator,"state"),"getPhase");
        boolean detached=!actual.toString().equals("READY"); if(detached) defects++;
        System.out.println((detached?"BUG":"PASS")+" subtitle flow delegate replacement: expected coordinator READY; actual="+actual);
        call(coordinator,"close");
    }
    static void exoPreparingSeek() throws Exception {
        Object speed=make("androidx.media3.common.PlaybackParameters",1.0f);
        Object size=make("androidx.media3.common.VideoSize",1280,720);
        final long[] position={10000L};
        Object exo=proxy("androidx.media3.exoplayer.ExoPlayer",(p,m,a)->{
            if(m.getName().equals("seekTo")){position[0]=(Long)a[a.length-1]; return null;}
            if(m.getName().equals("getCurrentPosition") || m.getName().equals("getBufferedPosition"))return position[0];
            if(m.getName().equals("getDuration"))return 100000L;
            if(m.getName().equals("isPlaying"))return false;
            if(m.getName().equals("getPlaybackParameters"))return speed;
            if(m.getName().equals("getVideoSize"))return size;
            return null;
        });
        Object engine=allocate(P+"engine.ExoPlayerEngine");
        field(engine,"player",exo); field(engine,"phase",enumValue(P+"engine.EnginePhase","PREPARING"));
        field(engine,"requestedStartPosition",10000L); field(engine,"pendingStartSeek",true);
        call(engine,"seekTo",40000L);
        Object actual=call(call(engine,"snapshot"),"getPosition");
        boolean stale=!actual.equals(40000L); if(stale) defects++;
        System.out.println((stale?"BUG":"PASS")+" Exo PREPARING seek snapshot: expected=40000; actual="+actual+"; engine seek="+position[0]);
    }
    static void check(String name, boolean passed) {
        checks++;
        if(!passed)defects++;
        System.out.println((passed?"PASS ":"BUG ")+name);
    }
    static void transportEntries() throws Exception {
        Object[] pair=new Object[2];
        Object session=make(P+"session.DefaultPlayerSession",fakeEngine(pair));
        Object resolver=proxy(P+"resolver.PlaybackResolver",(p,m,a)->{
            Object id=call(a[0],"getRequestId");
            return proxy(P+"resolver.ResolveHandle",(q,n,b)->n.getName().equals("getRequestId")?id:null);
        });
        Object factory=proxy(P+"vod.PlaybackItemFactory",(p,m,a)->item());
        Object coordinator=make(P+"vod.VodPlaybackCoordinator",resolver,factory,session);
        Object episode=make(P+"vod.VodEpisode","episode-1","site","flag","one","one");
        call(coordinator,"setEpisodes",Collections.singletonList(episode));
        call(coordinator,"play",0,enumValue(P+"vod.StartPolicy","FRESH"));
        Object port=proxy(P+"vod.VodPlaybackFlow$Port",(p,m,a)->{
            if(m.getReturnType()==boolean.class)return m.getName().equals("isCurrent");
            if(m.getName().equals("speed"))return 1.0f;
            return null;
        });
        Object flow=make(P+"vod.VodPlaybackFlow",coordinator,port);
        Object controller=allocate(P+"PlaybackController");
        call(controller,"setInputHandler",flow);
        Object server=call(type("com.fongmi.android.tv.server.Server"),"get");
        call(server,"setPlayer",controller);
        Object timebar=allocate("com.fongmi.android.tv.ui.custom.CustomSeekView");
        field(timebar,"player",controller);field(timebar,"refresh",(Runnable)()->{});
        call(timebar,"onScrubStop",null,40000L,false);
        check("real time-bar entry preserves pending seek",call(call(coordinator,"state"),"getPosition").equals(40000L));
        Object media=call(type("com.fongmi.android.tv.impl.SessionCallback"),"create",controller);
        call(media,"onSeekTo",50000L);
        check("real media-session seek preserves pending seek",call(call(coordinator,"state"),"getPosition").equals(50000L));
        call(media,"onPause");
        check("real media-session pause changes VOD intent",call(coordinator,"wantsPlayback").equals(false));
        call(media,"onPlay");
        check("real media-session play changes VOD intent",call(coordinator,"wantsPlayback").equals(true));
        call(timebar,"onScrubStop",null,60000L,true);
        check("cancelled time-bar preview does not seek",call(call(coordinator,"state"),"getPosition").equals(50000L));
        call(server,"setPlayer",(Object)null);
        call(media,"onSeekTo",70000L);
        check("stale media-session owner cannot seek",call(call(coordinator,"state"),"getPosition").equals(50000L));
        call(flow,"close");
    }
}
