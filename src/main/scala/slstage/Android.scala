package slstage

import com.sun.image.codec.jpeg.JPEGCodec

import java.awt.image.DataBufferInt
import java.net.Socket
import java.nio.{ByteBuffer, ByteOrder}
import java.io.{BufferedReader, ByteArrayInputStream, InputStream, InputStreamReader}
import javax.imageio.ImageIO

import scala.sys.process.{Process, ProcessIO}

case class DisplayInfo(realWidth: Int, realHeight: Int, virtualWidth: Int, virtualHeight: Int) {
  val ratio: Double = realWidth.toDouble / virtualWidth
  val gameWidth: Int = virtualWidth
  val gameHeight: Int = ((virtualWidth.toDouble / 9) * 16).toInt
  val offsetY: Int = (virtualHeight - gameHeight) / 2 * gameWidth
}

class Android(info: DisplayInfo) {
  def open(): Socket = {
    val sock = new Socket("localhost", 1313)
    val in = sock.getInputStream
    in.read() // Version
    in.read() // Size of the header
    readUInt(in) // Pid of the process
    readUInt(in) // Real display width in pixels
    readUInt(in) // Real display height in pixels
    readUInt(in) // Virtual display width in pixels
    readUInt(in) // Virtual display height in pixels
    in.read() // Display orientation
    in.read() // Quirk bitflags
    sock
  }

  def frame(sock: Socket): Array[Int] = {
    val in = sock.getInputStream
    val size = readUInt(in)
    val frame = new Array[Byte](size)
    var offset = in.read(frame)
    while (offset != size) {
      offset += in.read(frame, offset, size - offset)
    }
    val decoder = JPEGCodec.createJPEGDecoder(new ByteArrayInputStream(frame))
    decoder.decodeAsBufferedImage().getRaster.getDataBuffer.asInstanceOf[DataBufferInt].getData
  }

  private[this] def readUInt(in: InputStream): Int = {
    val bytes = new Array[Byte](4)
    in.read(bytes)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt
  }

  def minicap(callback: => Unit): Process = {
    val io = new ProcessIO(_.close(), _.close(), forward(callback))
    Process(s"adb shell LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P ${info.realWidth}x${info.realHeight}@${info.virtualWidth}x${info.virtualHeight}/0").run(io)
  }

  private[this] def forward(callback: => Unit)(in: InputStream): Unit = {
    val reader = new BufferedReader(new InputStreamReader(in))
    val line = reader.readLine()
    in.close()
    if (line != null && line.contains("PID")) {
      Process("adb forward tcp:1313 localabstract:minicap").run().exitValue()
      callback
    }
  }

  def tap(x: Int, y: Int): Unit = {
    val screenX = x * info.ratio
    val screenY = y * info.ratio + (info.realHeight - info.gameHeight * info.ratio) / 2
    Process(s"adb shell input tap ${screenX} ${screenY}").run()
  }
}
