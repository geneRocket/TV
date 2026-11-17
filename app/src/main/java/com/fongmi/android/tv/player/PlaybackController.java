package com.fongmi.android.tv.player;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.engine.EngineSnapshot;
import com.fongmi.android.tv.player.session.PlaybackItem;
import com.fongmi.android.tv.player.session.PlaybackPhase;
import com.fongmi.android.tv.player.session.PlaybackState;
import com.fongmi.android.tv.player.session.PlayerSession;
import com.fongmi.android.tv.player.session.Subscription;
import com.fongmi.android.tv.player.resolver.ResolvedMedia;
import com.fongmi.android.tv.utils.AdBlocker;
import com.fongmi.android.tv.utils.M3u8AdFilter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ThreadPools;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.orhanobut.logger.Logger;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import com.fongmi.android.tv.ui.custom.PlaybackDanmakuView;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class PlaybackController implements DirectPlaybackFlow.Engine {

    private static final String TAG = PlaybackController.class.getSimpleName();
    private final StringBuilder builder;
    private final Formatter formatter;
    private Runnable timeoutCheck;
    private final PlaybackErrors errors;
    private final PlaybackCommands commands;
    private final ExternalPlayer externalPlayer;
    private final DanmakuController danmaku;
    private final MediaSessionController mediaSession;
    private final Set<com.fongmi.android.tv.player.session.PlaybackObserver> observers;

    private final PlaybackEngineHost engines;
    private Map<String, String> headers;
    private final PlaybackSourcePipeline sourcePipeline;
    private List<Sub> subs;
    private String format;
    private String url;
    private Drm drm;
    private Sub sub;
    private boolean forceLive;
    private boolean replay;
    private int timeout;
    private long activeSessionId;

    private long position;
    private int decode;
    private int player;
    private int playerState;

    private static final Pattern PATTERN_VOD = Pattern.compile(".*\\.(mp4|mkv|avi|mov|wmv|m4v)(\\?.*)?$");

    public static PlaybackController create(Activity activity, Consumer<ErrorEvent> onError, Consumer<PlaybackCommand> onCommand) {
        return new PlaybackController(activity, onError, onCommand);
    }

    public boolean isHard() {
        return decode == DecodeMode.HARDWARE;
    }

    public boolean isExo() {
        return player == PlayerType.EXO;
    }

    public boolean isIjk() {
        return player == PlayerType.SYSTEM || player == PlayerType.IJK;
    }

    private PlaybackController(Activity activity, Consumer<ErrorEvent> onError, Consumer<PlaybackCommand> onCommand) {
        player = Setting.getPlayer();
        decode = Setting.getDecode(player);
        builder = new StringBuilder();
        Executor main = task -> {
            if (Looper.myLooper() == Looper.getMainLooper()) task.run();
            else App.post(task);
        };
        commands = new PlaybackCommands(main, this::isCurrent, command -> {
            try {
                onCommand.accept(command);
            } catch (RuntimeException failure) {
                ThreadPools.log(failure, "Playback command callback failed.");
            }
        });
        errors = new PlaybackErrors(main, error -> {
            try {
                onError.accept(error);
            } catch (RuntimeException failure) {
                ThreadPools.log(failure, "Playback error callback failed.");
            }
        });
        externalPlayer = new ExternalPlayer(this);
        danmaku = new DanmakuController(Setting.isDanmu(), this::prepared);
        formatter = new Formatter(builder, Locale.getDefault());
        position = C.TIME_UNSET;
        playerState = Player.STATE_IDLE;
        timeout = Constant.TIMEOUT_PLAY;
        mediaSession = new MediaSessionController(activity, this);
        observers = new LinkedHashSet<>();
        engines = new PlaybackEngineHost();
        sourcePipeline = new PlaybackSourcePipeline(new PlaybackSourcePipeline.Port() {
            @Override public boolean isCurrent() { return PlaybackController.this.isCurrent(); }
            @Override public void resolved(ResolvedMedia media) { applyResolvedMedia(media); }
            @Override public void failed() { reportError(ErrorEvent.parse()); }
        });
    }

    public void init(PlayerView exo, IjkVideoView ijk) {
        activateAsCurrentPlayer();
        engines.views(exo, ijk);
        setupEngine();
    }

    public void activateAsCurrentPlayer() {
        if (engines != null && engines.isClosed()) return;
        PlaybackOwners.acquire(this);
    }

    public boolean isCurrent() {
        return PlaybackOwners.owns(this);
    }

    void revokeOwnership() {
        try {
            if (inputHandler != null) inputHandler.onOwnershipLost();
        } catch (RuntimeException error) {
            ThreadPools.log(error, "Playback handoff failed.");
        } finally {
            mediaSession.setActive(false);
            releasePlayer();
        }
    }

    public PlayerSession session() {
        return engines.session();
    }

    public boolean ownsSession(PlayerSession expected, long sessionId) {
        return isCurrent() && expected != null && engines.session() == expected
                && expected.state().getSessionId() == sessionId
                && expected.state().getPhase() != PlaybackPhase.RELEASED;
    }

    private void setupEngine() {
        engines.initialize(player, decode, this::onPlaybackState);
        player = engines.type();
        decode = engines.decode();
    }

    private void releaseEngine() { engines.releaseEngine(); }

    public void setDanmuView(PlaybackDanmakuView view) {
        danmaku.setView(view);
    }

    public void resetDanmu() {
        App.removeCallbacks(danmakuPositionSync);
        danmaku.reset();
    }

    public void prepareDanmu(BaseDanmakuParser parser, DanmakuContext context) {
        danmaku.prepare(parser, context);
    }

    public ExoPlayer exo() {
        return engines.exo();
    }

    public IjkVideoView ijk() {
        return engines.ijk();
    }

    public MediaSessionCompat getSession() {
        return mediaSession.getSession();
    }

    public String getUrl() {
        return java.util.Objects.requireNonNullElse(url, "");
    }

    public Map<String, String> getHeaders() {
        return headers == null ? new HashMap<>() : headers;
    }

    public void setSub(Sub sub) {
        if (dispatchInput(PlaybackInput.subtitle(sub))) return;
        setSubtitleDirect(sub);
    }

    private void setSubtitleDirect(Sub sub) {
        if (isSameSub(sub)) return;
        boolean paused = engines.session() != null && engines.session().state().getPhase() == PlaybackPhase.PAUSED;
        float speed = getSpeed();
        String identity = currentIdentity();
        this.sub = sub;
        if (TextUtils.isEmpty(url)) return;
        long current = getPosition();
        if (isIjk()) setPlayer(PlayerType.EXO);
        setPosition(current);
        reloadMediaSource(identity, !paused);
        if (engines.session() != null) engines.session().setSpeed(speed);
    }

    /** Changes metadata only; VOD prepares through its flow after attaching a compatible session. */
    public boolean selectSubtitle(Sub selected) {
        if (isSameSub(selected)) return false;
        sub = selected;
        return true;
    }

    private PlaybackInput.Handler inputHandler;
    private java.util.function.BooleanSupplier foregroundSurface;

    public void setForegroundSource(java.util.function.BooleanSupplier source) { foregroundSurface = source; }
    public boolean hasForegroundSurface() { return foregroundSurface != null && foregroundSurface.getAsBoolean(); }

    public void setInputHandler(PlaybackInput.Handler handler) { inputHandler = handler; }

    public boolean dispatchInput(PlaybackInput input) {
        if (inputHandler == null) return false;
        // Consume stale media-session input instead of rerouting it to another playback owner.
        if (isCurrent()) inputHandler.dispatch(input);
        return true;
    }

    public void syncDanmakuPosition(long position) { danmaku.seekTo(position); }

    public Sub getSub() {
        return sub;
    }

    private boolean isSameSub(Sub target) {
        if (sub == null || target == null) return sub == target;
        return TextUtils.equals(sub.getUrl(), target.getUrl());
    }

    public void setDanmakus(List<Danmaku> items) {
        danmaku.setSources(items);
    }

    public List<Danmaku> getDanmakus() {
        return danmaku.getSources();
    }

    public Danmaku getDanmaku() {
        return danmaku.getSource();
    }

    public void setDanmaku(Danmaku item) {
        danmaku.select(item);
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getRecoveryFormat(ErrorEvent error) {
        int code = error.getCode();
        if (code == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                || code >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                && code <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) {
            return ExoUtil.getMimeType(code);
        }
        return format;
    }

    public void recoverExo(ErrorEvent error) {
        setFormat(getRecoveryFormat(error));
        setMediaSource();
    }

    public void setMetadata(MediaMetadataCompat metadata) {
        mediaSession.setMetadata(metadata);
    }

    public int getPlayer() {
        return player;
    }

    public void setPlayer(int player) {
        boolean changed = this.player != player;
        if (changed) reset();
        if (changed) stop();
        this.player = player;
        this.decode = getDecode(player);
        if (changed || engines.engine() == null) setupEngine();
    }

    public boolean ensureCompatiblePlayer(Drm drm) {
        if (drm == null || !isIjk()) return false;
        setPlayer(PlayerType.EXO);
        return true;
    }

    public int getDecode(int player) {
        return Setting.getDecode(player);
    }

    public void setDecode(int player, int decode) {
        Setting.putDecode(player, decode);
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public void reset() {
        position = C.TIME_UNSET;
        removeTimeoutCheck();
        stopParse();
    }

    public void clear() {
        headers = null;
        format = null;
        sub = null;
        subs = null;
        danmaku.clearSources();
        drm = null;
        url = null;
        forceLive = false;
        replay = false;
    }

    public String stringToTime(long time) {
        return Util.format(builder, formatter, time);
    }

    public int getVideoWidth() {
        return snapshot().getVideoWidth();
    }

    public int getVideoHeight() {
        return snapshot().getVideoHeight();
    }

    public float getSpeed() {
        return snapshot().getSpeed();
    }

    public long getPosition() {
        return snapshot().getPosition();
    }

    public long getDuration() {
        return snapshot().getDuration();
    }

    public long getBuffered() {
        return snapshot().getBufferedPosition();
    }

    @Override public EngineSnapshot snapshot() {
        return engines.session() == null ? EngineSnapshot.EMPTY : engines.session().snapshot();
    }

    public boolean haveTrack(int type) {
        if (isExo() && exo() != null) return ExoUtil.haveTrack(exo().getCurrentTracks(), type);
        if (isIjk() && engines.ijk() != null) return engines.ijk().haveTrack(type);
        return false;
    }

    public boolean isPlaying() {
        return snapshot().isPlaying();
    }

    private void pauseDanmu() {
        App.removeCallbacks(danmakuPositionSync);
        danmaku.pause();
    }

    public boolean canAdjustSpeed() {
        return isIjk() || !Setting.isTunnel();
    }

    public boolean isBuffering() {
        return playerState == Player.STATE_BUFFERING;
    }

    public boolean isReady() {
        return playerState == Player.STATE_READY;
    }

    public boolean isEnd() {
        if (isExo() && exo() != null) return exo().getPlaybackState() == Player.STATE_ENDED;
        if (isIjk() && engines.ijk() != null) return engines.ijk().getPlaybackState() == IjkVideoView.STATE_ENDED;
        return false;
    }

    public boolean isRelease() {
        return engines.engine() == null;
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(getUrl());
    }

    public boolean isLive() {
        if (forceLive) return true;
        return getDuration() < 5 * 60 * 1000;
    }

    public boolean isVod() {
        if (forceLive) return false;
        return getDuration() > 5 * 60 * 1000;
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public String getSizeText() {
        return getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_player)[player];
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public String setSpeed(float speed) {
        if (engines.session() != null) engines.session().setSpeed(speed);
        danmaku.applySpeed(getSpeed());
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float addon = speed >= 2 ? 1f : 0.25f;
        speed = speed >= 5 ? 0.25f : Math.min(speed + addon, 5.0f);
        return setSpeed(speed);
    }

    public String addSpeed(float value) {
        float speed = getSpeed();
        speed = Math.min(speed + value, 5);
        return setSpeed(speed);
    }

    public String subSpeed(float value) {
        float speed = getSpeed();
        speed = Math.max(speed - value, 0.2f);
        return setSpeed(speed);
    }

    public String toggleSpeed() {
        float speed = getSpeed();
        speed = speed == 1 ? 3f : 1f;
        return setSpeed(speed);
    }

    public void nextPlayer() {
        setPlayer(isExo() ? PlayerType.IJK : PlayerType.EXO);
    }

    public void toggleDecode(boolean save) {
        decode = isHard() ? DecodeMode.SOFTWARE : DecodeMode.HARDWARE;
        if (save) setDecode(player, decode);
    }

    public String getPositionTime(long timeOffset) {
        return stringToTime(getNewTime(timeOffset));
    }

    public long getNewTime(long time) {
        time = getPosition() + time;
        if (time > getDuration()) time = getDuration();
        else if (time < 0) time = 0;
        return time;
    }

    public String getDurationTime() {
        long time = getDuration();
        if (time < 0) time = 0;
        return stringToTime(time);
    }

    public void seekTo(int time) {
        seekTo(getPosition() + time);
    }

    public void seekTo(long time) {
        if (dispatchInput(PlaybackInput.seek(time))) return;
        seekDirect(time);
    }

    private void seekDirect(long time) {
        danmaku.seekTo(time);
        if (engines.engine() != null) {
            engines.session().seekTo(time);
        }
    }

    public void play() {
        if (dispatchInput(PlaybackInput.action(PlaybackInput.Type.PLAY))) return;
        playDirect();
    }

    private void playDirect() {
        if (isPlaying()) return;
        if (isEnd()) seekDirect(0);
        activateAsCurrentPlayer();
        mediaSession.setActive(true);
        if (engines.session() != null) engines.session().play();
        updateDanmuPlayingState();
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    }

    public void pause() {
        if (dispatchInput(PlaybackInput.action(PlaybackInput.Type.PAUSE))) return;
        pauseDirect();
    }

    private void pauseDirect() {
        if (engines.session() != null) engines.session().pause();
        pauseDanmu();
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    }

    public void stop() {
        App.removeCallbacks(danmakuPositionSync);
        errors.invalidate();
        removeTimeoutCheck();
        stopParse();
        if (engines.session() != null) engines.session().stop();
        mediaSession.setActive(false);
        danmaku.stop();
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
        setPlayerState(Player.STATE_IDLE);
    }

    /** Low-level effects used by DirectPlaybackFlow; deliberately bypasses the public input router. */
    @Override public void executeInput(PlaybackInput input) {
        switch (input.getType()) {
            case PLAY: playDirect(); break;
            case PAUSE: pauseDirect(); break;
            case SEEK: seekDirect(input.getPosition()); break;
            case SUBTITLE: setSubtitleDirect(input.getSubtitle()); break;
            case STOP: stop(); break;
            default: throw new IllegalArgumentException("unsupported direct effect: " + input.getType());
        }
    }

    private void failIllegalUrl() {
        stop();
        reportError(ErrorEvent.url(0));
    }

    public void release() {
        App.removeCallbacks(danmakuPositionSync);
        inputHandler = null;
        foregroundSurface = null;
        commands.close();
        errors.close();
        sourcePipeline.close();
        stopParse();
        try {
            mediaSession.release();
        } catch (RuntimeException e) {
            ThreadPools.log(e, "Media session release failed.");
        }
        engines.close();
        try {
            danmaku.release();
        } catch (RuntimeException e) {
            ThreadPools.log(e, "Danmaku release failed.");
        }
        observers.clear();
        clear();
        removeTimeoutCheck();
        if (PlaybackOwners.release(this)) {
            M3u8AdFilter.clearSubtitlePlaylistWhitelist();
            App.execute(Source.get().deferStop());
        }
    }

    public void releasePlayer() {
        commands.invalidate();
        errors.invalidate();
        stopParse();
        releaseEngine();
        removeTimeoutCheck();
        pauseDanmu();
    }

    public void start(Channel channel, int timeout) {
        errors.invalidate();
        String url = getChannelUrl(channel);
        replay = channel.isReplay();
        boolean forceLive = shouldForceLive(url, channel.getFormat(), replay);
        this.timeout = timeout;
        this.forceLive = forceLive;
        if (channel.hasMsg()) {
            reportError(ErrorEvent.extract(channel.getMsg()));
        } else if (channel.getParse() == 1) {
            startParse(channel.result(), false);
        } else if (isIllegal(url)) {
            failIllegalUrl();
        } else {
            setMediaSource(channel, timeout);
        }
    }

    public void start(Result result, boolean useParse, int timeout) {
        errors.invalidate();
        replay = false;
        this.timeout = timeout;
        this.forceLive = false;
        if (result.hasErrorMsg()) {
            reportError(ErrorEvent.extract(result.getMsg()));
        } else if (result.getParse(1) == 1 || result.getJx() == 1) {
            startParse(result, useParse);
        } else if (Setting.isRemoveAd() && AdBlocker.isAdUrl(result.getRealUrl())) {
            reportError(ErrorEvent.url(0));
        } else if (isIllegal(result.getRealUrl())) {
            failIllegalUrl();
        } else {
            setMediaSource(result, timeout);
        }
    }

    private void startParse(Result result, boolean useParse) {
        sourcePipeline.resolve(result, useParse, forceLive);
    }

    private void applyResolvedMedia(ResolvedMedia media) {
        if (Setting.isRemoveAd() && AdBlocker.isAdUrl(media.getUrl())) {
            reportError(ErrorEvent.parse());
            return;
        }
        if (!TextUtils.isEmpty(media.getSource())) Notify.show(ResUtil.getString(R.string.parse_from, media.getSource()));
        setMediaSource(media.getHeaders(), media.getUrl(), media.getFormat(), media.getDrm(), media.getSubtitles(), timeout, media.isForceLive());
    }

    private void stopParse() { sourcePipeline.cancel(); }

    public void setMediaSource() {
        reloadMediaSource(currentIdentity());
    }

    public void restoreCheckpoint(EngineSnapshot checkpoint) {
        if (checkpoint != null) setPosition(forceLive ? C.TIME_UNSET : checkpoint.getPosition());
        setMediaSource();
        if (checkpoint != null) setSpeed(checkpoint.getSpeed());
    }

    private void reloadMediaSource(String identity) {
        reloadMediaSource(identity, true);
    }

    private void reloadMediaSource(String identity, boolean playWhenReady) {
        if (TextUtils.isEmpty(url)) return;
        setMediaSource(identity, headers, url, format, drm, subs, timeout, this.forceLive, playWhenReady);
    }

    public void setMediaSource(String url) {
        setMediaSource(new HashMap<>(), url);
    }

    private void setMediaSource(Map<String, String> headers, String url) {
        setMediaSource(headers, url, null, null, new ArrayList<>(), Constant.TIMEOUT_PLAY);
    }

    private void setMediaSource(Channel channel, int timeout) {
        String url = getChannelUrl(channel);
        boolean forceLive = shouldForceLive(url, channel.getFormat(), channel.isReplay());
        setMediaSource(channel.getHeaders(), url, channel.getFormat(), channel.getDrm(), new ArrayList<>(), timeout, forceLive);
    }

    private String getChannelUrl(Channel channel) {
        return TextUtils.isEmpty(channel.getUrl()) ? channel.getCurrent() : channel.getUrl();
    }

    private void setMediaSource(Result result, int timeout) {
        setMediaSource(result.getHeaders(), result.getRealUrl(), result.getFormat(), result.getDrm(), result.getSubs(), timeout, false);
    }

    private void setMediaSource(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout) {
        setMediaSource(headers, url, format, drm, subs, timeout, false);
    }

    private void setMediaSource(Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout, boolean forceLive) {
        setMediaSource("", headers, url, format, drm, subs, timeout, forceLive);
    }

    private void setMediaSource(String identity, Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout, boolean forceLive) {
        setMediaSource(identity, headers, url, format, drm, subs, timeout, forceLive, true);
    }

    private void setMediaSource(String identity, Map<String, String> headers, String url, String format, Drm drm, List<Sub> subs, int timeout, boolean forceLive, boolean playWhenReady) {
        if (!isCurrent()) return;
        errors.invalidate();
        stopParse();
        this.headers = PlaybackHeaders.applyUserAgent(PlaybackHeaders.mergeInlineHeaders(headers, url));
        this.url = UrlUtil.stripTag(UrlUtil.normalize(url, ""));
        this.format = format;
        this.drm = drm;
        this.forceLive = forceLive;
        this.subs = checkSub(subs);
        if (isIllegal(this.url)) {
            failIllegalUrl();
            return;
        }
        ensureCompatiblePlayer(this.drm);
        removeTimeoutCheck();
        try {
            PlayerSession session = engines.session();
            if (session == null) throw new IllegalStateException("player session is not initialized");
            long startPosition = position;
            position = C.TIME_UNSET;
            String itemIdentity = TextUtils.isEmpty(identity) ? this.url : identity;
            boolean effectivePlayWhenReady = inputHandler == null ? playWhenReady : inputHandler.playWhenReady(playWhenReady);
            long id = session.prepare(new PlaybackItem(itemIdentity, this.url, this.headers, this.format, this.drm,
                    this.subs, startPosition, this.forceLive, effectivePlayWhenReady));
            if (isPreparing(session, id)) {
                timeoutCheck = () -> {
                    if (isPreparing(session, id)) reportError(ErrorEvent.timeout());
                };
                App.post(timeoutCheck, timeout);
            }
        } catch (RuntimeException e) {
            removeTimeoutCheck();
            ThreadPools.log(e, "Player media source setup failed.");
            reportError(ErrorEvent.url(ExoUtil.getRetry(PlaybackException.ERROR_CODE_UNSPECIFIED), PlaybackException.ERROR_CODE_UNSPECIFIED));
            return;
        }
        Logger.t(TAG).d(url);
    }

    private boolean isPreparing(PlayerSession session, long id) {
        if (engines.session() != session || !isCurrent()) return false;
        PlaybackState state = session.state();
        return state.getSessionId() == id
                && (state.getPhase() == PlaybackPhase.PREPARING || state.getPhase() == PlaybackPhase.BUFFERING);
    }

    private void removeTimeoutCheck() {
        if (timeoutCheck != null) App.removeCallbacks(timeoutCheck);
        timeoutCheck = null;
    }

    private void setPlayerState(int state) {
        if (playerState == state) return;
        playerState = state;
    }

    public void setTrack(List<Track> tracks) {
        for (Track track : tracks) {
            if (engines.engine() == null) return;
            if (track.isExo(player) || track.isIjk(player)) engines.session().setTrack(track);
        }
    }

    private void setPlaybackState(int state) {
        mediaSession.setPlaybackState(state, getPosition(), getSpeed());
        ActionEvent.update();
    }

    private boolean isIllegal(String url) {
        Uri uri = UrlUtil.uri(url);
        String host = UrlUtil.host(uri);
        String scheme = UrlUtil.scheme(uri);
        switch (scheme) {
            case "data":
                return false;
            case "":
            case "file":
                return !com.github.catvod.utils.Path.exists(url);
            default:
                return host.isEmpty();
        }
    }

    private List<Sub> checkSub(List<Sub> subs) {
        return Sub.withSelected(subs, sub);
    }

    public void setMetadata(String title, String artist, String artUri) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, artUri);
        builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());
        setMetadata(builder.build());
        ActionEvent.update();
    }

    public void share(Activity activity, CharSequence title) {
        externalPlayer.share(activity, title);
    }

    public void choose(Activity activity, CharSequence title) {
        externalPlayer.choose(activity, title);
    }

    public void checkData(Intent data) {
        externalPlayer.checkResult(data);
    }

    private void onPlaybackState(PlaybackState state) {
        PlaybackPhase phase = state.getPhase();
        if (state.getItem() != null && phase != PlaybackPhase.STOPPED && phase != PlaybackPhase.RELEASED) {
            PlaybackItem item = state.getItem();
            url = item.getUrl();
            headers = item.getHeaders();
            format = item.getFormat();
            drm = item.getDrm();
            subs = item.getSubtitles();
            forceLive = item.isForceLive();
        }
        if (state.getSessionId() != 0) activeSessionId = state.getSessionId();
        for (com.fongmi.android.tv.player.session.PlaybackObserver observer : new LinkedHashSet<>(observers)) {
            if (engines.session() == null || engines.session().state() != state) return;
            try {
                observer.onPlaybackState(state);
            } catch (RuntimeException error) {
                ThreadPools.log(error, "Playback observer failed.");
            }
        }
        if (engines.session() == null || engines.session().state() != state) return;
        mediaSession.setActive(isCurrent() && phase != PlaybackPhase.IDLE && phase != PlaybackPhase.STOPPED
                && phase != PlaybackPhase.ERROR && phase != PlaybackPhase.RELEASED);
        switch (phase) {
            case IDLE:
            case STOPPED:
                setPlayerState(Player.STATE_IDLE);
                setPlaybackState(phase == PlaybackPhase.IDLE ? PlaybackStateCompat.STATE_NONE : PlaybackStateCompat.STATE_STOPPED);
                break;
            case PREPARING:
            case BUFFERING:
                setPlayerState(Player.STATE_BUFFERING);
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
                updateDanmuPlayingState();
                break;
            case READY:
            case PAUSED:
                removeTimeoutCheck();
                setPlayerState(Player.STATE_READY);
                updateLiveDecision();
                setPlaybackState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
                updateDanmuPlayingState();
                break;
            case ENDED:
                setPlayerState(Player.STATE_ENDED);
                pauseDanmu();
                setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
                break;
            case ERROR:
                removeTimeoutCheck();
                setPlayerState(Player.STATE_IDLE);
                setPlaybackState(PlaybackStateCompat.STATE_ERROR);
                pauseDanmu();
                dispatchPlaybackError(state.getError());
                break;
            case RELEASED:
                break;
        }
    }

    public Subscription observe(com.fongmi.android.tv.player.session.PlaybackObserver observer) {
        if (observer == null) throw new IllegalArgumentException("observer must not be null");
        observers.add(observer);
        try {
            observer.onPlaybackState(engines.session() == null ? PlaybackState.IDLE : engines.session().state());
        } catch (RuntimeException error) {
            ThreadPools.log(error, "Playback observer failed.");
        }
        return () -> observers.remove(observer);
    }

    private String currentIdentity() {
        if (engines.session() == null) return "";
        PlaybackItem item = engines.session().state().getItem();
        return item == null ? "" : item.getIdentity();
    }

    static boolean shouldForceLive(String url, String format, boolean replay) {
        return resolveLiveFlag(isLikelyLive(url, format), replay, false);
    }

    static boolean resolveLiveFlag(boolean hint, boolean replay, boolean timelineLive) {
        return !replay && (hint || timelineLive);
    }

    private static boolean isLikelyLive(String url, String format) {
        if (url == null) return false;
        String value = url.toLowerCase(Locale.US);
        String type = format == null ? "" : format.toLowerCase(Locale.US);
        if (type.contains("m3u8") || type.contains("hls") || type.contains("mpd") || type.contains("dash")) return true;
        if (type.contains("mp4") || type.contains("mkv") || type.contains("mov") || type.contains("avi")) return false;
        if (value.contains(".m3u8") || value.contains(".mpd") || value.contains(".m3u")) return true;
        if (value.contains("/live/") || value.contains("playlist") || value.contains("manifest")) return true;
        return !PATTERN_VOD.matcher(value).matches();
    }

    private void updateLiveDecision() {
        forceLive = resolveLiveFlag(forceLive, replay, exo() != null && exo().isCurrentMediaItemLive());
    }

    public void reportError(ErrorEvent error) {
        errors.report(error);
    }

    public void command(PlaybackCommand command) {
        commands.send(command);
    }

    private void dispatchPlaybackError(Throwable throwable) {
        if (!(throwable instanceof PlaybackException)) {
            reportError(ErrorEvent.url(1));
            return;
        }
        PlaybackException error = (PlaybackException) throwable;
        Logger.t(TAG).e(error.errorCode + "," + url);
        if (isPlaylistStuck(error)) {
            reportError(ErrorEvent.url(0, error.errorCode));
            return;
        }
        reportError(ErrorEvent.url(ExoUtil.getRetry(error.errorCode), error.errorCode));
    }

    private boolean isPlaylistStuck(PlaybackException error) {
        Throwable cause = error.getCause();
        while (cause != null) {
            if (cause.getClass().getName().contains("PlaylistStuckException")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    public void prepared() {
        App.post(this::updateDanmuPlayingState);
    }

    public void setDanmuVisible(boolean visible) {
        danmaku.setVisible(visible, isPlaying(), isBuffering(), getPosition(), getSpeed());
        scheduleDanmakuPositionSync();
    }

    private final Runnable danmakuPositionSync = this::updateDanmuPlayingState;

    private void updateDanmuPlayingState() {
        danmaku.updatePlayingState(isPlaying(), isBuffering(), getPosition(), getSpeed());
        scheduleDanmakuPositionSync();
    }

    private void scheduleDanmakuPositionSync() {
        App.removeCallbacks(danmakuPositionSync);
        if (danmaku.needsPositionSync()) App.post(danmakuPositionSync, 250);
    }

}
