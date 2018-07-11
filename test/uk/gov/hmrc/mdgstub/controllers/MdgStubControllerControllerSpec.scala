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

import java.io.InputStream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import play.api.http.{HeaderNames, Status}
import play.api.mvc.RawBuffer
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.duration._

class MdgStubControllerControllerSpec extends UnitSpec with WithFakeApplication {

  implicit val actorSystem = ActorSystem()

  implicit val materializer = ActorMaterializer()

  implicit val timeout: akka.util.Timeout = 10 seconds

  val controller = new MdgStubController()

  "POST /request" should {
    "return 204 provided valid XML request" in {

      val body = readStream(this.getClass.getResourceAsStream("/validRequest.xml"))

      val rawBody = RawBuffer.apply(body.length, ByteString(body))

      val request: FakeRequest[RawBuffer] =
        FakeRequest("POST", "/").withBody(rawBody).withHeaders((HeaderNames.CONTENT_TYPE, "application/xml"))

      val result = controller.requestTransfer()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.NO_CONTENT
      }

    }

    "return 400 provided request with invalid XML" in {

      val body = "invalidXML".getBytes

      val rawBody = RawBuffer.apply(body.length, ByteString(body))

      val fakeRequest =
        FakeRequest("POST", "/").withBody(rawBody).withHeaders((HeaderNames.CONTENT_TYPE, "application/xml"))

      val result = controller.requestTransfer()(fakeRequest)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.BAD_REQUEST
      }

    }

    "return 400 provided request with valid XML but not matching the schema" in {

      val body = readStream(this.getClass.getResourceAsStream("/requestNotCompliantWithSchema.xml"))

      val rawBody = RawBuffer.apply(body.length, ByteString(body))

      val request: FakeRequest[RawBuffer] =
        FakeRequest("POST", "/").withBody(rawBody).withHeaders((HeaderNames.CONTENT_TYPE, "application/xml"))

      val result = controller.requestTransfer()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.BAD_REQUEST
      }

    }

    "return 400 provided request with valid XML but with other schema" in {

      val body =
        """
          <html xmlns="http://www.w3.org/1999/xhtml" lang="en" xml:lang="en"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.w3.org/1999/xhtml
                                    http://www.w3.org/2002/08/xhtml/xhtml1-strict.xsd"></html>

        """.getBytes

      val rawBody = RawBuffer.apply(body.length, ByteString(body))

      val request: FakeRequest[RawBuffer] =
        FakeRequest("POST", "/").withBody(rawBody).withHeaders((HeaderNames.CONTENT_TYPE, "application/xml"))

      val result = controller.requestTransfer()(request)

      withClue(Helpers.contentAsString(result)) {
        status(result) shouldBe Status.BAD_REQUEST
      }

    }
  }

  private def readStream(stream: InputStream): Array[Byte] =
    Iterator.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray

}
