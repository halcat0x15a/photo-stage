package slstage

import java.awt.image.BufferedImage
import java.net.Socket
import java.nio._
import java.io._

import javax.imageio.ImageIO

import scala.concurrent._
import scala.sys.process._

class Android {
  private[this] var sock: Socket = null
  private[this] var proc: Process = null

  val RealWidth = 1080
  val RealHeight = 2220
  val VirtualWidth = 360
  val VirtualHeight = 740
  val GameWidth = 360
  val GameHeight = 640

  def open(): Unit = {
    sock = new Socket("localhost", 1313)
    val in = sock.getInputStream
    in.read() // Version
    in.read() // Size of the header
    val pid = readUInt(in) // Pid of the process
    val realWidth = readUInt(in)
    val realHeight = readUInt(in)
    val virtualWidth = readUInt(in)
    val virtualHeight = readUInt(in)
    in.read() // Display orientation
    in.read() // Quirk bitflags
  }

  def isOpen: Boolean = sock != null

  def frame(): Option[BufferedImage] = {
    try {
      val in = sock.getInputStream
      val size = readUInt(in)
      val frame = new Array[Byte](size)
      in.read(frame)
      val image = ImageIO.read(new ByteArrayInputStream(frame))
      Some(image.getSubimage(0, (VirtualHeight - GameHeight) / 2, GameWidth, GameHeight))
    } catch {
      case _: Throwable =>
        sock.close()
        sock = null
        None
    }
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

  def minicap(): Future[Unit] = {
    val promise = Promise[Unit]
    val io = new ProcessIO(_.close(), _.close(), forward(promise))
    proc = Process(s"adb shell LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/minicap -P ${RealWidth}x${RealHeight}@${VirtualWidth}x${VirtualHeight}/0").run(io)
    promise.future
  }

  private[this] def forward(promise: Promise[Unit])(in: InputStream): Unit = {
    val reader = new BufferedReader(new InputStreamReader(in))
    val line = reader.readLine()
    in.close()
    if (line != null && line.contains("PID")) {
      Process("adb forward tcp:1313 localabstract:minicap").run().exitValue()
      promise.success(())
    } else {
      promise.failure(new RuntimeException)
    }
  }

  def tap(x: Int, y: Int): Unit = {
    Process(s"adb shell input tap ${x * 3} ${y * 3 + 150}").run()
  }

  def close(): Unit = {
    if (sock != null) sock.close()
    if (proc != null) proc.destroy()
  }
}
