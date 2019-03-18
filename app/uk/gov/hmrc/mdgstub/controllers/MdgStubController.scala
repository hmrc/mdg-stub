/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import org.xml.sax.SAXParseException
import play.api.Logger
import play.api.libs.concurrent.Promise
import play.api.mvc._
import uk.gov.hmrc.mdgstub.util.{Eithers, PerfLogger}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml._

@Singleton()
class MdgStubController @Inject() (implicit ec: ExecutionContext) extends BaseController {

  private val availabilityMode = raw"(\d{3}):(\d+):(\d+)".r

  def requestTransfer() = Action.async(parse.raw) { implicit request =>

    val xmlStr = new String(request.body.asBytes().get.toArray)

    Logger.info(s"Received request: $xmlStr")

    validateXml(xmlStr) match {
      case Right(xmlElem) =>

        withActiveAvailabilityMode(xmlElem) {
          case Right(Some(AvailabilityMode(status,delay,_))) if (delay.length > 0) => Promise.timeout(Status(status.toInt), delay)
          case Right(Some(AvailabilityMode(status,_,_)))     => Future.successful((Status(status.toInt)))
          case Right(None)                                   => Future.successful(NoContent)
          case Left(error)                                   => Future.successful(BadRequest(error))
        }

      case Left((xmlStr, error)) => {
        Logger.warn(s"Failed to validate xml: [${xmlStr}]. Error was: [${error.getMessage}].")
        Future.successful(BadRequest(s"Failed to parse xml. Error was: [${error.getMessage}]."))
      }
    }
  }

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
    } match {
      case Success(xml)   => Right(xml)
      case Failure(error) => Left((content, error))
    }
  }

  private def withActiveAvailabilityMode(xml: Elem)(block: Either[String,Option[AvailabilityMode]] => Future[Result]): Future[Result] = {

    val properties = for {
      property: Node <- xml \ "properties" \ "property"
      name <- property \ "name"
      value <- property \ "value"
    } yield (name.text, value.text)

    val availabilityModeMaybe: Option[String] = properties.collectFirst {
      case ("AVAILABILITY", mode) => mode
    }

    block(findActiveAvailabilityMode(availabilityModeMaybe))
  }

  private def findActiveAvailabilityMode(availabilityMaybe: Option[String]): Either[String,Option[AvailabilityMode]] = {

    def tokenToAvailabilityMode(token: String): Either[String, AvailabilityMode] = {
      token match {
        case availabilityMode(status, delay, until) => Right(AvailabilityMode(status, delay, until))
        case _                                      => Left(s"Could not parse token: [${token}].")
      }
    }

    // Convert the 'availability mode' String into either a collection of AvailabilityMode instances, or a parse error message.
    //
    (for {
      availability <- availabilityMaybe

      availabilityTokens: Seq[String] = availability.split(";")

      availabilities: Seq[Either[String, AvailabilityMode]] = availabilityTokens.map ( tokenToAvailabilityMode )

      availabilityModesOrParseError = Eithers.sequence(availabilities)
    } yield {
      // Find the first Availability where the 'until' field is still in the future.
      availabilityModesOrParseError.right.map { _.find(av => Instant.now.isBefore(av.until)) }
    })
      .getOrElse(Right(None)) // If the AVAILABILITY property wasn't passed in the request.
  }
}

final case class AvailabilityMode(status: String, delay: Duration, until: Instant)

object AvailabilityMode {
  def apply(status: String, delay: String, until: String): AvailabilityMode = {
    AvailabilityMode(status, delay.toLong seconds, Instant.ofEpochSecond(until.toLong))
  }
}