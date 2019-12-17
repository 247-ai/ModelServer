package com.ilabs.dsi.modelserver.functionalTests

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, MediaTypes, Multipart}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestDuration
import akka.util.ByteString
import com.ilabs.dsi.modelserver.WebRoute
import com.ilabs.dsi.modelserver.utils.ModelServerConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.io.File

class WebApiTests extends WordSpec with ScalatestRouteTest with Matchers with ScalaFutures with WebRoute {

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(100.seconds dilated)
  val db = Database.forConfig("db", ModelServerConfig.config)
  var devkey = ""
  // Create the tables which will be used in model server
  Await.result(db.run(sqlu"create table if not exists users ( userId varchar(200) not null primary key,devKey varchar(200) not null unique, registerDate DATETIME not null, isAdmin boolean);"),Duration.Inf)
  Await.result(db.run(sqlu"create table if not exists tucanamodels ( modelId varchar(200) not null, version varchar(100) not null, userId varchar(200) not null references users (userId), description varchar(250), lastUpdateTimestamp text, model longblob, dataSchema varchar(20000), primary key (modelId, version));"),Duration.Inf)

  // Test cases for registering the user and getting user's info
  "Path /modelserver/v1/users" should {

    Await.result(db.run(sqlu"delete from users where userId='tucanaUser'"), Duration.Inf)

    val webInput = ByteString("""{"userId":"tucanaUser","isAdmin":"true"}""".stripMargin)
    val webInputMissingParam = ByteString("""{"userId":"tucanaUser"}""".stripMargin)
    val postRequest = HttpRequest(
      HttpMethods.POST,
      uri = "/modelserver/v1/users",
      entity = HttpEntity(MediaTypes.`application/json`, webInput))

    val postRequestMissingParam = HttpRequest(
      HttpMethods.POST,
      uri = "/modelserver/v1/users",
      entity = HttpEntity(MediaTypes.`application/json`, webInputMissingParam))

    "register the user" in {
      postRequest ~> route ~> check {
        val result = responseAs[String]
        devkey = result.substring(33,43)
        status.isSuccess() shouldEqual true
      }
    }

    "return error saying missing required parameters " in {
      postRequestMissingParam ~> route ~> check {
        responseAs[String] shouldEqual "{\"error\":{\"code\":2,\"message\":\"Missing params: isAdmin\",\"type\":\"InputError\"}}".stripMargin
      }
    }

    val getRequest = HttpRequest(
      HttpMethods.GET,
      uri = "/modelserver/v1/users")

    "return the userId for the given devkey" in {
      getRequest  ~> RawHeader("tucana-devKey",devkey) ~> route ~> check {
        responseAs[String] shouldEqual "{\"userId\":\"tucanaUser\"}\n".stripMargin
        status.isSuccess() shouldEqual true
      }
    }

    "return the user is not registered" in {
      getRequest  ~> RawHeader("tucana-devKey","123asdf456") ~> route ~> check {
        responseAs[String] shouldEqual "{\"error\":{\"code\":1,\"message\":\"User not found for the given devkey. Devkey may not be registered one.\",\"type\":\"SQLException\"}}".stripMargin
      }
    }

    "return tucana devkey is not valid" in {
      getRequest  ~> RawHeader("tucana-devKey","123asdf4-6") ~> route ~> check {
        responseAs[String] shouldEqual "{\"error\":{\"code\":5,\"message\":\"DevKey is not valid. It should contain only alphanumeric values.\",\"type\":\"Invalid\"}}".stripMargin
      }
    }

    "return tucana devkey is not found in header" in {
      getRequest ~> route ~> check {
        responseAs[String] shouldEqual "{\"error\":{\"code\":4,\"message\":\"Unauthorized due to missing tucana-devKey\",\"type\":\"NotAuthorized\"}}".stripMargin
      }
    }
  }

  //Test cases for getting particular user's models
  "Path /modelserver/v1/models " should {
    val getRequest = HttpRequest(
      HttpMethods.GET,
      uri = "/modelserver/v1/models")

    "return the models belong to the given devkey" in {
      getRequest  ~> RawHeader("tucana-devkey",devkey) ~> route ~> check {
        responseAs[String] shouldEqual "[]\n".stripMargin
        status.isSuccess() shouldEqual true
      }
    }
  }

  //Test cases for uploading the models into db
  "Path /modelserver/v1/models " should {

    Await.result(db.run(sqlu"delete from tucanamodels where modelId='tucana1'"), Duration.Inf)

    val modelFile = Multipart.FormData.BodyPart.Strict("file", HttpEntity(MediaTypes.`application/octet-stream`,File("flashml-noPage.zip").bytes().toArray))
    val schema = Multipart.FormData.BodyPart.Strict("schema","""{"fields":[{"type":"string","name":"lineText"}]}""".stripMargin)
    val modelId = Multipart.FormData.BodyPart.Strict("modelId","tucana1")
    val version = Multipart.FormData.BodyPart.Strict("version","v1")
    val description = Multipart.FormData.BodyPart.Strict("description","This is a logistic regression model")
    val formData = Multipart.FormData(modelId,version,description,schema,modelFile)

    "upload the given model to the database" in {
      Post("/modelserver/v1/models",formData) ~> RawHeader("tucana-devkey",devkey) ~> route ~> check {
        responseAs[String] shouldEqual "{\"status\":\"success\",\"modelId\":\"tucana1\",\"version\":\"v1\"}\n".stripMargin
        status.isSuccess() shouldEqual true
      }
    }

    "return duplicate upload of the model" in {
      Post("/modelserver/v1/models",formData) ~> RawHeader("tucana-devkey",devkey) ~> route ~> check {
        responseAs[String] shouldEqual "{\"error\":{\"code\":3,\"message\":\"Model already exists. Try giving different modelId or version.\",\"type\":\"SQLException\"}}".stripMargin
        }
    }
  }

}
