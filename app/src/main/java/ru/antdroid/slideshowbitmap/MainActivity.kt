package ru.antdroid.slideshowbitmap

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.graphics.BitmapFactory

import android.graphics.Bitmap

import android.content.res.AssetManager
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.collection.LruCache
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

const val PAUSE_BETWEEN_FRAMES = 500L

class MainActivity : AppCompatActivity() {

    private val TAG: String = "MainActivityTest"
    lateinit var imageView: ImageView
    lateinit var fileName: TextView
    val countThreads = 5
    val cache = LruCache<Long, Bitmap>(countThreads*3)
    val maxTimeLoadFile = AtomicLong()
    val queue = ConcurrentLinkedQueue<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.image)
        fileName = findViewById(R.id.fileName)
        val progress = findViewById<ProgressBar>(R.id.progress)


        /*   val files = assets.list("")
           val pngFileList = files?.filter { it.contains(".png") }
        */
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch() {
            val timestampList = getAssetsTimestampList()

           timestampList.toLog("file list")


            val initList = timestampList.getNextList(0, countThreads)
            initList.list.toLog("init list")
            var lastBufferedIndex = initList.lastIndexInList
            Log.w("AntTest", "lastBufferedIndex is $lastBufferedIndex")

            loadListFileWithWait(initList.list)

            withContext(Dispatchers.Main) {
                progress.visibility = View.GONE
            }

            var timestamp = timestampList.first()
            var indexCount = countThreads
            while (true) {

                if(indexCount == countThreads){
                    val nIndex = timestampList.getNextIndex(timestampList[lastBufferedIndex])
                    val nextList = timestampList.getNextList(nIndex, countThreads)
                    nextList.list.toLog("next list")





                //    val t = System.currentTimeMillis()
                    nextList.list.forEach { ts ->
                        launch { loadTimestampFromAssetsToCache(ts) }
                    }
                //    Log.e("AntTest", "download time is ${System.currentTimeMillis()-t}----")








                    //  loadListFileWithoutWait(nextList.list)
                    lastBufferedIndex = nextList.lastIndexInList
                    indexCount = 0
                }

                val bitmap = getBitmapFromCache(timestamp)
                if (bitmap != null) {
                    loadBitmapInImageView(timestamp, bitmap)
                    Log.w("AntTest", "$timestamp is loaded from cache")
                } else {
                    Log.e("AntTest", "$timestamp cannot load from cache and MISS it")
                }
                val nextIndex = timestampList.getNextIndex(timestamp)
                timestamp = timestampList[nextIndex]
                indexCount++

                delay(PAUSE_BETWEEN_FRAMES)


            /*
                val nexIndex = timestampList.getNextIndex(currentItem)
                delay(PAUSE_BETWEEN_FRAMES)
                Log.w("AntTest", "size is ${timestampList.size} next index is $nexIndex")
                currentItem = timestampList[nexIndex]

                val newList = timestampList.getNextList(nexIndex, countThreads)
                newList.forEach { item ->
                    Log.w("AntTest", "$item in this list")
                }*/
            }

            return@launch


            val testListTimestamps = timestampList.take(countThreads)
            // val beginTime = System.currentTimeMillis()



            val threadCalc = ceil(maxTimeLoadFile.get().toDouble() / PAUSE_BETWEEN_FRAMES)
            var isDelayRun = true


            while (true) {
                queue.clear()
                queue.addAll(timestampList)
                timestampList.forEach { timestamp ->
                    loadAssetsFileToCache(threadCalc.toInt())
                    if(isDelayRun) delay(PAUSE_BETWEEN_FRAMES)
                    val bitmap = getBitmapFromCache(timestamp)
                    if (bitmap != null) {
                        isDelayRun = true
                        loadBitmapInImageView(timestamp, bitmap)
                        Log.w("AntTest", "$timestamp is loaded from cache")
                    } else {
                        isDelayRun = false
                        Log.e("AntTest", "$timestamp cannot load from cache and MISS it")
                    }
                }
            }

            //  }
            // val resultTime = System.currentTimeMillis() - beginTime
            //    val threadCalc = ceil(resultTime.toDouble() / countTestFiles / PAUSE_BETWEEN_FRAMES).toInt() + 1
            //    Log.i("AntTest", "load time $countTestFiles files is $resultTime msec (1 file ~${resultTime.toDouble() / countTestFiles} msec)")
            //     Log.i("AntTest", "thread is needed $threadCalc")

//            cache.resize(threadCalc+3)

            return@launch

            while (true) {

                launch {
                    //            loadAssetsFileToCache(threadCalc, ConcurrentLinkedQueue(timestampList))
                }

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                }

                timestampList.forEach { timestamp ->
                    delay(PAUSE_BETWEEN_FRAMES)
                    //val bitmap: Bitmap? = getBitmapFromAsset(timestamp)
                    val bitmap = getBitmapFromCache(timestamp)
                    if (bitmap != null) {
                        loadBitmapInImageView(timestamp, bitmap)
                        Log.w("AntTest", "$timestamp is loaded from cache")
                    } else {
                        Log.e("AntTest", "$timestamp cannot load from cache")
                    }
                }
            }
        }


