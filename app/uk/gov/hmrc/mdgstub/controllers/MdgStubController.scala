/*
 * Copyright 2018 HM Revenue & Customs
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

import java.io.{ByteArrayInputStream, StringReader}

import javax.inject.Singleton
import org.xml.sax.SAXParseException
import play.api.mvc._
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.xml._

@Singleton()
class MdgStubController extends BaseController {

  def requestTransfer() = Action.async(parse.raw) { implicit request =>

    val xml = new String(request.body.asBytes().get.toArray)

    validXml(xml) match {
      case Success(_) =>
        if (checkIfSimulatedFailure(xml)) {
          Future.successful(InternalServerError("Simulated failure"))
        } else {
          Future.successful(NoContent)
        }
      case Failure(error) =>
        Future.successful(BadRequest(error.getMessage))
    }
  }



  private def validXml(body: String): Try[Unit] = {

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

          override def error(e: SAXParseException): Unit =
            throw e

        }

    }

    Try(xmlLoader.load(new StringReader(body)))

  }

  private def checkIfSimulatedFailure(body : String): Boolean = {
    val parsedXml = scala.xml.XML.loadString(body)
    val properties = for {
      property: Node <- parsedXml \ "properties" \ "property"
      name <- property \ "name"
      value <- property \ "value"
    } yield (name.text, value.text)

    properties.contains(("SHOULD_FAIL", "true"))

  }

}
