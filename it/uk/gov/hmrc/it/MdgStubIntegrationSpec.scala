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
package uk.gov.hmrc.it

import java.time.Instant
import org.scalatest.Matchers
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class MdgStubIntegrationSpec extends UnitSpec with Matchers with GuiceOneServerPerSuite {
  "MDG Stub" when {
    "available" should {
      "return successful response without delay" in {
        val delay = 0 seconds

        val requestBodyXmlString = Requests.available(delay)

        val request = FakeRequest(POST, "/mdg-stub/request").withBody(requestBodyXmlString).withHeaders((CONTENT_TYPE, XML))

        val startTime = Instant.now

        val response = route(app, request).get

        // Check the 'delay' period of time has elapsed since the request was sent.
        Await.result(response map { _ => Instant.now should be < startTime.plusMillis(1000) }, delay.plus(500 millis))

        status(response) shouldBe NO_CONTENT
      }

      "return successful response delayed 1 second" in {
        val delay = 1 second

        val requestBodyXmlString = Requests.available(delay)

        val request = FakeRequest(POST, "/mdg-stub/request").withBody(requestBodyXmlString).withHeaders((CONTENT_TYPE, XML))

        val startTime = Instant.now

        val response = route(app, request).get

        // Check the 'delay' period of time has elapsed since the request was sent.
        Await.result(response map { _ => Instant.now should be > startTime.plusMillis(delay toMillis) }, delay.plus(500 millis))

        status(response) shouldBe NO_CONTENT
      }

      "return successful response for request containing no AVAILABILITY property" in {
        val requestBodyXmlString = Requests.available

        val request = FakeRequest(POST, "/mdg-stub/request").withBody(requestBodyXmlString).withHeaders((CONTENT_TYPE, XML))

        val startTime = Instant.now

        val response = route(app, request).get

        // Check the 'delay' period of time has elapsed since the request was sent.
        Await.result(response map { _ => Instant.now should be < startTime.plusMillis(1000) }, 500 millis)

        status(response) shouldBe NO_CONTENT
      }
    }

    "unavailable" should {
      "return a 503 error without delay" in {
        val delay = 0 seconds

        val requestBodyXmlString = Requests.unavailable(delay)

        val request = FakeRequest(POST, "/mdg-stub/request").withBody(requestBodyXmlString).withHeaders((CONTENT_TYPE, XML))

        val startTime = Instant.now

        val response = route(app, request).get

        // Check the 'delay' period of time has elapsed since the request was sent.
        Await.result(response map { _ => Instant.now should be < startTime.plusMillis(1000) }, 500 millis)

        status(response) shouldBe SERVICE_UNAVAILABLE
      }

      "return a 503 error delayed 1 second" in {
        val delay = 1 second

        val requestBodyXmlString = Requests.unavailable(delay)

        val request = FakeRequest(POST, "/mdg-stub/request").withBody(requestBodyXmlString).withHeaders((CONTENT_TYPE, XML))

        val startTime = Instant.now

        val response = route(app, request).get

        // Check the 'delay' period of time has elapsed since the request was sent.
        Await.result(response map { _ => Instant.now should be > startTime.plusMillis(delay toMillis) }, delay.plus(500 millis))

        status(response) shouldBe SERVICE_UNAVAILABLE
      }
    }

    "sent a request containing an invalidly formatted AVAILABILITY property" should {
      "return 400" in {
        val delay = 0 seconds

        val requestBodyXmlString = Requests.invalid

        val request = FakeRequest(POST, "/mdg-stub/request").withBody(requestBodyXmlString).withHeaders((CONTENT_TYPE, XML))

        val startTime = Instant.now

        val response = route(app, request).get

        // Check the 'delay' period of time has elapsed since the request was sent.
        Await.result(response map { _ => Instant.now should be < startTime.plusMillis(1000) }, delay.plus(500 millis))

        status(response) shouldBe BAD_REQUEST
      }
    }
  }
}

object Requests {
  def available(delay: Duration, until: Long = Instant.MAX.getEpochSecond) =
    populateRequest(xmlTemplate, NO_CONTENT, delay, until)


  def unavailable(delay: Duration, until: Long = Instant.MAX.getEpochSecond) =
    populateRequest(xmlTemplate, SERVICE_UNAVAILABLE, delay, until)

  private def populateRequest(template: String, status: Int, delay: Duration, until: Long): String =
    template.replace("{STATUS}", status.toString)
      .replace("{DELAY}",  delay.toSeconds.toString)
      .replace("{UNTIL}",  until.toString)

  lazy val xmlTemplate   = scala.xml.XML.load(this.getClass.getResource("/template/requestWithAvailabilityTemplate.xml")).toString()
  lazy val available = scala.xml.XML.load(this.getClass.getResource("/template/requestWithoutAvailabilityTemplate.xml")).toString()
  lazy val invalid = xmlTemplate.replace("{STATUS}","NO_CONTENT").replace("{DELAY}","1").replace("{UNTIL}", "BAD_VALUE")
}