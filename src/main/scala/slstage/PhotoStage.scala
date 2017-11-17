package slstage

import java.awt.{Color => AWTColor}

import javafx.application.Application
import javafx.concurrent.{Service, Task}
import javafx.event.ActionEvent
import javafx.fxml.{FXML, FXMLLoader}
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.scene.image.PixelFormat
import javafx.scene.input.{MouseButton, MouseEvent}
import javafx.scene.paint.Color
import javafx.stage.{Stage, StageStyle}

import scala.annotation.tailrec
import scala.sys.process.Process

class PhotoStageController(stage: Stage, info: DisplayInfo) {
  val CuteColor = new AWTColor(0xf5, 0x05, 0x70)
  val CoolColor = new AWTColor(0x00, 0x69, 0xfd)
  val PassionColor = new AWTColor(0xfe, 0xb3, 0x00)

  @FXML var canvas: Canvas = _

  private[this] var key = CoolColor
  private[this] var offsetX = 0.0
  private[this] var offsetY = 0.0

  private[this] val android = new Android(info)
  private[this] var client: Service[Unit] = _
  private[this] var server: Process = _

  @FXML def initialize(): Unit = {
    val width = info.gameWidth
    val height = info.gameHeight
    canvas.setWidth(width)
    canvas.setHeight(height)
    client = new Service[Unit] {
      def createTask() = new Task[Unit] {
        def call = {
          var sock = android.open()
          val format = PixelFormat.getIntArgbInstance
          val writer = canvas.getGraphicsContext2D.getPixelWriter
          while (!isCancelled) {
            try {
              val buffer = chromakey(android.frame(sock))
              writer.setPixels(0, 0, width, height, format, buffer, 0, width)
            } catch {
              case e: Throwable =>
                sock.close()
                sock = android.open()
            }
          }
          sock.close()
        }
      }
    }
    server = android.minicap {
      client.start()
    }
  }

  def cute(event: ActionEvent): Unit = {
    key = CuteColor
  }

  def cool(event: ActionEvent): Unit = {
    key = CoolColor
  }

  def passion(event: ActionEvent): Unit = {
    key = PassionColor
  }

  def touch(event: MouseEvent): Unit = {
    event.getButton match {
      case MouseButton.PRIMARY =>
        android.tap(event.getSceneX.toInt, event.getSceneY.toInt)
      case MouseButton.SECONDARY =>
        offsetX = stage.getX - event.getScreenX
        offsetY = stage.getY - event.getScreenY
      case _ =>
    }
  }

  def drag(event: MouseEvent): Unit = {
    event.getButton match {
      case MouseButton.PRIMARY =>
      case MouseButton.SECONDARY =>
        stage.setX(event.getScreenX + offsetX)
        stage.setY(event.getScreenY + offsetY)
      case _ =>
    }
  }

  def close(): Unit = {
    try { client.cancel() } catch { case _: Throwable => }
    try { server.destroy() } catch { case _: Throwable => }
  }

  private[this] def chromakey(rgb: Array[Int]): Array[Int] = {
    val width = info.gameWidth
    val height = info.gameHeight
    val length = width * height
    val buffer = new Array[Int](length)
    val threshold = 255 * 0.4
    val red = key.getRed
    val green = key.getGreen
    val blue = key.getBlue
    val offset = info.offsetY
    var i = 0
    while (i < length) {
      val pixel = rgb(i + offset)
      val b = (pixel & 0xff) - blue
      val g = ((pixel >> 8) & 0xff) - green
      val r = ((pixel >> 16) & 0xff) - red
      val distance = Math.sqrt(r * r + b * b + g * g)
      if (distance > threshold) {
        buffer(i) = pixel | 0xff000000
      }
      i += 1
    }
    buffer
  }
}

class PhotoStage extends Application {
  var controller: PhotoStageController = _

  def start(stage: Stage): Unit = {
    val args = getParameters.getUnnamed
    val info = DisplayInfo(args.get(0).toInt, args.get(1).toInt, args.get(2).toInt, args.get(3).toInt)
    controller = new PhotoStageController(stage, info)
    val loader = new FXMLLoader(getClass.getResource("/main.fxml"))
    loader.setController(controller)
    val scene = new Scene(loader.load[Group])
    stage.setScene(scene)
    stage.setTitle("PhotoStage")
    stage.setAlwaysOnTop(true)
    scene.setFill(Color.TRANSPARENT)
    stage.initStyle(StageStyle.TRANSPARENT)
    stage.show()
  }

  override def stop(): Unit = {
    controller.close()
  }
}

object PhotoStage extends App {
  val ProjectionRegex = """(\d+)x(\d+)@(\d+)x(\d+)""".r
  args.headOption match {
    case Some(ProjectionRegex(realWidth, realHeight, virtualWidth, virtualHeight)) =>
      Application.launch(classOf[PhotoStage], realWidth, realHeight, virtualWidth, virtualHeight)
    case _ =>
      Application.launch(classOf[PhotoStage], "1080", "1920", "720", "1280")
  }
}
