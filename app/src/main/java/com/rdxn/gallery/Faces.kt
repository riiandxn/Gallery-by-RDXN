package com.rdxn.gallery

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val EMB = 128

class FaceRec(val key: Long, val mediaId: Long, val box: FloatArray, val emb: FloatArray, var pid: Int)
class PersonRec(val pid: Int, var name: String, val sum: FloatArray, var count: Int)

/** Satu-satunya tempat data wajah: disimpan di folder privat aplikasi (filesDir/faces). */
object Faces {
    @Volatile private var inst: FaceStore? = null

    fun dir(ctx: Context): File = File(ctx.applicationContext.filesDir, "faces")
    fun thumbFile(ctx: Context, key: Long): File = File(File(dir(ctx), "thumbs"), "$key.jpg")

    fun get(ctx: Context): FaceStore = inst ?: synchronized(this) {
        inst ?: FaceStore(dir(ctx)).also { it.load(); inst = it }
    }
}

class FaceStore(private val dir: File) {
    private val faces = ArrayList<FaceRec>()
    private val persons = LinkedHashMap<Int, PersonRec>()
    private val processed = HashSet<Long>()
    private var nextPid = 1
    private var nextKey = 1L
    private val dataFile: File get() = File(dir, "faces.bin")
    private val thumbDir: File get() = File(dir, "thumbs")

    companion object {
        const val CLUSTER_T = 0.45f   // kemiripan minimum (cosine) agar wajah masuk ke orang yang sama
    }

    init { thumbDir.mkdirs() }

    @Synchronized fun isProcessed(id: Long): Boolean = id in processed
    @Synchronized fun markProcessed(id: Long) { processed.add(id) }

    @Synchronized fun addFace(mediaId: Long, box: FloatArray, emb: FloatArray, thumb: ByteArray) {
        val key = nextKey++
        thumbDir.mkdirs()
        File(thumbDir, "$key.jpg").writeBytes(thumb)
        faces.add(FaceRec(key, mediaId, box, emb, assign(emb)))
    }

    /** Masukkan wajah ke orang terdekat; kalau tidak ada yang cukup mirip, buat orang baru. */
    private fun assign(emb: FloatArray): Int {
        var best: PersonRec? = null
        var bs = -1f
        for (p in persons.values) {
            var dot = 0f
            var n = 0f
            for (i in emb.indices) { dot += p.sum[i] * emb[i]; n += p.sum[i] * p.sum[i] }
            val s = dot / sqrt(max(n, 1e-9f))
            if (s > bs) { bs = s; best = p }
        }
        val target: PersonRec = if (best != null && bs >= CLUSTER_T) best else {
            val np = PersonRec(nextPid++, "", FloatArray(emb.size), 0)
            persons[np.pid] = np
            np
        }
        for (i in emb.indices) target.sum[i] += emb[i]
        target.count++
        return target.pid
    }

    @Synchronized fun rename(pid: Int, name: String) { persons[pid]?.name = name.trim() }

    @Synchronized fun clearAll() {
        faces.clear(); persons.clear(); processed.clear(); nextPid = 1; nextKey = 1L
        thumbDir.listFiles()?.forEach { it.delete() }
        save()
    }

    @Synchronized fun snapshot(): List<PersonUi> {
        val byPid = HashMap<Int, MutableList<FaceRec>>()
        for (f in faces) byPid.getOrPut(f.pid) { ArrayList() }.add(f)
        val out = ArrayList<PersonUi>()
        for (p in persons.values) {
            val fs = byPid[p.pid] ?: continue
            val ids = fs.map { it.mediaId }.toSet()
            if (ids.size < 2 && p.name.isBlank()) continue   // wajah yang cuma muncul sekali tidak ditampilkan
            val cover = fs.maxByOrNull { it.box[2] * it.box[3] } ?: continue
            out.add(PersonUi(p.pid, p.name, ids, cover.key))
        }
        out.sortByDescending { it.mediaIds.size }
        return out
    }

