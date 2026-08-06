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

package uk.gov.hmrc.agentregistrationfrontend.controllers.individual

import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentregistration.shared.AgentApplication
import uk.gov.hmrc.agentregistration.shared.BusinessType
import uk.gov.hmrc.agentregistration.shared.individual.IndividualProvidedDetails
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ControllerSpec
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.providedetails.llp.AgentRegistrationIndividualProvidedDetailsStubs

class CheckYourAnswersControllerSpec
extends ControllerSpec:

  private val linkId = tdAll.linkId
  private val agentApplicationLlp =
    tdAll
      .agentApplicationLlp
      .afterContactDetailsComplete
  private val agentApplicationSoleTrader =
    tdAll
      .agentApplicationSoleTrader
      .afterContactDetailsComplete

  private val path = s"/agent-registration/provide-details/check-your-answers/${linkId.value}"

  "route should have correct path and method" in:
    AppRoutes.providedetails.CheckYourAnswersController.show(linkId) shouldBe Call(
      method = "GET",
      url = path
    )

  private object individualProvideDetails:

    val complete: IndividualProvidedDetails = tdAll.providedDetails.afterHmrcStandardforAgentsAgreed
    val completeAndConfirmed: IndividualProvidedDetails = tdAll.providedDetails.afterFinished
    val missingAgreeStandards: IndividualProvidedDetails = tdAll.providedDetails.afterApproveAgentApplication
    val missingApproveApplication: IndividualProvidedDetails = tdAll.providedDetails.afterUcrProvided
    val missingUcrIdentifiers: IndividualProvidedDetails = tdAll.providedDetails.AfterSaUtr.afterSaUtrProvided
    val missingSaUtr: IndividualProvidedDetails = tdAll.providedDetails.AfterNino.afterNinoProvided
    val missingDateOfBirth: IndividualProvidedDetails = tdAll.providedDetails.afterEmailAddressVerified
    val missingNino: IndividualProvidedDetails = tdAll.providedDetails.AfterDateOfBirth.afterDateOfBirthProvided
    val missingEmail: IndividualProvidedDetails = tdAll.providedDetails.afterTelephoneNumberProvided
    val missingEmailValidation: IndividualProvidedDetails = tdAll.providedDetails.afterEmailAddressProvided
    val missingTelephone: IndividualProvidedDetails = tdAll.providedDetails.afterStarted

  private final case class TestCaseForCya(
    providedDetails: IndividualProvidedDetails,
    name: String,
    application: AgentApplication = agentApplicationLlp,
    expectedRedirect: Option[String] = None
  )

  List(
    TestCaseForCya(
      providedDetails = individualProvideDetails.complete,
      name = "complete agent details"
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.missingAgreeStandards,
      name = "agree to standards of agents",
      expectedRedirect = Some(AppRoutes.providedetails.IndividualHmrcStandardForAgentsController.show(linkId).url)
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.missingApproveApplication,
      name = "approve applicant",
      expectedRedirect = Some(AppRoutes.providedetails.IndividualApproveApplicantController.show(linkId).url)
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.missingUcrIdentifiers,
      name = "ucr identifiers",
      expectedRedirect = Some(AppRoutes.providedetails.internal.UcrIndividualController.populateIndividualIdentifiersFromUcr(linkId).url)
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.missingSaUtr,
      name = "saUtr",
      expectedRedirect = Some(AppRoutes.providedetails.IndividualSaUtrController.show(linkId).url)
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.missingNino,
      name = "nino",
      expectedRedirect = Some(AppRoutes.providedetails.IndividualNinoController.show(linkId).url)
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.missingDateOfBirth,
      name = "date of birth",
      expectedRedirect = Some(AppRoutes.providedetails.IndividualDateOfBirthController.show(linkId).url)
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.missingEmailValidation,
      name = "email address validation",
      expectedRedirect = Some(AppRoutes.providedetails.IndividualEmailAddressController.show(linkId).url)
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.missingEmail,
      name = "email address",
      expectedRedirect = Some(AppRoutes.providedetails.IndividualEmailAddressController.show(linkId).url)
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.missingTelephone,
      name = "telephone number",
      expectedRedirect = Some(AppRoutes.providedetails.IndividualTelephoneNumberController.show(linkId).url)
    ),
    TestCaseForCya(
      providedDetails = individualProvideDetails.complete,
      name = "sole trader",
      application = agentApplicationSoleTrader,
      expectedRedirect = Some(AppRoutes.providedetails.IndividualConfirmationController.show(linkId).url)
    )
  ).foreach: testCase =>
    testCase.expectedRedirect match
      case None =>
        s"GET $path with ${testCase.name} should return 200 and render page" in:
          ProvideDetailsStubHelper.stubAuthAndFindApplicationAndProvidedDetails(
            testCase.application,
            testCase.providedDetails
          )
          val response: WSResponse = get(path)
          response.status shouldBe Status.OK
          val doc = response.parseBodyAsJsoupDocument
          doc.title() shouldBe "Check your answers - Apply for an agent services account - GOV.UK"
          ProvideDetailsStubHelper.verifyAuthAndFindApplicationAndProvidedDetails()
      case Some(expectedRedirect) if testCase.application.businessType === BusinessType.SoleTrader =>
        s"GET $path for ${testCase.name} should redirect to ${testCase.expectedRedirect.value} page" in:
          ProvideDetailsStubHelper.stubAuthAndFindApplicationAndProvidedDetails(
            testCase.application,
            testCase.providedDetails
          )
          AgentRegistrationIndividualProvidedDetailsStubs.stubUpsertIndividualProvidedDetails(individualProvideDetails.complete)
          val response: WSResponse = get(path)
          response.status shouldBe Status.SEE_OTHER
          response.header("Location").value shouldBe expectedRedirect
          ProvideDetailsStubHelper.verifyAuthAndFindApplicationAndProvideDetailsSoleTrader()

      case Some(expectedRedirect) =>
        s"GET $path with missing ${testCase.name} should redirect to the ${testCase.name} page" in:
          ProvideDetailsStubHelper.stubAuthAndFindApplicationAndProvidedDetails(
            testCase.application,
            testCase.providedDetails
          )
          val response: WSResponse = get(path)
          response.status shouldBe Status.SEE_OTHER
          response.header("Location").value shouldBe expectedRedirect
          ProvideDetailsStubHelper.verifyAuthAndFindApplicationAndProvidedDetails()
