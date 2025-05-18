package com.example.cizimuygulamasi // TODO: KENDİ PAKET ADINIZI YAZIN

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

// DATA CLASS TANIMLARI (Bir öncekiyle aynı)
data class PointData(val x: Float, val y: Float)
interface BaseDrawingElement { val type: String }
sealed class DrawingActionElement : BaseDrawingElement {
    data class PathAction(
        override val type: String = "path",
        val points: List<PointData>,
        val color: String,
        val strokeWidth: Float
    ) : DrawingActionElement()
    data class TextAction(
        override val type: String = "text",
        var text: String, var x: Float, var y: Float,
        var color: String, var textSize: Float,
        @Transient var bounds: RectF? = null
    ) : DrawingActionElement()
}
data class DrawingDataContainer(val elements: MutableList<DrawingActionElement>)

class DrawingActionElementAdapter : JsonSerializer<DrawingActionElement>, JsonDeserializer<DrawingActionElement> {
    override fun serialize(src: DrawingActionElement, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return context.serialize(src, src::class.java)
    }
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): DrawingActionElement {
        val jsonObject = json.asJsonObject
        val type = jsonObject.get("type")?.asString
        return when (type) {
            "path" -> context.deserialize(json, DrawingActionElement.PathAction::class.java)
            "text" -> context.deserialize(json, DrawingActionElement.TextAction::class.java)
            else -> throw JsonParseException("Unknown element type: $type")
        }
    }
}

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DrawingMode { PEN, ERASER, TEXT_PLACEMENT }
    private var currentDrawingMode = DrawingMode.PEN
    private var currentPathPaint: Paint = Paint()
    private var currentPath: Path = Path()
    private var currentPointsList = mutableListOf<PointData>()
    private var drawingElements = mutableListOf<DrawingActionElement>()
    private var activeColor: Int = Color.BLACK
    private var activeStrokeWidth: Float = 15f
    private var activeTextSize: Float = 60f
    private val eraserColor: Int = Color.parseColor("#F0F0F0")
    private var textToPlace: String? = null
    private val textPaint = TextPaint().apply { isAntiAlias = true }
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(DrawingActionElement::class.java, DrawingActionElementAdapter())
        .create()

    init { setupCurrentPaint() }

    private fun setupCurrentPaint() {
        currentPathPaint = Paint().apply {
            color = if (currentDrawingMode == DrawingMode.ERASER) eraserColor else activeColor
            strokeWidth = activeStrokeWidth
            isAntiAlias = true; style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        textPaint.color = activeColor
        textPaint.textSize = activeTextSize
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Arka planı çizmek isteyebilirsiniz, eğer transparan değilse
        // canvas.drawColor(eraserColor) // Veya XML'de tanımlanan background zaten bunu yapar.

        for (element in drawingElements) {
            when (element) {
                is DrawingActionElement.PathAction -> {
                    val path = Path()
                    if (element.points.isNotEmpty()) {
                        path.moveTo(element.points.first().x, element.points.first().y)
                        for (i in 1 until element.points.size) { path.lineTo(element.points[i].x, element.points[i].y) }
                    }
                    val paint = Paint().apply {
                        try { color = Color.parseColor(element.color) } catch (e: Exception) { color = Color.BLACK }
                        strokeWidth = element.strokeWidth
                        isAntiAlias = true; style = Paint.Style.STROKE
                        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawPath(path, paint)
                }
                is DrawingActionElement.TextAction -> {
                    textPaint.color = try { Color.parseColor(element.color) } catch (e: Exception) { Color.BLACK }
                    textPaint.textSize = element.textSize
                    canvas.drawText(element.text, element.x, element.y, textPaint)
                    if (element.bounds == null) { // Optimize edilmiş bounds hesaplaması
                        val bounds = Rect()
                        textPaint.getTextBounds(element.text, 0, element.text.length, bounds)
                        // Metnin sol alt köşesi (x,y) olduğu için bounds'ı ona göre ayarla
                        element.bounds = RectF(element.x, element.y - bounds.height(), element.x + bounds.width(), element.y)
                    }
                }
            }
        }
        if (currentDrawingMode == DrawingMode.PEN || currentDrawingMode == DrawingMode.ERASER) {
            canvas.drawPath(currentPath, currentPathPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (currentDrawingMode) {
            DrawingMode.PEN, DrawingMode.ERASER -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        currentPath.reset(); currentPointsList.clear(); setupCurrentPaint()
                        currentPath.moveTo(x, y); currentPointsList.add(PointData(x, y)); return true
                    }
                    MotionEvent.ACTION_MOVE -> { currentPath.lineTo(x, y); currentPointsList.add(PointData(x, y)) }
                    MotionEvent.ACTION_UP -> {
                        currentPath.lineTo(x, y); currentPointsList.add(PointData(x,y))
                        if (currentPointsList.size > 1) {
                            val colorToSave = if (currentDrawingMode == DrawingMode.ERASER) String.format("#%06X", (0xFFFFFF and eraserColor)) else String.format("#%06X", (0xFFFFFF and activeColor))
                            drawingElements.add(DrawingActionElement.PathAction(points = ArrayList(currentPointsList), color = colorToSave, strokeWidth = activeStrokeWidth))
                        }
                        currentPath.reset(); currentPointsList.clear()
                    }
                    else -> return false
                }
            }
            DrawingMode.TEXT_PLACEMENT -> {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    textToPlace?.let { text ->
                        val textColorHex = String.format("#%06X", (0xFFFFFF and activeColor))
                        drawingElements.add(DrawingActionElement.TextAction(text = text, x = x, y = y, color = textColorHex, textSize = activeTextSize))
                        Log.d("DrawingView", "Metin eklendi: '$text' at ($x, $y)")
                        textToPlace = null; setPenMode()
                        Toast.makeText(context, "'$text' eklendi.", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
            }
        }
        invalidate(); return true
    }

    fun setCurrentColor(color: Int) { activeColor = color; textPaint.color = activeColor }
    fun setStrokeWidthValue(width: Float) { activeStrokeWidth = if (width > 0) width else 1f }
    fun setTextSizeValue(size: Float) { activeTextSize = if (size > 10) size else 10f; textPaint.textSize = activeTextSize }
    fun setEraserMode() { currentDrawingMode = DrawingMode.ERASER }
    fun setPenMode() { currentDrawingMode = DrawingMode.PEN }
    fun prepareForTextPlacement(text: String) { this.textToPlace = text; this.currentDrawingMode = DrawingMode.TEXT_PLACEMENT; Toast.makeText(context, "'$text' yerleştirmek için ekrana dokunun.", Toast.LENGTH_LONG).show() }
    fun clearCanvas() { drawingElements.clear(); currentPath.reset(); currentPointsList.clear(); textToPlace = null; setPenMode(); invalidate() }
    fun getDrawingDataForUpload(): DrawingDataContainer = DrawingDataContainer(elements = ArrayList(drawingElements))
    fun loadDrawingFromJson(jsonData: String?) {
        clearCanvas()
        if (jsonData.isNullOrEmpty()) { invalidate(); return }
        try {
            val container = gson.fromJson(jsonData, DrawingDataContainer::class.java)
            container?.elements?.let { drawingElements.addAll(it) }
        } catch (e: Exception) { Log.e("DrawingView", "JSON yükleme hatası", e); Toast.makeText(context, "Çizim yüklenemedi.", Toast.LENGTH_SHORT).show() }
        invalidate()
    }

    // PDF EXPORT İÇİN YENİ METOD
    fun getBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Eğer View'ın normal bir arkaplanı varsa (XML'den gelen veya setBackgroundColor ile),
        // bu draw metodu onu da çizecektir. Eğer sadece çizilenleri istiyorsak ve
        // arkaplanı PDF'te ayrıca ayarlayacaksak, bu draw() yeterli.
        // Eğer transparan bir bitmap üzerine sadece elementleri çizmek istersek,
        // önce bitmap'i eraserColor ile doldurup sonra onDraw'u çağırabiliriz.
        // Şimdilik View'ın kendi draw metodu (arkaplan dahil) yeterli.
        this.draw(canvas)
        return bitmap
    }
}