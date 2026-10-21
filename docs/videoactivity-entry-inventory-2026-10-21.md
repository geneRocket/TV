# VideoActivity 按钮与回调入口清单

日期：2026-10-21；代码基线：`4e70e8ba8`。

此表登记两端 VideoActivity 的全部显式 setOn…Listener、Adapter/Presenter 构造入口及 @Override 回调（含内部 Port、异步回调）。登记与静态调用追踪不等于全部按钮完成真机点击；动态列表项和外部对话框内部控件另由其回调入口覆盖。具体缺陷和验证范围见 [审查结论](/Users/wuwenjun/code/TV/docs/videoactivity-refactor-review-2026-10-21.md)。

## TV

### 显式控件绑定（32）

| 位置 | 控件 | 事件 | 注册表达式 |
|---|---|---|---|
| [767](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:767) | `mBinding.widget.error` | Click | `view -> { takePlaybackControl(); onRefresh(); });` |
| [769](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:769) | `mBinding.desc` | Click | `view -> onDesc());` |
| [770](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:770) | `mBinding.keep` | Click | `view -> onKeep());` |
| [771](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:771) | `mBinding.video` | Click | `view -> onVideo());` |
| [772](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:772) | `mBinding.change1` | Click | `view -> onChange());` |
| [773](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:773) | `mBinding.search1` | Click | `view -> onSearchPage());` |
| [774](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:774) | `mBinding.control.text` | Click | `this::onTrack);` |
| [775](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:775) | `mBinding.control.volume` | Click | `view -> VolumeDialog.create(this).show());` |
| [776](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:776) | `mBinding.control.loudness` | Click | `view -> LoudnessDialog.create(this).show());` |
| [777](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:777) | `mBinding.control.audio` | Click | `this::onTrack);` |
| [778](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:778) | `mBinding.control.video` | Click | `this::onTrack);` |
| [787](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:787) | `mBinding.control.loop` | Click | `view -> onLoop());` |
| [788](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:788) | `mBinding.control.danmu` | Click | `view -> onDanmu());` |
| [789](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:789) | `mBinding.control.danmu` | LongClick | `view -> onDanmakuSource());` |
| [792](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:792) | `mBinding.control.next` | Click | `view -> checkNext());` |
| [793](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:793) | `mBinding.control.prev` | Click | `view -> checkPrev());` |
| [794](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:794) | `mBinding.control.episodes` | Click | `view -> onEpisodes());` |
| [795](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:795) | `mBinding.control.scale` | Click | `view -> onScale());` |
| [796](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:796) | `mBinding.control.speed` | Click | `view -> onSpeed());` |
| [797](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:797) | `mBinding.control.reset` | Click | `view -> onReset());` |
| [798](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:798) | `mBinding.control.player` | Click | `view -> onPlayer());` |
| [799](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:799) | `mBinding.control.decode` | Click | `view -> onDecode());` |
| [800](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:800) | `mBinding.control.ending` | Click | `view -> onEnding());` |
| [801](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:801) | `mBinding.control.opening` | Click | `view -> onOpening());` |
| [802](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:802) | `mBinding.control.player` | LongClick | `view -> onChoose());` |
| [803](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:803) | `mBinding.control.speed` | LongClick | `view -> onSpeedLong());` |
| [804](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:804) | `mBinding.control.reset` | LongClick | `view -> onResetToggle());` |
| [805](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:805) | `mBinding.control.ending` | LongClick | `view -> onEndingReset());` |
| [806](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:806) | `mBinding.control.opening` | LongClick | `view -> onOpeningReset());` |
| [807](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:807) | `mBinding.video` | Touch | `(view, event) -> mKeyDown.onTouchEvent(event));` |
| [833](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:833) | `child.itemView` | Key | `null);` |
| [838](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:838) | `child.itemView` | Key | `new View.OnKeyListener() {` |

### Adapter / Presenter 构造入口（8）

