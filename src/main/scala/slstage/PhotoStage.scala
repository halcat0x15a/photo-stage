package slstage

import java.net.Socket
import java.nio._
import java.io._

import javafx.application.Application
import javafx.concurrent._
import javafx.embed.swing.SwingFXUtils
import javafx.scene._
import javafx.scene.canvas._
import javafx.scene.control._
import javafx.scene.image._
import javafx.scene.input.MouseButton
import javafx.scene.paint.Color
import javafx.stage._

import javax.imageio.ImageIO

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._

class PhotoStage extends Application {
  val CuteColor = Color.web("0xf50570ff")
  val CoolColor = Color.web("0x0069fdff")
  val PassionColor = Color.web("0xfeb300ff")

  val android = new Android

  var svc: Service[Unit] = null

  def start(stage: Stage): Unit = {
    android.minicap().andThen { case Success(_) => android.open() }
    val (width, height) = (android.GameWidth, android.GameHeight)
    var xoffset = 0.0
    var yoffset = 0.0
    var key = CoolColor

    val cute = new MenuItem("Cute")
    cute.setOnAction { _ =>
      key = CuteColor
    }
    val cool = new MenuItem("Cool")
    cool.setOnAction { _ =>
      key = CoolColor
    }
    val passion = new MenuItem("Passion")
    passion.setOnAction { _ =>
      key = PassionColor
    }
    val menu = new Menu("Screen")
    menu.getItems.addAll(cute, cool, passion)
    val menuBar = new MenuBar
    menuBar.setUseSystemMenuBar(true)
    menuBar.getMenus().addAll(menu)

    val canvas = new Canvas(width, height)
    val gc = canvas.getGraphicsContext2D
    canvas.setOnMousePressed { event =>
      event.getButton match {
        case MouseButton.PRIMARY =>
          android.tap(event.getSceneX.toInt, event.getSceneY.toInt)
        case MouseButton.SECONDARY =>
          xoffset = stage.getX - event.getScreenX
          yoffset = stage.getY - event.getScreenY
        case _ =>
      }
    }
    canvas.setOnMouseDragged { event =>
      event.getButton match {
        case MouseButton.PRIMARY =>
        case MouseButton.SECONDARY =>
          stage.setX(event.getScreenX + xoffset)
          stage.setY(event.getScreenY + yoffset)
        case _ =>
      }
    }

    val group = new Group
    group.getChildren.addAll(menuBar, canvas)

    svc = new Service[Unit] {
      def createTask() = new Task[Unit] {
        def call = {
          while (!isCancelled) {
            try {
              if (android.isOpen) {
                android.frame().foreach { buffered =>
                  val image = SwingFXUtils.toFXImage(buffered, null)
                  chromakey(image, key)
                  gc.clearRect(0, 0, width, height)
                  gc.drawImage(image, 0, 0)
                }
              } else {
                android.open()
              }
            } catch {
              case e: Throwable =>
                e.printStackTrace
                android.open()
            }
          }
        }
      }
    }

    val scene = new Scene(group)
    scene.setFill(Color.TRANSPARENT)

    stage.setTitle("PhotoStageBB")
    stage.setScene(scene)
    stage.setAlwaysOnTop(true)
    stage.initStyle(StageStyle.TRANSPARENT)
    stage.show()
    svc.start()
  }

  def chromakey(image: WritableImage, key: Color): Unit = {
    val width = android.GameWidth
    val height = android.GameHeight
    val length = width * height * 4
    val buffer = new Array[Byte](length)
    val reader = image.getPixelReader
    val format = PixelFormat.getByteBgraInstance
    reader.getPixels(0, 0, width, height, format, buffer, 0, width * 4)
    @tailrec def filter(i: Int): Unit = {
      if (i + 3 < length) {
        val b = buffer(i + 0) & 0xff
        val g = buffer(i + 1) & 0xff
        val r = buffer(i + 2) & 0xff
        val distance = math.sqrt(math.pow((r.toDouble / 255) - key.getRed, 2)
          + math.pow((g.toDouble / 255) - key.getGreen, 2)
          + math.pow((b.toDouble / 255) - key.getBlue, 2))
        if (distance < 0.4) {
          buffer(i + 3) = 0
        }
        filter(i + 4)
      }
    }
    filter(0)
    val writer = image.getPixelWriter
    writer.setPixels(0, 0, width, height, format, buffer, 0, width * 4)
  }

  override def stop(): Unit = {
    if (svc != null) svc.cancel()
    android.close()
    super.stop()
  }
}

object PhotoStage extends App {
  Application.launch(classOf[PhotoStage])
}
