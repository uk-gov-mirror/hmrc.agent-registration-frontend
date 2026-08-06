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

package uk.gov.hmrc.agentregistrationfrontend.testsupport

import play.api.libs.ws.DefaultBodyWritables.writeableOf_urlEncodedForm
import play.api.libs.ws.DefaultWSCookie
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSCookie
import play.api.libs.ws.WSRequest
import play.api.libs.ws.WSResponse
import play.api.mvc.AnyContent
import play.api.mvc.Request
import play.api.mvc.Session
import play.api.mvc.SessionCookieBaker
import play.api.test.FakeRequest
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.SessionCookieCrypto

import scala.annotation.nowarn

trait WsHelper:
  self: ISpec =>

  private val ws: WSClient = app.injector.instanceOf[WSClient]

  def get[T](
    uri: String,
    cookies: Seq[WSCookie] = Seq.empty
  ): WSResponse =
    buildClient(
      path = uri,
      cookies = cookies
    )
      .withHttpHeaders("Authorization" -> "Bearer 123")
      .get()
      .futureValue

  def post(
    uri: String,
    cookies: Seq[WSCookie] = Seq.empty
  )(body: Map[String, Seq[String]]): WSResponse =
    buildClient(
      path = uri,
      cookies = cookies
    )
      .withHttpHeaders(
        "Authorization" -> "Bearer 123",
        "Csrf-Token" -> "nocheck"
      )
      .post(body)
      .futureValue

  def delete[T](
    uri: String,
    cookies: Seq[WSCookie] = Seq.empty
  ): WSResponse =
    buildClient(
      path = uri,
      cookies = cookies
    )
      .withHttpHeaders("Authorization" -> "Bearer 123")
      .delete()
      .futureValue

  def getUnauthenticated[T](
    uri: String,
    cookies: Seq[WSCookie] = Seq.empty
  ): WSResponse =
    buildUnauthenticatedClient(
      path = uri,
      cookies = cookies
    )
      .get()
      .futureValue

  def postUnauthenticated(
    uri: String,
    cookies: Seq[WSCookie] = Seq.empty
  )(body: Map[String, Seq[String]]): WSResponse =
    buildUnauthenticatedClient(
      path = uri,
      cookies = cookies
    )
      .withHttpHeaders("Csrf-Token" -> "nocheck")
      .post(body)
      .futureValue

  val baseUrl: String = "/agent-registration"

  @nowarn
  private def buildUnauthenticatedClient(
    path: String,
    cookies: Seq[WSCookie] = Seq.empty
  ): WSRequest = {
    ws
      .url(s"http://localhost:$port$baseUrl${path.replace(baseUrl, "")}")
      .withFollowRedirects(false)
      .withCookies(cookies*)
  }

  @nowarn
  private def buildClient(
    path: String,
    cookies: Seq[WSCookie] = Seq.empty
  ): WSRequest =
    val allCookies =
      if (cookies.nonEmpty)
        cookies
      else
        Seq(
          DefaultWSCookie("PLAY_LANG", "en"),
          mockSessionCookie
        )
    ws
      .url(s"http://localhost:$port$baseUrl${path.replace(baseUrl, "")}")
      .withFollowRedirects(false)
      .withCookies(allCookies*)

  val sessionHeaders: Map[String, String] = Map(
    SessionKeys.lastRequestTimestamp -> System.currentTimeMillis().toString,
    SessionKeys.authToken -> "mock-bearer-token",
    SessionKeys.sessionId -> "session-id-123"
  )

  implicit val request: Request[AnyContent] = FakeRequest()
    .withSession(sessionHeaders.toSeq*)
    .withFormUrlEncodedBody()

  def mockSessionCookie: WSCookie =

    val cookieCrypto = app.injector.instanceOf[SessionCookieCrypto]
    val cookieBaker = app.injector.instanceOf[SessionCookieBaker]
    val sessionCookie = cookieBaker.encodeAsCookie(Session(sessionHeaders))
    val encryptedValue = cookieCrypto.crypto.encrypt(PlainText(sessionCookie.value))
    val cookie = sessionCookie.copy(value = encryptedValue.value)

    new WSCookie():
      override def name: String = cookie.name
      override def value: String = cookie.value
      override def domain: Option[String] = cookie.domain
      override def path: Option[String] = Some(cookie.path)
      override def maxAge: Option[Long] = cookie.maxAge.map(_.toLong)
      override def secure: Boolean = cookie.secure
      override def httpOnly: Boolean = cookie.httpOnly

  extension (response: WSResponse)

    /** Extract all cookies from a WSResponse for use in subsequent requests.
      */
    def extractCookies: scala.collection.immutable.Seq[WSCookie] = response.cookies.toList
