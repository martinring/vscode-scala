package net.flatmap.vscode

import io.circe.generic.semiauto._

case class ScalaConfig()

object ScalaConfig {
  implicit def encoder = deriveEncoder[ScalaConfig]
  implicit def decoder = deriveDecoder[ScalaConfig]
}

case class Config(scala: Option[ScalaConfig])

object Config {
  implicit def encoder = deriveEncoder[Config]
  implicit def decoder = deriveDecoder[Config]
}