    @Synchronized fun save() {
        try {
            dir.mkdirs()
            val tmp = File(dir, "faces.tmp")
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { o ->
                o.writeInt(1); o.writeInt(nextPid); o.writeLong(nextKey)
                o.writeInt(processed.size); for (id in processed) o.writeLong(id)
                o.writeInt(persons.size)
                for (p in persons.values) {
                    o.writeInt(p.pid); o.writeUTF(p.name); o.writeInt(p.count)
                    for (v in p.sum) o.writeFloat(v)
                }
                o.writeInt(faces.size)
                for (f in faces) {
                    o.writeLong(f.key); o.writeLong(f.mediaId)
                    for (v in f.box) o.writeFloat(v)
                    o.writeInt(f.pid)
                    for (v in f.emb) o.writeFloat(v)
                }
            }
            tmp.renameTo(dataFile)
        } catch (e: IOException) {
            // gagal simpan: data tetap ada di memori, dicoba lagi pada penyimpanan berikutnya
        }
    }

    @Synchronized fun load() {
        if (!dataFile.exists()) return
        try {
            DataInputStream(BufferedInputStream(FileInputStream(dataFile))).use { i ->
                if (i.readInt() != 1) return
                nextPid = i.readInt(); nextKey = i.readLong()
                repeat(i.readInt()) { processed.add(i.readLong()) }
                repeat(i.readInt()) {
                    val pid = i.readInt(); val name = i.readUTF(); val count = i.readInt()
                    val sum = FloatArray(EMB) { i.readFloat() }
                    persons[pid] = PersonRec(pid, name, sum, count)
                }
                repeat(i.readInt()) {
                    val key = i.readLong(); val mid = i.readLong()
                    val box = FloatArray(4) { i.readFloat() }
                    val pid = i.readInt()
                    val emb = FloatArray(EMB) { i.readFloat() }
                    faces.add(FaceRec(key, mid, box, emb, pid))
                }
            }
        } catch (e: Exception) {
            faces.clear(); persons.clear(); processed.clear(); nextPid = 1; nextKey = 1L
        }
    }
}

/** Model SFace (ONNX): wajah 112x112 -> 128 angka. Model yang sama dengan FaceTagger di PC. */
class Embedder(ctx: Context) : AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inName: String

    init {
        val bytes = ctx.assets.open("sface.onnx").use { it.readBytes() }
        session = env.createSession(bytes, OrtSession.SessionOptions())
        inName = session.inputNames.first()
    }

    fun embed(aligned: Bitmap): FloatArray {
        val n = SIZE * SIZE
        val px = IntArray(n)
        aligned.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val data = FloatArray(3 * n)   // urutan RGB, nilai 0..255, tanpa normalisasi (sama dengan OpenCV)
        for (i in 0 until n) {
            val c = px[i]
            data[i] = ((c shr 16) and 0xFF).toFloat()
            data[n + i] = ((c shr 8) and 0xFF).toFloat()
            data[2 * n + i] = (c and 0xFF).toFloat()
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { t ->
            session.run(mapOf(inName to t)).use { res ->
                @Suppress("UNCHECKED_CAST")
                val v = (res[0].value as Array<FloatArray>)[0]
                var s = 0f
                for (x in v) s += x * x
                val norm = max(sqrt(s), 1e-9f)
                FloatArray(v.size) { v[it] / norm }
            }
        }
    }

    override fun close() { session.close() }

    companion object { const val SIZE = 112 }
}

/** Meluruskan wajah ke posisi standar (sama dengan alignCrop OpenCV). */
object Align {
    private val T = floatArrayOf(
        38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f,
    )