| 位置 | 入口 |
|---|---|
| [857](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:857) | `mBinding.flag.setAdapter(new ItemBridgeAdapter(mFlagAdapter = new ArrayObjectAdapter(mFlagPresenter = new FlagPresenter(this::onFlagClick))));` |
| [863](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:863) | `mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this::onQualityClick));` |
| [869](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:869) | `mBinding.array.setAdapter(new ItemBridgeAdapter(mArrayAdapter = new ArrayObjectAdapter(mArrayPresenter = new ArrayPresenter(this))));` |
| [875](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:875) | `mBinding.part.setAdapter(new ItemBridgeAdapter(mPartAdapter = new ArrayObjectAdapter(mPartPresenter = new PartPresenter(item -> SearchActivity.start(this, item, true)))));` |
| [881](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:881) | `mBinding.quick.setAdapter(new ItemBridgeAdapter(mQuickAdapter = new ArrayObjectAdapter(new QuickPresenter(item -> { capturePlaybackPosition(); takePlaybackControl(); mSourceSearch.select(item); }))));` |
| [898](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:898) | `mBinding.recommend.setAdapter(new ItemBridgeAdapter(mRecommendAdapter = new ArrayObjectAdapter(new VodPresenter(recommendListener))));` |
| [904](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:904) | `mBinding.control.parse.setAdapter(new ItemBridgeAdapter(mParseAdapter = new ArrayObjectAdapter(new ParsePresenter(this::onParseClick))));` |
| [920](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:920) | `getEpisodeView().setAdapter(new ItemBridgeAdapter(mEpisodeAdapter = new ArrayObjectAdapter(mEpisodePresenter = new EpisodePresenter(this::setEpisodeActivated))));` |

### Override 回调（136）

