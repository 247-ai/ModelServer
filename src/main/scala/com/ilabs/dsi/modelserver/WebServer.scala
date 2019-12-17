package com.ilabs.dsi.modelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.ilabs.dsi.modelserver.utils.ModelServerConfig
import org.slf4j.LoggerFactory


/**
  * Created by samik on 12/3/17.
  */
object WebServer extends App with WebRoute
{
  // Set up logger
  val log = LoggerFactory.getLogger("WebServer")

  override implicit val system = ActorSystem("ModelServer", ModelServerConfig.config)
  override implicit val materializer = ActorMaterializer()
  implicit val executor = system.dispatcher

  val bindingFuture = Http().bindAndHandle(route, ModelServerConfig.get("http.interface"),ModelServerConfig.get("http.port").toInt)
  println(s"Model submitting Server online at http://${ModelServerConfig.get("http.interface")}:${ModelServerConfig.get("http.port")}/")
}
