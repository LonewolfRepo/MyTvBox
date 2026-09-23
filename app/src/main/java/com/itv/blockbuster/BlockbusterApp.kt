package com.itv.blockbuster

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.itv.blockbuster.di.ImageHttpClient
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class BlockbusterApp : Application(), ImageLoaderFactory {

    // UPDATED: Coil previously had no ImageLoaderFactory at all, so it fell
    // back to its own fully default, fully separate ImageLoader/OkHttpClient
    // - a third connection pool/thread pool in memory on top of the portal
    // API's own client and StreamValidator's. This wires it to the
    // dedicated image-loading client instead (see NetworkModule's
    // provideImageOkHttpClient for why that client - not the portal API's
    // authenticated one - is what's shared here), and turns on crossfade so
    // poster/thumbnail loads fade in instead of popping in abruptly during
    // carousel scroll.
    @Inject
    @ImageHttpClient
    lateinit var imageOkHttpClient: OkHttpClient

    // FIX (confirmed via device logcat - a ConnectivityManager StackLog
    // trace showed this happening live): Coil.imageLoader(context) builds
    // its ImageLoader singleton LAZILY, the first time ANY AsyncImage
    // anywhere in the app actually composes - which, on a fresh install,
    // was the very first PosterCard inside the very first carousel's LazyRow
    // item. ImageLoader.Builder(...).build() isn't cheap: it stands up a
    // ConnectivityManager network-callback observer (a real Binder IPC
    // call), a disk cache, and a memory cache. All of that was landing as a
    // single synchronous stall on the MAIN THREAD, mid-composition, during
    // the first carousel's first scroll/layout - exactly the kind of
    // stutter this bug report described. Building it here instead, on a
    // background dispatcher at app startup (well before any screen is
    // visible), and handing the already-built instance to Coil via
    // setImageLoader() means the first real AsyncImage composition finds a
    // singleton that's already there instead of paying to build one itself.
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.Default).launch {
            Coil.setImageLoader(newImageLoader())
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .crossfade(300)
            // FIX (confirmed via device logcat: "why are pictures being
            // decoded again when composition happens" - GC events freeing
            // 290k-320k objects landing exactly on top of multi-second
            // Davey stalls during long, sustained D-pad scrolling): this
            // was left at Coil's DEFAULT memory cache sizing, which
            // computes as a percentage of ActivityManager.getMemoryClass()
            // - the STANDARD per-app heap size, NOT the larger one
            // android:largeHeap="true" (see AndroidManifest.xml) actually
            // grants this app. That means the earlier largeHeap fix likely
            // never actually increased Coil's own cache budget at all, even
            // though it raised the app's overall heap ceiling for
            // everything else. With hundreds of distinct posters touched
            // per session (confirmed via gfx_stats.txt: 494-556 GPU texture
            // cache entries in a single capture), an undersized memory
            // cache means genuinely-already-seen images get evicted well
            // before the user scrolls back to them - so recomposing an
            // "already seen" item during a long scroll session can trigger
            // a real, full redecode, not just a fast cache hit. Explicitly
            // sizing this at 35% of available memory (rather than trusting
            // whatever Coil's default calculation happens to produce) gives
            // this app's actual poster-heavy usage pattern deliberate,
            // known headroom instead of an implicit, possibly-undersized
            // budget.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.35)
                    .build()
            }
            .build()
    }
}