| 位置 | 入口 |
|---|---|
| [173](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:173) | `public boolean areItemsTheSame(@NonNull Vod oldItem, @NonNull Vod newItem)` |
| [178](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:178) | `public boolean areContentsTheSame(@NonNull Vod oldItem, @NonNull Vod newItem)` |
| [186](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:186) | `protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest)` |
| [203](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:203) | `public boolean isBackground()` |
| [204](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:204) | `public boolean isBuffering()` |
| [206](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:206) | `public void beginPreparing()` |
| [212](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:212) | `public void clearPlaybackOverlay()` |
| [213](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:213) | `public void showProgress()` |
| [214](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:214) | `public void stopContentSearch()` |
| [215](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:215) | `public void clearPlaybackTimeout()` |
| [216](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:216) | `public void updateMetadata()` |
| [217](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:217) | `public void resetRecovery()` |
| [218](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:218) | `public void clearBrokenSources()` |
| [219](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:219) | `public void hideProgress()` |
| [220](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:220) | `public void resetTransientPlayerState()` |
| [221](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:221) | `public void applyDefaultTrack()` |
| [222](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:222) | `public void showTracks()` |
| [224](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:224) | `public void persistPlayerSelection()` |
| [228](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:228) | `public void updateVideoSize()` |
| [237](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:237) | `public int currentEpisodeIndex()` |
| [238](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:238) | `public void capturePosition()` |
| [239](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:239) | `public boolean isSourceSwitching()` |
| [240](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:240) | `public void keepCurrentPositionOnNextPlay()` |
| [241](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:241) | `public void playFlag(Flag flag, int episode)` |
| [243](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:243) | `public boolean selectFlag(Flag item, boolean notify)` |
| [258](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:258) | `public void selectEpisode(Episode item)` |
| [269](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:269) | `public boolean selectParse(Parse item, boolean notify)` |
| [278](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:278) | `public void refreshPlayback()` |
| [279](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:279) | `public void stopSourceSearch()` |
| [280](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:280) | `public void openDetail(Vod item, boolean notify)` |
| [285](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:285) | `public Flag nextPlayableFlag()` |
| [296](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:296) | `public Vod nextPlayableSite()` |
| [302](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:302) | `public void beginSourceSwitch()` |
| [303](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:303) | `public void markCurrentSourceBroken()` |
| [304](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:304) | `public void startSourceLoading()` |
| [305](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:305) | `public void markSourceSelected()` |
| [310](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:310) | `public boolean accepts(Result result)` |
| [311](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:311) | `public String expectedKeyword()` |
| [312](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:312) | `public String sourceSwitchKeyword()` |
| [313](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:313) | `public boolean automatic()` |
| [314](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:314) | `public String sourceKey(Vod vod)` |
| [315](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:315) | `public void mergeCandidates(List<Vod> items, String token)` |
| [316](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:316) | `public void cancelEmptyFallback()` |
| [318](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:318) | `public void removeCandidate(Vod item)` |
| [324](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:324) | `public void beginSourceSwitch()` |
| [325](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:325) | `public void markCurrentSourceBroken()` |
| [326](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:326) | `public void setManual()` |
| [327](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:327) | `public void startSourceLoading()` |
| [328](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:328) | `public void openDetail(Vod vod)` |
| [329](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:329) | `public int candidateCount()` |
| [330](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:330) | `public void selectNextCandidate()` |
| [331](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:331) | `public void cancelSearch()` |
| [333](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:333) | `public void clearCandidates()` |
| [337](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:337) | `public void setSearchKeyword(String keyword)` |
| [343](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:343) | `public void startSearch(List<Site> sites, String keyword, boolean automatic, Runnable settled)` |
| [347](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:347) | `public void onSearchSettled()` |
| [351](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:351) | `public String nextRequestToken()` |
| [352](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:352) | `public void setPendingRequest(String token)` |
| [353](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:353) | `public void scheduleSourceTimeout()` |
| [354](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:354) | `public void requestDetail(String token)` |
| [355](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:355) | `public boolean accepts(Result result)` |
| [356](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:356) | `public void clearSourceTimeout()` |
| [357](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:357) | `public void prepareFor(Vod vod)` |
| [358](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:358) | `public void render(Vod vod)` |
| [359](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:359) | `public boolean isSourceSwitching()` |
| [360](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:360) | `public void resumeSourceSearch()` |
| [361](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:361) | `public void continueSourceSwitch()` |
| [362](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:362) | `public boolean shouldFinishWhenEmpty()` |
| [363](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:363) | `public boolean hasFallbackName()` |
| [364](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:364) | `public void finishScreen()` |
| [365](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:365) | `public void showEmptyState()` |
| [366](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:366) | `public void showFallbackName()` |
| [367](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:367) | `public void scheduleEmptyFallback()` |
| [368](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:368) | `public void recoverSource()` |
| [369](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:369) | `public void notifyMessage(String message)` |
| [373](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:373) | `public PlaybackRecovery.Action decide(ErrorEvent error)` |
| [374](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:374) | `public void retry()` |
| [375](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:375) | `public void toggleDecode()` |
| [376](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:376) | `public void reloadExo(ErrorEvent error)` |
| [377](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:377) | `public void switchPlayerAndRetry()` |
| [378](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:378) | `public void stopWithError(ErrorEvent error)` |
| [379](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:379) | `public boolean mayChangeSource()` |
| [380](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:380) | `public boolean usesParse()` |
| [382](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:382) | `public boolean selectNextParse()` |
| [389](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:389) | `public void resetParse()` |
| [390](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:390) | `public boolean selectNextFlag()` |
| [391](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:391) | `public void recoverSource()` |
| [392](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:392) | `public boolean hasRecoveryWork()` |
| [393](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:393) | `public boolean isSourceSwitching()` |
| [394](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:394) | `public void continueSourceSwitch()` |
| [710](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:710) | `public void onLayoutCompleted(@NonNull RecyclerView.State state)` |
| [726](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:726) | `protected ViewBinding getBinding()` |
| [731](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:731) | `protected void initView()` |
| [764](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:764) | `@SuppressLint("ClickableViewAccessibility") protected void initEvent()` |
| [809](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:809) | `public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition)` |
| [816](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:816) | `public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition)` |
| [823](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:823) | `public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition)` |
| [839](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:839) | `public boolean onKey(View v, int keyCode, KeyEvent event)` |
| [888](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:888) | `public void onItemClick(Vod item)` |
| [893](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:893) | `public boolean onLongClick(Vod item)` |
| [1222](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1222) | `public void onClick(@NonNull View view)` |
| [1230](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1230) | `public void onClick(@NonNull View view)` |
| [1458](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1458) | `public boolean onArrayItemTouch()` |
| [1464](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1464) | `public void onRevSort()` |
| [1471](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1471) | `public void onRevPlay(TextView view)` |
| [1987](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1987) | `public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition)` |
| [1994](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1994) | `public void onLoadFailed(@Nullable Drawable error)` |
| [2001](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2001) | `public void onLoadCleared(@Nullable Drawable placeholder)` |
| [2031](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2031) | `public void onResponse(@NonNull Call call, @NonNull Response response)` |
| [2051](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2051) | `public void onFailure(@NonNull Call call, @NonNull IOException e)` |
| [2116](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2116) | `public void showChooser(TrackDialog dialog)` |
| [2121](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2121) | `public void onTrackClick(Track item)` |
| [2126](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2126) | `public void onSubtitleClick()` |
| [2148](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2148) | `public void onTimeChanged()` |
| [2652](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2652) | `public boolean dispatchKeyEvent(KeyEvent event)` |
| [2715](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2715) | `public void onBright(int progress)` |
| [2724](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2724) | `public void onBrightEnd()` |
| [2729](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2729) | `public void onVolume(int progress)` |
| [2738](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2738) | `public void onVolumeEnd()` |
| [2743](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2743) | `public void onSeeking(int time)` |
| [2754](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2754) | `public void onSeekTo(int time)` |
| [2766](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2766) | `public void onSpeedUp()` |
| [2778](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2778) | `public void onSpeedEnd()` |
| [2787](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2787) | `public void onKeyUp()` |
| [2792](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2792) | `public void onKeyDown()` |
| [2797](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2797) | `public void onKeyCenter()` |
| [2808](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2808) | `public void onSingleTap()` |
| [2813](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2813) | `public void onDoubleTap()` |
| [2818](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2818) | `public void onPlayerClick(Integer item)` |
| [2829](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2829) | `public void onPlayerShare(String title)` |
| [2834](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2834) | `protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)` |
| [2848](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2848) | `protected void onResume()` |
| [2872](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2872) | `protected void onPause()` |
| [2885](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2885) | `protected void onStop()` |
| [2912](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2912) | `public void onBackPressed()` |
| [2934](/Users/wuwenjun/code/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2934) | `protected void onDestroy()` |