/*
        scope.launch {
            pngFileList?.forEach {
                loadBitmapInImageView(it)
                delay(1000)
            }

        }
*/

    }

    suspend fun loadListFileWithWait(initList:List<Long>){
        withContext(Dispatchers.IO) {
            val timeJobList = arrayListOf<Deferred<Long>>()
            initList.forEach { timestamp ->
                timeJobList.add(async { loadTimestampFromAssetsToCache(timestamp) })
            }
                timeJobList.forEach {
                    val timeOneFile = it.await()
                    if (timeOneFile > maxTimeLoadFile.get()) maxTimeLoadFile.set(timeOneFile)
                    Log.i("AntTest", "$timeOneFile and maxTime is ${maxTimeLoadFile.get()}")
                }
        }
    }

    suspend fun loadListFileWithoutWait(initList:List<Long>){

        withContext(Dispatchers.IO) {
            val t = System.currentTimeMillis()
            initList.forEach { timestamp ->
                launch { loadTimestampFromAssetsToCache(timestamp) }
            }
            Log.e("AntTest", "download time is ${System.currentTimeMillis()-t}----")
        }

    }

    fun List<Long>.getNextIndex(currentItem:Long):Int{
        val currentIndex = indexOf(currentItem)
        return if(currentIndex+1 == size){
            0
        }else{
            currentIndex+1
        }
    }

    fun List<Long>.getNextList(firstIndex:Int, count:Int):ListTimestampToLoadInBuffer{
        val result = ArrayList<Long>()
        var item = this[firstIndex]
        result.add(item)
        var nextIndex = 0
        for (i:Int in 1 until count){
            nextIndex = getNextIndex(item)
            item = this[nextIndex]
            result.add(item)
       }
        return ListTimestampToLoadInBuffer(result, nextIndex)
    }

    fun List<Long>.toLog(title:String){
        Log.i("AntTest", "$title")
        forEachIndexed { index, l ->
            Log.i("AntTest", "$index) $l")
        }
    }

    data class ListTimestampToLoadInBuffer(
        val list: List<Long>,
        val lastIndexInList:Int
    )

    suspend fun loadTimestampFromAssetsToCache(timestamp: Long): Long {
        val beginTime = System.currentTimeMillis()
        var bitmap = getBitmapFromCache(timestamp)
        if(bitmap == null ) bitmap = getBitmapFromAsset(timestamp)
        setBitmapToCache(timestamp, bitmap)
        val timeResult = System.currentTimeMillis() - beginTime
        Log.i("AntTest", "load time $timeResult on file $timestamp")
        return timeResult
    }

    suspend fun loadAssetsFileToCache(threadCalc: Int) {
        withContext(Dispatchers.IO) {
                val time = arrayListOf<Deferred<Long>>()
                for (i: Int in 1..threadCalc) {
                    time.add(async { loadTimestampFromAssetsToCache(queue.poll()) })
                }

                time.forEach {
                    val timeOneFile = it.await()
                    if (timeOneFile > maxTimeLoadFile.get()) {
                        Log.i("AntTest", "correct max time now is $timeOneFile")
                        maxTimeLoadFile.set(timeOneFile)
                    }
                }

        }
       /*

        withContext(Dispatchers.IO) {
            while (timestampList.isNotEmpty()) {
                val joinList = ArrayList<Job>()
                for (i: Int in 1..threadCalc) {
                    val job = launch { getBitmapFromAsset(timestampList.poll()) }
                    joinList.add(job)
                }
                joinList.forEach {
                    it.join()
                }
            }
        }*/
    }

    suspend fun getBitmapFromCache(key: Long): Bitmap? {
        val result = cache.get(key)
        Log.w("AntTest", "bitmap ($key) get from cache. Result is ${result != null}");
        return result
    }

    suspend fun setBitmapToCache(key: Long, bitmap: Bitmap?) {
        if (bitmap == null) {
            Log.e("AntTest", "bitmap is null");
            return
        }
        cache.put(key, bitmap)
        Log.w("AntTest", "bitmap $key добавлен в кэш");
    }


    suspend fun loadBitmapInImageView(timestamp: Long, bitmap: Bitmap) {
        withContext(Dispatchers.Main) {
            imageView.setImageBitmap(bitmap)
            fileName.text = "file $timestamp.png is loaded"
        }
    }

    suspend fun getBitmapFromAsset(timestamp: Long?): Bitmap? {
        if (timestamp == null) return null
        //    val cacheBitmap = getBitmapFromCache(timestamp)
        //    if (cacheBitmap != null) {
        //     Log.d("AntTest", "load from cache $timestamp")
        //       return cacheBitmap
        //   }
        val assetManager: AssetManager = assets
        val istr: InputStream
        var bitmap: Bitmap? = null
        try {
            Log.d("AntTest", "begin load from file $timestamp")
            val rnds = (0L..2000L).random()
            delay(rnds)

            val filePath = "$timestamp.png"
            istr = assetManager.open(filePath)
            bitmap = BitmapFactory.decodeStream(istr)
            Log.d("AntTest", "loaded from file $timestamp (delay:$rnds)")
            //       if (bitmap != null) setBitmapToCache(timestamp, bitmap)
        } catch (e: IOException) {
            Log.e("AntTest", "error $e")
            // handle exception
        }
        return bitmap
    }


    suspend fun getAssetsTimestampList(): List<Long> {
        val files = assets.list("")
        val pngFileList = files?.filter { it.contains(".png") } ?: listOf()
        val timestampList = pngFileList.sortedBy { it }.map {
            it.substringBeforeLast(".").toLong()
        }
        Log.d("AntTest", "loaded list file from assets (is ${timestampList.size} files)")
        return timestampList
    }


/* suspend fun downloadImage(urlString:String?) : Bitmap?{
     if(urlString==null) return null
     return try {
         val url = URL(urlString)
         val httpURLConnection = url.openConnection() as HttpURLConnection
         httpURLConnection.connect()
         val input: InputStream = httpURLConnection.inputStream
         Log.e(TAG, "bitmap $urlString is load")
         BitmapFactory.decodeStream(input)
     } catch (e: MalformedURLException) {
         e.printStackTrace()
         null
     } catch (e: IOException) {
         e.printStackTrace()
         null
     }

 }*/

}

