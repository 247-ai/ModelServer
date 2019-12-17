package com.ilabs.dsi.modelserver

import java.sql.SQLException

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, _}
import akka.http.scaladsl.server.Directives.{complete, get, handleExceptions, headerValueByName, path, pathPrefix, _}
import akka.http.scaladsl.server.{ExceptionHandler, MissingHeaderRejection, RejectionHandler, StandardRoute, ValidationRejection}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, PredefinedFromEntityUnmarshallers}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.stream.ActorMaterializer
import akka.util.ByteString
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.ilabs.dsi.modelserver.utils.{DBManager, ErrorConstants, Json, ModelServerConfig, Utils}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

trait WebRoute extends SprayJsonSupport with DefaultJsonProtocol{
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer

  // needed for the future flatMap/onComplete in the end
  implicit def executor: ExecutionContextExecutor

  // Define an implicit unmarshaller to convert the JSON payload to an WebInput object.
  // Somewhat hairy topic :-) References:
  // [1] http://malaw.ski/2016/04/10/hakk-the-planet-implementing-akka-http-marshallers/
  // [2] http://doc.akka.io/docs/akka-http/current/scala/http/common/unmarshalling.html
  implicit val webInputFromStringPayloadUM: FromEntityUnmarshaller[WebInput] =
  PredefinedFromEntityUnmarshallers.stringUnmarshaller.map(new WebInput(_))

  implicit val ErrorInfoFormat: RootJsonFormat[ErrorInfo] = jsonFormat3(ErrorInfo)
  implicit val ErrorFormat: RootJsonFormat[Error] = jsonFormat1(Error)

  // Define an exception handler for the possible exceptions that arise.
  val serverExceptionHandler = ExceptionHandler {
    case ex: Exception =>
      complete(HttpResponse(StatusCodes.InternalServerError, entity = ex.getMessage))
  }

