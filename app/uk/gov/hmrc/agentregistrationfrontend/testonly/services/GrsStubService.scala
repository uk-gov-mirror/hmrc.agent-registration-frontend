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

package uk.gov.hmrc.agentregistrationfrontend.testonly.services

import play.api.mvc.Request
import uk.gov.hmrc.agentregistration.shared.BusinessType
import uk.gov.hmrc.agentregistration.shared.BusinessType.*
import uk.gov.hmrc.agentregistration.shared.Nino
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.=!=
import uk.gov.hmrc.agentregistration.shared.util.SafeEquals.===
import uk.gov.hmrc.agentregistrationfrontend.model.grs.JourneyData
import uk.gov.hmrc.agentregistrationfrontend.testonly.connectors.AgentsExternalStubsConnector
import uk.gov.hmrc.agentregistrationfrontend.testonly.model.*

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class GrsStubService @Inject() (
  agentsExternalStubsConnector: AgentsExternalStubsConnector
)(using ExecutionContext):

  def storeStubsData(
    businessType: BusinessType,
    journeyData: JourneyData,
    deceased: Boolean
  )(using Request[?]): Future[Unit] =

    val maybeSoleTraderUserForDeceasedCheck: Future[Unit] =
      (businessType, journeyData.nino) match
        case (SoleTrader, Some(nino: Nino)) =>
          agentsExternalStubsConnector.createIndividualUserForDeceasedCheck(
            nino = nino,
            assignedPrincipalEnrolments = Seq("HMRC-MTD-IT"),
            deceased = deceased
          )
        case _ => Future.successful(())

    val utr: String = journeyData.sautr.map(_.value).getOrElse(journeyData.ctutr.map(_.value).getOrElse(""))
    // to get BPR fetches working we need to store a BPR in stubs using GRS data
    val businessPartnerRecord: Future[Unit] = agentsExternalStubsConnector.storeBusinessPartnerRecord(
      bpr = BusinessPartnerRecord(
        businessPartnerExists = true,
        uniqueTaxReference = Some(utr),
        utr = Some(utr),
        safeId = journeyData.registration.registeredBusinessPartnerId.map(_.value).getOrElse(""),
        isAnIndividual = businessType === SoleTrader,
        isAnOrganisation = businessType =!= SoleTrader,
        individual =
          if businessType === SoleTrader then
            Some(
              uk.gov.hmrc.agentregistrationfrontend.testonly.model.Individual(
                firstName = journeyData.fullName.map(_.firstName).getOrElse("Test"),
                lastName = journeyData.fullName.map(_.lastName).getOrElse("User"),
                dateOfBirth = journeyData.dateOfBirth.map(_.toString).getOrElse("1990-01-01")
              )
            )
          else None,
        organisation =
          businessType match
            case BusinessType.Partnership.LimitedLiabilityPartnership | BusinessType.LimitedCompany | BusinessType.Partnership.LimitedPartnership | BusinessType.Partnership.ScottishLimitedPartnership =>
              Some(Organisation(
                organisationName = journeyData.companyProfile.map(_.companyName).getOrElse("BPR Test Org"),
                organisationType = "5T"
              ))
            case Partnership.GeneralPartnership | BusinessType.Partnership.ScottishPartnership =>
              Some(Organisation(
                organisationName = "Electronicsson Group", // hard coded to avoid random generation of name - we don't get this from GRS
                organisationType = "5T"
              ))
            case _ => None,
        crn =
          businessType match
            case BusinessType.Partnership.LimitedLiabilityPartnership | BusinessType.LimitedCompany | BusinessType.Partnership.LimitedPartnership | BusinessType.Partnership.ScottishLimitedPartnership =>
              journeyData.companyProfile.map(_.companyNumber.value)
            case _ => None,
        addressDetails = AddressDetails(
          addressLine1 = "1 Test Street",
          addressLine2 = Some("Test Area"),
          addressLine3 = None,
          addressLine4 = None,
          postalCode = journeyData.postcode.getOrElse("TE1 1ST"),
          countryCode = "GB"
        ),
        contactDetails = Some(ContactDetails(
          primaryPhoneNumber = Some("01234567890"),
          emailAddress = Some("test@example.com")
        ))
      )
    )

    for {
      _ <- maybeSoleTraderUserForDeceasedCheck
      _ <- businessPartnerRecord
    } yield ()