## 手机

### 显式控件绑定（44）

| 位置 | 控件 | 事件 | 注册表达式 |
|---|---|---|---|
| [441](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:441) | `mBinding.widget.error` | Click | `view -> { takePlaybackControl(); retryPlayback(); });` |
| [442](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:442) | `mBinding.name` | Click | `view -> onName());` |
| [443](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:443) | `mBinding.more` | Click | `view -> onMore());` |
| [444](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:444) | `mBinding.actor` | Click | `view -> onActor());` |
| [445](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:445) | `mBinding.content` | Click | `view -> onContent());` |
| [446](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:446) | `mBinding.reverse` | Click | `view -> onReverse());` |
| [447](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:447) | `mBinding.download` | Click | `view -> onDownload());` |
| [448](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:448) | `mBinding.name` | LongClick | `view -> onChange());` |
| [449](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:449) | `mBinding.content` | LongClick | `view -> onCopy());` |
| [450](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:450) | `mBinding.control.cast` | Click | `view -> onCast());` |
| [451](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:451) | `mBinding.control.info` | Click | `view -> onInfo());` |
| [452](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:452) | `mBinding.control.full` | Click | `view -> onFull());` |
| [453](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:453) | `mBinding.control.keep` | Click | `view -> onKeep());` |
| [454](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:454) | `mBinding.control.danmu` | Click | `view -> onDanmu());` |
| [455](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:455) | `mBinding.control.danmuSetting` | Click | `view -> onDanmuSetting());` |
| [456](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:456) | `mBinding.control.play` | Click | `view -> checkPlay());` |
| [457](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:457) | `mBinding.control.next` | Click | `view -> checkNext());` |
| [458](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:458) | `mBinding.control.prev` | Click | `view -> checkPrev());` |
| [459](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:459) | `mBinding.control.setting` | Click | `view -> onSetting());` |
| [460](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:460) | `mBinding.control.title` | LongClick | `view -> onChange());` |
| [461](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:461) | `mBinding.control.right.back` | Click | `view -> onFull());` |
| [462](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:462) | `mBinding.control.right.lock` | Click | `view -> onLock());` |
| [463](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:463) | `mBinding.control.right.rotate` | Click | `view -> onRotate());` |
| [464](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:464) | `mBinding.control.action.text` | Click | `this::onTrack);` |
| [465](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:465) | `mBinding.control.action.audio` | Click | `this::onTrack);` |
| [466](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:466) | `mBinding.control.action.video` | Click | `this::onTrack);` |
| [467](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:467) | `mBinding.control.action.loop` | Click | `view -> onLoop());` |
| [468](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:468) | `mBinding.control.action.scale` | Click | `view -> onScale());` |
| [469](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:469) | `mBinding.control.action.speed` | Click | `view -> onSpeed());` |
| [470](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:470) | `mBinding.control.action.reset` | Click | `view -> onReset());` |
| [471](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:471) | `mBinding.control.action.player` | Click | `view -> onPlayer());` |
| [472](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:472) | `mBinding.control.action.decode` | Click | `view -> onDecode());` |
| [473](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:473) | `mBinding.control.action.ending` | Click | `view -> onEnding());` |
| [474](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:474) | `mBinding.control.action.opening` | Click | `view -> onOpening());` |
| [475](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:475) | `mBinding.control.action.episodes` | Click | `view -> onEpisodes());` |
| [476](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:476) | `mBinding.control.action.text` | LongClick | `view -> onTextLong());` |
| [477](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:477) | `mBinding.control.action.player` | LongClick | `view -> onChoose());` |
| [478](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:478) | `mBinding.control.action.speed` | LongClick | `view -> onSpeedLong());` |
| [479](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:479) | `mBinding.control.action.reset` | LongClick | `view -> onResetToggle());` |
| [480](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:480) | `mBinding.control.action.ending` | LongClick | `view -> onEndingReset());` |
| [481](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:481) | `mBinding.control.action.opening` | LongClick | `view -> onOpeningReset());` |
| [482](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:482) | `mBinding.video` | Touch | `(view, event) -> mKeyDown.onTouchEvent(event));` |
| [483](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:483) | `mBinding.control.action.getRoot()` | Touch | `this::onActionTouch);` |
| [484](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:484) | `mBinding.swipeLayout` | Refresh | `this::onSwipeRefresh);` |

