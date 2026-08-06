/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentregistrationfrontend.testonly.connectors

import uk.gov.hmrc.agentregistration.shared.Nino
import uk.gov.hmrc.agentregistrationfrontend.config.AppConfig
import uk.gov.hmrc.agentregistrationfrontend.connectors.Connector
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.BusinessPartnerRecord
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.LoginResponse
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.PlanetId
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.SignInRequest
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.User
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.UserId
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

/** Connector to agents external stubs service used in test environments only
  */
@Singleton
class AgentsExternalStubsConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig: AppConfig
)(using
  ExecutionContext
)
extends Connector:

  // Requires user to be logged in in stubs
  def storeBusinessPartnerRecord(
    bpr: BusinessPartnerRecord
  )(using
    request: RequestHeader
  ): Future[Unit] =
    val url: URL = url"$baseUrl/records/business-partner-record"
    httpClient
      .post(url)
      .withBody(Json.toJson(bpr))
      .execute[HttpResponse]
      .map: response =>
        response.status match
          case Status.CREATED => ()
          case status =>
            Errors.throwUpstreamErrorResponse(
              httpMethod = "POST",
              url = url,
              status = status,
              response = response
            )

  def createIndividualUserForDeceasedCheck(
    nino: Nino,
    assignedPrincipalEnrolments: Seq[String],
    deceased: Boolean = false
  )(using
    request: RequestHeader
  ): Future[Unit] =
    val user = User(
      userId = UserId.make(nino),
      planetId = None, // None will default to current planet - we require this for deceased check to work
      nino = Some(nino),
      deceased = Some(deceased)
    )
    createDeceasedCheckUser(user).map(_ => ())

  def signIn(signInRequest: SignInRequest): Future[LoginResponse] =
    val url: URL = url"$baseUrl/sign-in"
    given hc: HeaderCarrier = HeaderCarrier()
    httpClient
      .post(url)
      .withBody(Json.toJson(signInRequest))
      .execute[HttpResponse]
      .map: response =>
        response.status match
          case Status.CREATED | Status.ACCEPTED => LoginResponse.from(response)
          case status =>
            Errors.throwUpstreamErrorResponse(
              httpMethod = "POST",
              url = url,
              status = status,
              response = response,
              info = "sign-in problem"
            )

  def removeUser(
    userId: UserId,
    planetId: PlanetId
  )(using hc: HeaderCarrier): Future[Unit] =
    val url: URL = url"$baseUrl/users/${userId.value}"
    httpClient
      .delete(url)
      .setHeader(dummySessionForStubs(planetId)*)
      .execute[HttpResponse]
      .map: response =>
        response.status match
          case Status.NO_CONTENT | Status.OK => ()
          case Status.NOT_FOUND => ()
          case status =>
            Errors.throwUpstreamErrorResponse(
              httpMethod = "DELETE",
              url = url,
              status = status,
              response = response
            )

  def createUser(
    user: User,
    affinityGroup: Option[AffinityGroup] = None
  ): Future[Unit] =

    given hc: HeaderCarrier = HeaderCarrier()
    val queryParams: Seq[(String, String)] =
      user.planetId match
        case None => affinityGroup.map("affinityGroup" -> _.toString).toList
        case Some(planetId) => (("planetId" -> planetId.value)) :: affinityGroup.map("affinityGroup" -> _.toString).toList
    val url: URL = url"$baseUrl/users?$queryParams"

    httpClient
      .post(url)
      .withBody(Json.toJson(user))
      .execute[HttpResponse]
      .map: response =>
        response.status match
          case Status.CREATED | Status.OK => ()
          case Status.CONFLICT => () // ignore 409 errors (created user with duplicate ninos) from user stubs repo
          case status =>
            Errors.throwUpstreamErrorResponse(
              httpMethod = "POST",
              url = url,
              status = status,
              response = response
            )

  private def dummySessionForStubs(planetId: PlanetId): Seq[(String, String)] = Seq(
    "AuthenticatedSession-Planet-ID" -> planetId.value,
    "AuthenticatedSession-User-ID" -> "dummy",
    "AuthenticatedSession-Auth-Token" -> "dummy",
    "AuthenticatedSession-Provider-Type" -> "dummy"
  )
  def findUser(
    userId: UserId,
    planetId: PlanetId
  )(using RequestHeader): Future[Option[User]] =

    val url: URL = url"$baseUrl/users/${userId.value}"

    httpClient
      .get(url)
      .setHeader(
        dummySessionForStubs(planetId)*
      )
      .execute[HttpResponse]
      .map: response =>
        response.status match
          case Status.OK => Some(response.json.as[User])
          case Status.NOT_FOUND => None
          case status =>
            Errors.throwUpstreamErrorResponse(
              httpMethod = "GET",
              url = url,
              status = status,
              response = response,
              info = s"find user problem: $userId, $planetId"
            )

  private def createDeceasedCheckUser(
    user: User
  )(using hc: HeaderCarrier): Future[Unit] =
    val queryParams: Seq[(String, String)] = Seq(
      "affinityGroup" -> "Individual"
    )
    val url: URL = url"$baseUrl/users?$queryParams"
    httpClient
      .post(url)
      .withBody(Json.toJson(user))
      .execute[HttpResponse]
      .map: response =>
        response.status match
          case Status.CREATED | Status.OK => ()
          case status =>
            Errors.throwUpstreamErrorResponse(
              httpMethod = "POST",
              url = url,
              status = status,
              response = response
            )

  private val baseUrl: String = appConfig.agentsExternalStubsBaseUrl + "/agents-external-stubs"
