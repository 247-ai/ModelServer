package com.ilabs.dsi.modelserver.utils

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}

/**
  * @since 4/6/18
  */
object ModelServerConfig {

  // Making var for tests override
  //This is def to support hotloading of the config file
  def config: Config = ConfigFactory.parseFile(new File("model-server-config.conf"))

  def get(key: String): String = config.getAnyRef(key.toString).toString

}
