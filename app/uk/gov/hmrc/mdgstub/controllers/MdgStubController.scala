/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mdgstub.controllers

import java.io.StringReader
import java.time.Instant

import org.apache.pekko.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import org.xml.sax.SAXParseException
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.mdgstub.util.Eithers
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.language.postfixOps
import scala.xml._

@Singleton()
class MdgStubController @Inject()(
  actorSystem: ActorSystem,
  cc: ControllerComponents
)(using
  ExecutionContext
) extends BackendController(cc) {

  private val logger = Logger(this.getClass)
  private val availabilityMode = raw"(\d{3}):(\d+):(\d+)".r

  def requestTransfer() =
    Action.async(parse.raw) { implicit request =>

      val xmlStr = new String(request.body.asBytes().get.toArray)

      logger.info(s"Received request: $xmlStr")

      validateXml(xmlStr) match
        case Right(xmlElem) =>
          withActiveAvailabilityMode(xmlElem) {
            case Right(Some(AvailabilityMode(status, delay, _))) if (delay.length > 0) => after(delay, Future.successful(Status(status.toInt)))
            case Right(Some(AvailabilityMode(status, _, _)))     => Future.successful(Status(status.toInt))
            case Right(None)                                     => Future.successful(NoContent)
            case Left(error)                                     => Future.successful(BadRequest(error))
          }

        case Left((xmlStr, error)) =>
          logger.warn(s"Failed to validate xml: [${xmlStr}]. Error was: [${error.getMessage}].")
          Future.successful(BadRequest(s"Failed to parse xml. Error was: [${error.getMessage}]."))
    }

  private def after[A](delay: FiniteDuration, future: Future[A]): Future[A] =
    org.apache.pekko.pattern.after(delay, actorSystem.scheduler)(future)

  private def validateXml(content : String): Either[(String, Throwable),Elem] = {

    val schemaLang = javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI

    val xsdStream =
      new javax.xml.transform.stream.StreamSource(this.getClass.getResourceAsStream("/mdg-schema.xsd"))

    val schema = javax.xml.validation.SchemaFactory.newInstance(schemaLang).newSchema(xsdStream)

    val factory = javax.xml.parsers.SAXParserFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.setSchema(schema)

    val validatingParser = factory.newSAXParser()

    val xmlLoader = new scala.xml.factory.XMLLoader[scala.xml.Elem] {
      override def parser = validatingParser

      override def adapter =
        new scala.xml.parsing.NoBindingFactoryAdapter {
          override def error(e: SAXParseException): Unit = throw e
        }
    }

    Try {
      xmlLoader.load(new StringReader(content))
    } match
      case Success(xml)   => Right(xml)
      case Failure(error) => Left((content, error))
  }

  private def withActiveAvailabilityMode(xml: Elem)(block: Either[String,Option[AvailabilityMode]] => Future[Result]): Future[Result] = {

    val availability = for
      fromProperties <- availabilityFromProperties(xml)
      fromFilename   <- Right(availabilityFromFilename(xml))
    yield
      fromFilename.orElse(fromProperties)

    block(availability)
  }

  private def availabilityFromFilename(xml: Elem) = {
    val FailureSimulationInFilename = "fail(\\d{3})\\..*".r

    val availabilityFromFilename =
      (for
        case FailureSimulationInFilename(httpStatus) <- (xml \ "sourceFileName").map(_.text)
       yield AvailabilityMode(httpStatus, 0 seconds, Instant.MAX)
      ).headOption

    availabilityFromFilename
  }

  private def availabilityFromProperties(xml: Elem) = {
    val properties =
      for
        property: Node <- xml \ "properties" \ "property"
        name  <- property \ "name"
        value <- property \ "value"
      yield (name.text, value.text)

    val rawAvailabilityFromProperties: Option[String] =
      properties.collectFirst {
        case ("AVAILABILITY", mode) => mode
      }

    findActiveAvailabilityMode(rawAvailabilityFromProperties)
  }

  private def findActiveAvailabilityMode(availabilityMaybe: Option[String]): Either[String,Option[AvailabilityMode]] = {

    def tokenToAvailabilityMode(token: String): Either[String, AvailabilityMode] =
      token match
        case availabilityMode(status, delay, until) => Right(AvailabilityMode(status, delay, until))
        case _                                      => Left(s"Could not parse token: [${token}].")

    // Convert the 'availability mode' String into either a collection of AvailabilityMode instances, or a parse error message.
    //
    (for
      availability <- availabilityMaybe

      availabilityTokens: Seq[String] = availability.split(";").toSeq

      availabilities: Seq[Either[String, AvailabilityMode]] = availabilityTokens.map ( tokenToAvailabilityMode )

      availabilityModesOrParseError = Eithers.sequence(availabilities)
     yield
      // Find the first Availability where the 'until' field is still in the future.
      availabilityModesOrParseError.map { _.find(av => Instant.now.isBefore(av.until)) }
    ).getOrElse(Right(None)) // If the AVAILABILITY property wasn't passed in the request.
  }
}

case class AvailabilityMode(
  status: String,
  delay : FiniteDuration,
  until : Instant
)

object AvailabilityMode {
  def apply(status: String, delay: String, until: String): AvailabilityMode =
    AvailabilityMode(status, delay.toLong seconds, Instant.ofEpochSecond(until.toLong))
}