    /** s = 5 titik (mata kiri-gambar, mata kanan-gambar, hidung, mulut kiri-gambar, mulut kanan-gambar). */
    fun matrix(s: FloatArray): Matrix {
        var pmx = 0f; var pmy = 0f; var qmx = 0f; var qmy = 0f
        for (i in 0 until 5) { pmx += s[2 * i]; pmy += s[2 * i + 1]; qmx += T[2 * i]; qmy += T[2 * i + 1] }
        pmx /= 5; pmy /= 5; qmx /= 5; qmy /= 5
        var den = 0f; var na = 0f; var nb = 0f
        for (i in 0 until 5) {
            val px = s[2 * i] - pmx; val py = s[2 * i + 1] - pmy
            val qx = T[2 * i] - qmx; val qy = T[2 * i + 1] - qmy
            den += px * px + py * py
            na += px * qx + py * qy
            nb += px * qy - py * qx
        }
        val a = na / den
        val b = nb / den
        val tx = qmx - (a * pmx - b * pmy)
        val ty = qmy - (b * pmx + a * pmy)
        return Matrix().apply { setValues(floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)) }
    }

    fun crop(src: Bitmap, s: FloatArray): Bitmap {
        val out = Bitmap.createBitmap(Embedder.SIZE, Embedder.SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, matrix(s), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}

/** Memindai foto yang belum diproses: deteksi wajah -> luruskan -> embedding -> kelompokkan. */
class Indexer(private val ctx: Context, private val store: FaceStore) {
    companion object {
        private val lock = Mutex()
        private const val MAX_SIDE = 1280
        private const val MIN_FACE_PX = 48
    }

    suspend fun run(onProgress: (Int, Int) -> Unit = { _, _ -> }, onChanged: () -> Unit = {}) {
        withContext(Dispatchers.Default) {
            if (!lock.tryLock()) return@withContext
            var det: FaceDetector? = null
            var emb: Embedder? = null
            try {
                var done = 0
                while (true) {
                    val todo = MediaRepo.load(ctx).filter { !it.isVideo && !store.isProcessed(it.id) }
                    if (todo.isEmpty()) break
                    if (det == null || emb == null) {
                        det = FaceDetection.getClient(
                            FaceDetectorOptions.Builder()
                                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                                .setMinFaceSize(0.04f)
                                .build()
                        )
                        emb = Embedder(ctx)
                    }
                    for ((i, p) in todo.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        try {
                            process(p, det!!, emb!!)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            // foto rusak / gagal diproses: dilewati
                        }
                        store.markProcessed(p.id)
                        done++
                        onProgress(i + 1, todo.size)
                        if (done % 20 == 0) { store.save(); onChanged() }
                    }
                    store.save(); onChanged()
                }
            } finally {
                det?.close()
                emb?.close()
                lock.unlock()
            }
        }
    }

    private fun process(p: Photo, det: FaceDetector, emb: Embedder) {
        val bmp = decode(p.uri) ?: return
        try {
            val faces = Tasks.await(det.process(InputImage.fromBitmap(bmp, 0)))
            for (f in faces) {
                val r = f.boundingBox
                if (min(r.width(), r.height()) < MIN_FACE_PX) continue
                if (abs(f.headEulerAngleY) > 45f) continue   // wajah terlalu menyamping
                val pts = landmarks(f) ?: continue
                val aligned = Align.crop(bmp, pts)
                val e = emb.embed(aligned)
                aligned.recycle()
                val box = floatArrayOf(
                    r.left.toFloat() / bmp.width, r.top.toFloat() / bmp.height,
                    r.width().toFloat() / bmp.width, r.height().toFloat() / bmp.height,
                )
                store.addFace(p.id, box, e, thumb(bmp, r))
            }
        } finally {
            bmp.recycle()
        }
    }

    private fun decode(uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = max(info.size.width, info.size.height)
            if (side > MAX_SIDE) dec.setTargetSampleSize(side / MAX_SIDE)
        }
    } catch (e: Exception) {
        null
    }

    private fun landmarks(f: Face): FloatArray? {
        val le = f.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val re = f.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val nose = f.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val ml = f.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mr = f.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        if (le == null || re == null || nose == null || ml == null || mr == null) return null
        val e1 = if (le.x <= re.x) le else re
        val e2 = if (le.x <= re.x) re else le
        val m1 = if (ml.x <= mr.x) ml else mr
        val m2 = if (ml.x <= mr.x) mr else ml
        return floatArrayOf(e1.x, e1.y, e2.x, e2.y, nose.x, nose.y, m1.x, m1.y, m2.x, m2.y)
    }

    /** Potongan wajah persegi 128x128 (JPEG) untuk kartu Orang. */
    private fun thumb(bmp: Bitmap, r: Rect): ByteArray {
        val side = min(min((max(r.width(), r.height()) * 1.5f).toInt(), bmp.width), bmp.height)
        val left = (r.centerX() - side / 2).coerceIn(0, bmp.width - side)
        val top = (r.centerY() - side / 2).coerceIn(0, bmp.height - side)
        val crop = Bitmap.createBitmap(bmp, left, top, side, side)
        val small = Bitmap.createScaledBitmap(crop, 128, 128, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (crop !== bmp) crop.recycle()
        small.recycle()
        return out.toByteArray()
    }
}