### Adapter / Presenter 构造入口（6）

| 位置 | 入口 |
|---|---|
| [492](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:492) | `mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));` |
| [493](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:493) | `mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));` |
| [497](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:497) | `mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(item -> { takePlaybackControl(); onItemClick(item); }, ViewType.HORI));` |
| [501](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:501) | `mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));` |
| [505](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:505) | `mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(item -> { takePlaybackControl(); onItemClick(item); }, ViewType.DARK));` |
| [520](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:520) | `mBinding.recommend.setAdapter(mRecommendAdapter = new VodAdapter(recommendListener, Style.rect(), Product.getSpec(this)));` |

### Override 回调（49）

| 位置 | 入口 |
|---|---|
| [359](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:359) | `protected boolean transparent()` |
| [364](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:364) | `protected ViewBinding getBinding()` |
| [369](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:369) | `protected void onNewIntent(Intent intent)` |
| [403](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:403) | `protected void initView(Bundle savedInstanceState)` |
| [414](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:414) | `public void post(Runnable task, long delayMillis)` |
| [415](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:415) | `public void remove(Runnable task)` |
| [438](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:438) | `@SuppressLint("ClickableViewAccessibility") protected void initEvent()` |
| [510](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:510) | `public void onItemClick(Vod item)` |
| [515](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:515) | `public boolean onLongClick(Vod item)` |
| [750](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:750) | `public void onClick(@NonNull View view)` |
| [896](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:896) | `public void onItemClick(Flag item)` |
| [926](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:926) | `public void onItemClick(Episode item)` |
| [942](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:942) | `public void onItemClick(Result result)` |
| [956](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:956) | `public void onItemClick(Vod item)` |
| [964](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:964) | `public void onItemClick(Parse item)` |
| [1460](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1460) | `public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition)` |
| [1467](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1467) | `public void onLoadFailed(@Nullable Drawable error)` |
| [1474](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1474) | `public void onLoadCleared(@Nullable Drawable placeholder)` |
| [1627](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1627) | `public void onTrackClick(Track item)` |
| [1632](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1632) | `public void onSubtitleClick()` |
| [1651](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:1651) | `public void onTimeChanged()` |
| [2110](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2110) | `public void onCasted()` |
| [2116](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2116) | `public void onScale(int tag)` |
| [2123](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2123) | `public void onParse(Parse item)` |
| [2129](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2129) | `public void onSpeedUp()` |
| [2141](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2141) | `public void onSpeedEnd()` |
| [2152](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2152) | `public void onBright(int progress)` |
| [2161](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2161) | `public void onBrightEnd()` |
| [2166](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2166) | `public void onVolume(int progress)` |
| [2175](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2175) | `public void onVolumeEnd()` |
| [2180](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2180) | `public void onSeek(int time)` |
| [2191](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2191) | `public void onSeekEnd(int time)` |
| [2205](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2205) | `public void onSeekCancel()` |
| [2213](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2213) | `public void onSingleTap()` |
| [2219](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2219) | `public void onDoubleTap()` |
| [2231](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2231) | `public void onShare(CharSequence title)` |
| [2238](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2238) | `public void onPlayerClick(Integer item)` |
| [2250](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2250) | `public void onPlayerShare(String title)` |
| [2255](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2255) | `protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)` |
| [2266](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2266) | `protected void onUserLeaveHint()` |
| [2274](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2274) | `public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode)` |
| [2291](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2291) | `public void onConfigurationChanged(@NonNull Configuration newConfig)` |
| [2299](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2299) | `public void onWindowFocusChanged(boolean hasFocus)` |
| [2305](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2305) | `protected void onStart()` |
| [2313](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2313) | `protected void onResume()` |
| [2326](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2326) | `protected void onPause()` |
| [2338](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2338) | `protected void onStop()` |
| [2365](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2365) | `public void onBackPressed()` |
| [2378](/Users/wuwenjun/code/TV/app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:2378) | `protected void onDestroy()` |
