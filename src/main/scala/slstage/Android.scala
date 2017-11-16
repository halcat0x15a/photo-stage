package slstage

import java.awt.image.BufferedImage
import java.net.Socket
import java.nio.{ByteBuffer, ByteOrder}
import java.io.{BufferedReader, ByteArrayInputStream, InputStream, InputStreamReader}
import javax.imageio.ImageIO

import scala.concurrent.{Future, Promise}
import scala.sys.process.{Process, ProcessIO}

case class DisplayInfo(realWidth: Int, realHeight: Int, virtualWidth: Int, virtualHeight: Int) {
  def ratio: Double = realWidth.toDouble / virtualWidth
  def gameWidth: Int = virtualWidth
  def gameHeight: Int = ((virtualWidth.toDouble / 9) * 16).toInt
}

class Android {
  private[this] var sock: Socket = null
  private[this] var proc: Process = null

  def open(): Unit = {
    sock = new Socket("localhost", 1313)
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
  }

  def isOpen: Boolean = sock != null && !sock.isClosed

  def frame(info: DisplayInfo): BufferedImage = {
    val in = sock.getInputStream
    val size = readUInt(in)
    val frame = new Array[Byte](size)
    in.read(frame)
    val image = ImageIO.read(new ByteArrayInputStream(frame))
    image.getSubimage(0, (info.virtualHeight - info.gameHeight) / 2, info.gameWidth, info.gameHeight)
  }

  private[this] def readUInt(in: InputStream): Int = {
    val bytes = new Array[Byte](4)
    in.read(bytes)
    val buf = ByteBuffer.allocate(4)
    buf.order(ByteOrder.LITTLE_ENDIAN)
    buf.put(bytes)
    buf.flip()
    buf.getInt
  }

  def minicap(info: DisplayInfo): Future[Unit] = {
    val promise = Promise[Unit]
    val io = new ProcessIO(_ => (), _ => (), forward(promise))
    proc = Process(s"adb shell LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P ${info.realWidth}x${info.realHeight}@${info.virtualWidth}x${info.virtualHeight}/0").run(io)
    promise.future
  }

  private[this] def forward(promise: Promise[Unit])(in: InputStream): Unit = {
    val reader = new BufferedReader(new InputStreamReader(in))
    val line = reader.readLine()
    if (line != null && line.contains("PID")) {
      Process("adb forward tcp:1313 localabstract:minicap").run().exitValue()
      promise.success(())
    } else {
      promise.failure(new RuntimeException)
    }
  }

  def tap(info: DisplayInfo, x: Int, y: Int): Unit = {
    val screenX = x * info.ratio
    val screenY = y * info.ratio + (info.realHeight - info.gameHeight * info.ratio) / 2
    Process(s"adb shell input tap ${screenX} ${screenY}").run()
  }

  def close(): Unit = {
    if (sock != null) {
      sock.close()
    }
  }

  def destroy(): Unit = {
    if (proc != null) {
      proc.destroy()
    }
  }
}