  val serverRejectionHandler: RejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case ValidationRejection(msg, _) => completeWithError(msg)}
      .handle { case MissingHeaderRejection(str) =>
        if(str == "tucana-devKey")
          completeWithError(ErrorConstants.MISSING_KEY)
        else
          completeWithError(s"Missing Header: $str")}
      .result()

  val route = (handleExceptions(serverExceptionHandler)
    & handleRejections(serverRejectionHandler)
    & pathPrefix("modelserver" / "v1")
    & decodeRequest
    & cors()
    & withSizeLimit(ModelServerConfig.get("request.bytes.size").toLong)) {
    // Now proceed with the API functionality
    // Register an user through /user endpoint does not require devKey value.
    (path("users") & entity(as[WebInput]) & post) { webInput =>
      // Validate required fields
      val missingParams = webInput.required(Array("userId", "isAdmin").toList)
      validate(missingParams.isEmpty,ErrorConstants.MISSING_PARAM(missingParams.mkString(", "))) {
        validate(!DBManager.checkUserAlreadyExist(webInput.stringVal("userId")),ErrorConstants.USER_ALREADY_EXISTS) {
          val devKey = Utils.constructRandomKey(10)
          try {
            DBManager.setUser(webInput.stringVal("userId"), devKey, webInput.stringVal("isAdmin").toBoolean)
            complete(HttpEntity(ContentTypes.`application/json`, Json.Value(Map("userId" -> webInput.stringVal("userId"), "devKey" -> devKey)).writeln))
          }
          catch {
            case ex: SQLException => complete(StatusCodes.Forbidden, s"Error: ${ex.getMessage}\n")
          }
        }
      }
    } ~
      headerValueByName("tucana-devKey") {
        devKey => {
          validate(checkIfValidDevkey(devKey),ErrorConstants.INVALID_DEVKEY) {
            validate(DBManager.checkIfUserRegistered(devKey), ErrorConstants.USER_NOT_FOUND) {
              path("users") {
                get {
                  val userId = DBManager.getUser(devKey)
                  complete(HttpEntity(ContentTypes.`application/json`, Json.Value(Map("userId" -> userId)).writeln))
                }
              } ~
                path("models") {
                  get {
                    // Get the list of all models by this user
                    val models = DBManager.getModels(devKey)
                    val responseJson = Utils.constructJSONResponse(models, Array("version", "userId", "modelId", "description", "lastUpdateTimestamp"))
                    complete(HttpEntity(ContentTypes.`application/json`, responseJson))
                  }
                } ~
                // Save a model object
                path("models") {
                  post {
                    entity(as[Multipart.FormData]) {
                      formdata => {
                        //Processing each form data and store it in a Map[String,Array[Byte]]
                        val userId = DBManager.getUser(devKey)
                        var inputsGiven = formdata.parts.mapAsync(1) {
                          case b: BodyPart if b.name == "file" =>
                            def writeFileOnLocal(array: Array[Byte], byteString: ByteString): Array[Byte] = {
                              val byteArray = byteString.toArray
                              array ++ byteArray
                            }

                            b.entity.dataBytes.runFold(Array[Byte]())(writeFileOnLocal).map(bytes => b.name -> bytes)

                          case b: BodyPart if b.name == "schema" =>
                            def writeSchema(array: Array[Byte], byteString: ByteString): Array[Byte] = {
                              val byteArray = byteString.toArray
                              array ++ byteArray
                            }

                            b.entity.dataBytes.runFold(Array[Byte]())(writeSchema).map(bytes => b.name -> bytes)

                          case b: BodyPart =>
                            b.toStrict(2.seconds).map(strict =>
                              b.name -> strict.entity.data.utf8String.getBytes())
                        }.runFold(Map.empty[String, Array[Byte]])((map, tuple) => map + tuple)

                        onSuccess(inputsGiven) { up =>
                          //Converting the array bytes to string in order to store in DB
                          val modelId = new String(up.getOrElse("modelId", Array[Byte]()))
                          val version = new String(up.getOrElse("version", Array[Byte]()))
                          val schema = new String(up.getOrElse("schema", Array[Byte]()))
                          val description = new String(up.getOrElse("description", Array[Byte]()))
                          val model = up.getOrElse("file", Array[Byte]())
                          validate(!DBManager.checkIfModelExist(modelId,version),ErrorConstants.MODEL_ALREADY_EXISTS) {
                            DBManager.setModel(modelId, version, userId, description, model, schema)
                            complete(HttpEntity(ContentTypes.`application/json`, Json.Value(Map("status" -> "success", "modelId" -> modelId, "version"->version)).writeln))
                          }
                        }
                      }
                    }
                  }
                }
            }
          }
        }
      }

  }

  def checkIfValidDevkey(devKey:String):Boolean = {
    devKey.matches("^[a-zA-Z0-9]*$")
  }

  def completeWithError(error: String): StandardRoute = {
    complete(errorDecoder(error)._2,Error(ErrorInfo(errorDecoder(error)._1, errorDecoder(error)._3, error)))
  }

  case class ErrorInfo(code: Int, `type`: String, message: String)

  case class Error(error: ErrorInfo)

  def errorDecoder(error: String): (Int, StatusCode, String) = {
    error match {
      case ErrorConstants.USER_ALREADY_EXISTS => (0, Forbidden, "SQLException")
      case ErrorConstants.USER_NOT_FOUND => (1, NotFound, "SQLException")
      case s if s.startsWith("Missing params:") => (2, BadRequest, "InputError")
      case ErrorConstants.MODEL_ALREADY_EXISTS => (3, Forbidden, "SQLException")
      case ErrorConstants.MISSING_KEY => (4, Forbidden, "NotAuthorized")
      case ErrorConstants.INVALID_DEVKEY => (5, Forbidden, "Invalid")
      case ErrorConstants.SERVICE_DOWN => (500, InternalServerError, "InternalServerError")
      case _ => (10, InternalServerError, "InternalServerError")
    }
  }
}

