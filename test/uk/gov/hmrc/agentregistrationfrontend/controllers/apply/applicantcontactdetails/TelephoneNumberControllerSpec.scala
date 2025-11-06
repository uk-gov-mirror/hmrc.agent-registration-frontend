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

package uk.gov.hmrc.agentregistrationfrontend.controllers.apply.applicantcontactdetails

import play.api.libs.ws.DefaultBodyReadables.*
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentregistration.shared.AgentApplicationLlp

import uk.gov.hmrc.agentregistrationfrontend.forms.TelephoneNumberForm
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ControllerSpec
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.AgentRegistrationStubs
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.AuthStubs

class TelephoneNumberControllerSpec
extends ControllerSpec:

  private val path = "/agent-registration/apply/applicant/telephone-number"

  private object agentApplication:

    val beforeTelephoneUpdate: AgentApplicationLlp =
      tdAll
        .agentApplicationLlp
        .sectionContactDetails
        .whenApplicantIsAuthorised
        .afterNameDeclared

    val afterTelephoneNumberProvided: AgentApplicationLlp =
      tdAll
        .agentApplicationLlp
        .sectionContactDetails
        .whenApplicantIsAuthorised
        .afterTelephoneNumberProvided

  "routes should have correct paths and methods" in:
    routes.TelephoneNumberController.show shouldBe Call(
      method = "GET",
      url = path
    )
    routes.TelephoneNumberController.submit shouldBe Call(
      method = "POST",
      url = path
    )
    routes.TelephoneNumberController.submit.url shouldBe routes.TelephoneNumberController.show.url

  s"GET $path should return 200 and render page" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.beforeTelephoneUpdate)
    val response: WSResponse = get(path)

    response.status shouldBe Status.OK
    response.parseBodyAsJsoupDocument.title() shouldBe "If we need to speak to you about this application, what number do we call? - Apply for an agent services account - GOV.UK"

  s"GET $path when telephone number has been stored already should return 200 and render page with the previous answer filled in" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.afterTelephoneNumberProvided)
    val response: WSResponse = get(path)

    response.status shouldBe Status.OK
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "If we need to speak to you about this application, what number do we call? - Apply for an agent services account - GOV.UK"
    doc.mainContent
      .selectOrFail(s"input[name='${TelephoneNumberForm.key}']")
      .attr("value") shouldBe agentApplication
      .afterTelephoneNumberProvided
      .getApplicantContactDetails
      .getTelephoneNumber.value

  s"POST $path with valid name should save data and redirect to check your answers" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.beforeTelephoneUpdate)
    AgentRegistrationStubs.stubUpdateAgentApplication(agentApplication.afterTelephoneNumberProvided)

    val response: WSResponse =
      post(path)(Map(
        TelephoneNumberForm.key -> Seq(tdAll.telephoneNumber.value)
      ))

    response.status shouldBe Status.SEE_OTHER
    response.body[String] shouldBe ""
    response.header("Location").value shouldBe routes.CheckYourAnswersController.show.url

  s"POST $path with blank inputs should return 400" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.beforeTelephoneUpdate)
    val response: WSResponse =
      post(path)(Map(
        TelephoneNumberForm.key -> Seq("")
      ))

    response.status shouldBe Status.BAD_REQUEST
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "Error: If we need to speak to you about this application, what number do we call? - Apply for an agent services account - GOV.UK"
    doc.mainContent.select("#telephoneNumber-error").text() shouldBe "Error: Enter the number we should call to speak to you about this application"

  s"POST $path with invalid characters should return 400" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.beforeTelephoneUpdate)
    val response: WSResponse =
      post(path)(Map(
        TelephoneNumberForm.key -> Seq("[[)(*%")
      ))

    response.status shouldBe Status.BAD_REQUEST
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "Error: If we need to speak to you about this application, what number do we call? - Apply for an agent services account - GOV.UK"
    doc.mainContent.select("#telephoneNumber-error").text() shouldBe "Error: Enter a phone number, like 01632 960 001 or 07700 900 982"

  s"POST $path with more than 25 characters should return 400" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.beforeTelephoneUpdate)
    val response: WSResponse =
      post(path)(Map(
        TelephoneNumberForm.key -> Seq("2".repeat(26))
      ))

    response.status shouldBe Status.BAD_REQUEST
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "Error: If we need to speak to you about this application, what number do we call? - Apply for an agent services account - GOV.UK"
    doc.mainContent.select("#telephoneNumber-error").text() shouldBe "Error: The phone number must be 25 characters or fewer"

  s"POST $path with save for later and valid selection should save data and redirect to the saved for later page" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.beforeTelephoneUpdate)
    AgentRegistrationStubs.stubUpdateAgentApplication(agentApplication.afterTelephoneNumberProvided)
    val response: WSResponse =
      post(path)(Map(
        TelephoneNumberForm.key -> Seq(tdAll.telephoneNumber.value),
        "submit" -> Seq("SaveAndComeBackLater")
      ))

    response.status shouldBe Status.SEE_OTHER
    response.body[String] shouldBe ""
    response.header("Location").value shouldBe AppRoutes.apply.SaveForLaterController.show.url

  s"POST $path with save for later and invalid inputs should not return errors and redirect to save for later page" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.beforeTelephoneUpdate)
    val response: WSResponse =
      post(path)(Map(
        TelephoneNumberForm.key -> Seq("[[*%"),
        "submit" -> Seq("SaveAndComeBackLater")
      ))

    response.status shouldBe Status.SEE_OTHER
    response.body[String] shouldBe ""
    response.header("Location").value shouldBe AppRoutes.apply.SaveForLaterController.show.url
