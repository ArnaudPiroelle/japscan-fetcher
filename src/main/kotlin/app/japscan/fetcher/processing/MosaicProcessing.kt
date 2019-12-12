package app.japscan.fetcher.processing

import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.ImageIO

object MosaicProcessing {
    fun process(input: InputStream, output: OutputStream) {
        val source = ImageIO.read(input)
        val output1 = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val output2 = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_RGB)
        val comboImage = output1.createGraphics()
        val comboImage2 = output2.createGraphics()

        val columnWidth = 100
        val lineHeight = 100
        val width = source.width.toDouble()
        val height = source.height.toDouble()

        var numColumn = 0
        while (width > numColumn * 2 * columnWidth + 2 * columnWidth) {
            numColumn++
        }

        var numLine = 0
        while (height > numLine * 2 * lineHeight + 2 * lineHeight) {
            numLine++
        }

        for (i in 0 until numColumn) {
            val column1 = i * 2
            val column2 = column1 + 1

            var src = Rect(column1 * columnWidth, 0, column2 * columnWidth, height.toInt())
            var dst = Rect(column2 * columnWidth, 0, (column2 + 1) * columnWidth, height.toInt())
            comboImage.drawImage(source, src, dst)

            src = Rect(column2 * columnWidth, 0, (column2 + 1) * columnWidth, height.toInt())
            dst = Rect(column1 * columnWidth, 0, column2 * columnWidth, height.toInt())
            comboImage.drawImage(source, src, dst)
        }

        var src = Rect(numColumn * 2 * columnWidth, 0, width.toInt(), height.toInt())
        var dst = Rect(numColumn * 2 * columnWidth, 0, width.toInt(), height.toInt())
        comboImage.drawImage(source, src, dst)
        comboImage.dispose()

        for (i in 0 until numLine) {
            val line1 = i * 2
            val line2 = line1 + 1

            src = Rect(0, line1 * lineHeight, width.toInt(), line2 * lineHeight)
            dst = Rect(0, line2 * lineHeight, width.toInt(), (line2 + 1) * lineHeight)
            comboImage2.drawImage(output1, src, dst)

            src = Rect(0, line2 * lineHeight, width.toInt(), (line2 + 1) * lineHeight)
            dst = Rect(0, line1 * lineHeight, width.toInt(), line2 * lineHeight)
            comboImage2.drawImage(output1, src, dst)
        }

        src = Rect(0, numLine * 2 * lineHeight, width.toInt(), height.toInt())
        dst = Rect(0, numLine * 2 * lineHeight, width.toInt(), height.toInt())
        comboImage2.drawImage(output1, src, dst)

        ImageIO.write(output2, "jpg", output)
        comboImage2.dispose()
        output1.flush()
        output2.flush()
    }

    private data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)
    private fun Graphics.drawImage(source: BufferedImage, src: Rect, dst: Rect): Boolean {
        return drawImage(
            source,
            src.left,
            src.bottom,
            src.right,
            src.top,
            dst.left,
            dst.bottom,
            dst.right,
            dst.top,
            null
        )
    }
}

