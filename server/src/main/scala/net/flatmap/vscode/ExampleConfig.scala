package net.flatmap.vscode

import io.circe.generic.semiauto._

case class ExampleConfig(maxNumberOfProblems: Option[Int])

object ExampleConfig {
  implicit def encoder = deriveEncoder[ExampleConfig]
  implicit def decoder = deriveDecoder[ExampleConfig]
}

case class Config(languageServerExample: Option[ExampleConfig])

object Config {
  implicit def encoder = deriveEncoder[Config]
  implicit def decoder = deriveDecoder[Config]
}


