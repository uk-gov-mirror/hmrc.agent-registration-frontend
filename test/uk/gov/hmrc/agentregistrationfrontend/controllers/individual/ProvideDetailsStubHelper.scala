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

package uk.gov.hmrc.agentregistrationfrontend.controllers.individual

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.PersonReference
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistration.shared.risking.RiskingProgress
import uk.gov.hmrc.agentregistrationfrontend.testsupport.testdata.TdAll.tdAll
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.AgentRegistrationRiskingStubs
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.AgentRegistrationStubs
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.CitizenDetailsStub
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.providedetails.IndividualAuthStubs
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.providedetails.llp.AgentRegistrationIndividualProvidedDetailsStubs
import uk.gov.hmrc.auth.core.ConfidenceLevel

object ProvideDetailsStubHelper:

  def stubAuthAndFindApplicationAndProvidedDetails(
    agentApplication: AgentApplication,
    individualProvideDetails: IndividualProvidedDetails,
    isScr: Boolean = false,
    withBpr: Boolean = false,
    cl: ConfidenceLevel = ConfidenceLevel.L250
  ): StubMapping =

    if isScr
    then IndividualAuthStubs.stubAuthorise(responseBody = IndividualAuthStubs.responseBodyAsCl50())
    else IndividualAuthStubs.stubAuthoriseWithNinoAndSaUtr(cl)

    if withBpr
    then
      AgentRegistrationStubs.stubGetApplicationBusinessPartnerRecord(
        utr = tdAll.saUtr.asUtr, // doesn't matter we are using same utr as provided details, there is no conflict
        responseBody = tdAll.businessPartnerRecordResponse
      )
    else ()
    AgentRegistrationIndividualProvidedDetailsStubs.stubFindAllIndividualProvidedDetails(List(individualProvideDetails), agentApplication.agentApplicationId)
    AgentRegistrationStubs.stubFindApplicationByLinkId(tdAll.linkId, agentApplication)

  def stubAuthAndUpdateProvidedDetails(
    agentApplication: AgentApplication,
    individualProvidedDetails: IndividualProvidedDetails,
    updatedIndividualProvidedDetails: IndividualProvidedDetails,
    isScr: Boolean = false,
    withBpr: Boolean = false
  ): StubMapping =
    stubAuthAndFindApplicationAndProvidedDetails(
      agentApplication,
      individualProvidedDetails,
      isScr,
      withBpr
    )
    AgentRegistrationIndividualProvidedDetailsStubs.stubUpsertIndividualProvidedDetails(updatedIndividualProvidedDetails)

  def stubAuthAndFixIndividualProvidedDetails(
    agentApplication: AgentApplication,
    individualProvidedDetails: IndividualProvidedDetails,
    updatedIndividualProvidedDetails: IndividualProvidedDetails,
    isScr: Boolean = false
  ): StubMapping =
    stubAuthAndUpdateProvidedDetails(
      agentApplication,
      individualProvidedDetails,
      updatedIndividualProvidedDetails
    )
    AgentRegistrationStubs.stubGetApplicationBusinessPartnerRecord(
      utr = tdAll.saUtr.asUtr, // doesn't matter we are using same utr as provided details, there is no conflict
      responseBody = tdAll.businessPartnerRecordResponse
    )

  def stubAuthAndMatchIndividualProvidedDetails(
    agentApplication: AgentApplication,
    individualProvidedDetails: IndividualProvidedDetails,
    isScr: Boolean = false,
    cl: ConfidenceLevel = ConfidenceLevel.L250
  ): StubMapping =
    stubAuthAndFindApplicationAndProvidedDetails(
      agentApplication,
      individualProvidedDetails,
      isScr,
      cl = cl
    )
    CitizenDetailsStub.stubFindSaUtrAndDateOfBirth(
      nino = tdAll.nino,
      saUtr = tdAll.saUtr,
      firstName = tdAll.individualName.value.split(" ").headOption,
      lastName = tdAll.individualName.value.split(" ").lastOption
    )
    AgentRegistrationStubs.stubGetApplicationBusinessPartnerRecord(
      utr = tdAll.saUtr.asUtr, // doesn't matter we are using same utr as provided details, there is no conflict
      responseBody = tdAll.businessPartnerRecordResponse
    )

  def stubAuthAndClaimMatchedIndividualProvidedDetails(
    agentApplication: AgentApplication,
    individualProvidedDetails: IndividualProvidedDetails,
    updatedIndividualProvidedDetails: IndividualProvidedDetails
  ): StubMapping =
    stubAuthAndMatchIndividualProvidedDetails(agentApplication, individualProvidedDetails)
    AgentRegistrationIndividualProvidedDetailsStubs.stubUpsertIndividualProvidedDetails(updatedIndividualProvidedDetails)

  def verifyAuthAndFindApplicationAndProvidedDetails(withBpr: Boolean = false): Unit =
    IndividualAuthStubs.verifyAuthorise()
    AgentRegistrationIndividualProvidedDetailsStubs.verifyFindAllForApplicationId(tdAll.agentApplicationId)
    AgentRegistrationStubs.verifyFindApplicationByLinkId(tdAll.linkId)
    if withBpr
    then AgentRegistrationStubs.verifyGetApplicationBusinessPartnerRecord(utr = tdAll.saUtr.asUtr)
    else ()

  def verifyAuthAndFindApplicationAndProvideDetailsSoleTrader(): Unit =
    IndividualAuthStubs.verifyAuthorise()
    AgentRegistrationIndividualProvidedDetailsStubs.verifyFindAllForApplicationId(tdAll.agentApplicationId, 2)
    AgentRegistrationStubs.verifyFindApplicationByLinkId(tdAll.linkId)
    AgentRegistrationIndividualProvidedDetailsStubs.verifyUpsertIndividualProvidedDetails()

  def verifyAuthAndUpdateProvidedDetails(): Unit =
    verifyAuthAndFindApplicationAndProvidedDetails()
    AgentRegistrationIndividualProvidedDetailsStubs.verifyUpsertIndividualProvidedDetails()

  def stubRiskingProgress(
    agentApplication: AgentApplication,
    individualProvidedDetails: IndividualProvidedDetails,
    riskingProgress: RiskingProgress
  ): StubMapping =
    stubAuthAndFindApplicationAndProvidedDetails(agentApplication, individualProvidedDetails)
    AgentRegistrationStubs.stubGetApplicationBusinessPartnerRecord(
      utr = tdAll.saUtr.asUtr,
      responseBody = tdAll.businessPartnerRecordResponse
    )
    AgentRegistrationRiskingStubs.stubGetIndividualRiskingResponse(
      personReference = individualProvidedDetails.personReference,
      riskingProgress = riskingProgress
    )

  def verifyRiskingProgressCalls(personReference: PersonReference): Unit =
    verifyAuthAndFindApplicationAndProvidedDetails()
    AgentRegistrationRiskingStubs.verifyGetIndividualRiskingResponse(personReference = personReference)

  def stubFixableFailureUpdate(
    agentApplication: AgentApplication,
    individualProvidedDetails: IndividualProvidedDetails,
    updatedIndividualProvidedDetails: IndividualProvidedDetails
  ): StubMapping =
    stubAuthAndUpdateProvidedDetails(
      agentApplication,
      individualProvidedDetails,
      updatedIndividualProvidedDetails
    )
    AgentRegistrationStubs.stubGetApplicationBusinessPartnerRecord(
      utr = tdAll.saUtr.asUtr,
      responseBody = tdAll.businessPartnerRecordResponse
    )
