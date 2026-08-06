/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.http.Status.NOT_FOUND
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.agentregistration.shared.Crn
import uk.gov.hmrc.agentregistration.shared.companieshouse.CompaniesHouseDateOfBirth
import uk.gov.hmrc.agentregistration.shared.companieshouse.CompaniesHouseOfficer
import uk.gov.hmrc.agentregistration.shared.companieshouse.CompaniesHouseOfficerIdentification
import uk.gov.hmrc.agentregistration.shared.companieshouse.CompaniesHouseOfficerRole
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistrationfrontend.config.AppConfig
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import java.time.LocalDate

@Singleton
class CompaniesHouseApiProxyConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(using ExecutionContext)
extends Connector:

  private val baseUrl: String = appConfig.companiesHouseApiProxyBaseUrl

  def getCompaniesHouseOfficers(
    crn: Crn
  )(using RequestHeader): Future[Seq[CompaniesHouseOfficer]] =
    val url: URL = url"$baseUrl/companies-house-api-proxy/company/${crn.value}/officers"
    fetchCompaniesHouseOfficers(url)

  def getCompaniesHouseOfficers(
    crn: Crn,
    surname: String
  )(using RequestHeader): Future[Seq[CompaniesHouseOfficer]] =
    val url: URL = url"$baseUrl/companies-house-api-proxy/company/${crn.value}/officers?surname=$surname"
    fetchCompaniesHouseOfficers(url)

  private def fetchCompaniesHouseOfficers(
    url: URL
  )(using RequestHeader): Future[Seq[CompaniesHouseOfficer]] = http
    .get(url)
    .execute[HttpResponse]
    .map: response =>
      response.status match
        case status if is2xx(status) => (response.json \ "items").as[Seq[CompaniesHouseOfficer]]
        case status if status === NOT_FOUND => Seq.empty
        case status =>
          Errors.throwUpstreamErrorResponse(
            httpMethod = "GET",
            url = url,
            status = status,
            response = response
          )
    .andLogOnFailure(s"Failed to retrieve officers from Companies House for $url")

  private given Reads[CompaniesHouseOfficer] =
    (
      (__ \ "name").read[String] and
        (__ \ "date_of_birth").readNullable[CompaniesHouseDateOfBirth] and
        (__ \ "resigned_on").readNullable[LocalDate](using Reads.localDateReads("yyyy-MM-dd")) and
        (__ \ "officer_role").readNullable[CompaniesHouseOfficerRole] and
        (__ \ "identification").readNullable[CompaniesHouseOfficerIdentification]
    )(CompaniesHouseOfficer.apply)
