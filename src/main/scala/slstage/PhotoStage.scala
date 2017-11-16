package slstage

import java.net.URL
import java.util.ResourceBundle

import javafx.application.Application
import javafx.concurrent.{Service, Task}
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.{FXML, FXMLLoader, Initializable}
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.scene.control.ToggleGroup
import javafx.scene.image.{PixelFormat, WritableImage}
import javafx.scene.input.{MouseButton, MouseEvent}
import javafx.scene.paint.Color
import javafx.stage.{Stage, StageStyle}

import javax.imageio.ImageIO

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

class PhotoStageController extends Initializable {
  val CuteColor = Color.web("0xf50570ff")
  val CoolColor = Color.web("0x0069fdff")
  val PassionColor = Color.web("0xfeb300ff")

  @FXML var root: Group = _
  @FXML var screen: ToggleGroup = _
  @FXML var canvas: Canvas = _

  private[this] var displayInfo: DisplayInfo = null
  private[this] var key = CoolColor
  private[this] var xoffset = 0.0
  private[this] var yoffset = 0.0

  private[this] val android = new Android
  private[this] val renderer = new Service[Unit] {
    def createTask() = new Task[Unit] {
      def call = {
        while (!isCancelled) {
          render()
        }
      }
    }
  }

  def initialize(location: URL, resources: ResourceBundle): Unit = {
    screen.selectedToggleProperty.addListener { (_, _, newValue) =>
      if (newValue != null) {
        key = newValue.getUserData match {
          case "cute" => CuteColor
          case "cool" => CoolColor
          case "passion" => PassionColor
        }
      }
    }
  }

  def touch(event: MouseEvent): Unit = {
    val stage = root.getScene.getWindow
    event.getButton match {
      case MouseButton.PRIMARY =>
        android.tap(displayInfo, event.getSceneX.toInt, event.getSceneY.toInt)
      case MouseButton.SECONDARY =>
        xoffset = stage.getX - event.getScreenX
        yoffset = stage.getY - event.getScreenY
      case _ =>
    }
  }

  def drag(event: MouseEvent): Unit = {
    val stage = root.getScene.getWindow
    event.getButton match {
      case MouseButton.PRIMARY =>
      case MouseButton.SECONDARY =>
        stage.setX(event.getScreenX + xoffset)
        stage.setY(event.getScreenY + yoffset)
      case _ =>
    }
  }

  def open(info: DisplayInfo): Unit = {
    displayInfo = info
    canvas.setWidth(info.gameWidth)
    canvas.setHeight(info.gameHeight)
    android.minicap(displayInfo).andThen {
      case Success(_) =>
        android.open()
    }
    renderer.start()
  }

  def close(): Unit = {
    renderer.cancel()
    android.close()
    android.destroy()
  }

  private[this] def render(): Unit = {
    try {
      if (android.isOpen) {
        val image = SwingFXUtils.toFXImage(android.frame(displayInfo), null)
        chromakey(image)
        val gc = canvas.getGraphicsContext2D
        gc.clearRect(0, 0, displayInfo.gameWidth, displayInfo.gameHeight)
        gc.drawImage(image, 0, 0)
      }
    } catch {
      case _: Throwable =>
        android.close()
        android.open()
    }
  }

  private[this] def chromakey(image: WritableImage): Unit = {
    val width = displayInfo.gameWidth
    val height = displayInfo.gameHeight
    val length = width * height * 4
    val buffer = new Array[Byte](length)
    val format = PixelFormat.getByteBgraInstance
    val reader = image.getPixelReader
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
}

class PhotoStage extends Application {
  var controller: PhotoStageController = _

  def start(stage: Stage): Unit = {
    val args = getParameters.getUnnamed
    val displayInfo = DisplayInfo(args.get(0).toInt, args.get(1).toInt, args.get(2).toInt, args.get(3).toInt)
    val loader = new FXMLLoader(getClass.getResource("/main.fxml"))
    val root = loader.load[Group]
    controller = loader.getController[PhotoStageController]
    controller.open(displayInfo)
    val scene = new Scene(root)
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
      println("java -jar photo-stage.jar {RealWidth}x{RealHeight}@{VirtualWidth}x{VirtualHeight}")
      System.exit(0)
  }
}
