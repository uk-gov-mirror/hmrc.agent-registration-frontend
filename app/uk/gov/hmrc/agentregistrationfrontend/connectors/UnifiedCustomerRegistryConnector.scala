/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentregistrationfrontend.connectors

import uk.gov.hmrc.agentregistration.shared.Nino
import uk.gov.hmrc.agentregistration.shared.SaUtr
import uk.gov.hmrc.agentregistration.shared.UcrIdentifiers
import uk.gov.hmrc.agentregistration.shared.Utr
import uk.gov.hmrc.agentregistrationfrontend.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class UnifiedCustomerRegistryConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig: AppConfig
)(using ExecutionContext)
extends Connector:

  private val baseUrl: String = appConfig.agentRegistrationBaseUrl + "/agent-registration"

  def getOrganisationIdentifiers(
    utr: Utr
  )(using RequestHeader): Future[Option[UcrIdentifiers]] =
    val url: URL = url"$baseUrl/unified-customer-registry/organisation/utr/${utr.value}"
    httpClient
      .get(url)
      .execute[HttpResponse]
      .map: response =>
        response.status match
          case status if is2xx(status) => Some(response.json.as[UcrIdentifiers])
          case _ =>
            logger.warn("Failed to get organisation identifiers")
            None

  def getIndividualIdentifiersByNino(
    nino: Nino
  )(using RequestHeader): Future[Option[UcrIdentifiers]] =
    val sanitisedNino = nino.value.replaceAll("\\s", "")
    val url: URL = url"$baseUrl/unified-customer-registry/individual/nino/$sanitisedNino"
    httpClient
      .get(url)
      .execute[HttpResponse]
      .map: response =>
        response.status match
          case status if is2xx(status) => Some(response.json.as[UcrIdentifiers])
          case _ =>
            logger.warn("Failed to get individual identifiers by nino")
            None

  def getIndividualIdentifiersBySaUtr(
    saUtr: SaUtr
  )(using RequestHeader): Future[Option[UcrIdentifiers]] =
    val url: URL = url"$baseUrl/unified-customer-registry/individual/utr/${saUtr.value}"
    httpClient
      .get(url)
      .execute[HttpResponse]
      .map: response =>
        response.status match
          case status if is2xx(status) => Some(response.json.as[UcrIdentifiers])
          case _ =>
            logger.warn("Failed to get individual identifiers by sa-utr")
            None
