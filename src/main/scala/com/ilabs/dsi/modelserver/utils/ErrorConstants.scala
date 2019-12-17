package com.ilabs.dsi.modelserver.utils

object ErrorConstants {

  val USER_ALREADY_EXISTS = "User already exists"
  val USER_NOT_FOUND = "User not found for the given devkey. Devkey may not be registered one."
  val MISSING_KEY = "Unauthorized due to missing tucana-devKey"
  val MODEL_ALREADY_EXISTS = "Model already exists. Try giving different modelId or version."
  val SERVICE_DOWN = "Service is down"
  val INVALID_DEVKEY = "DevKey is not valid. It should contain only alphanumeric values."
  def MISSING_PARAM(param: String): String = s"Missing params: $param"
}
