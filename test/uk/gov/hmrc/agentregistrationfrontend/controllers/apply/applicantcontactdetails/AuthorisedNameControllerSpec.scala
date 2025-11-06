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
import uk.gov.hmrc.agentregistration.shared.contactdetails.ApplicantContactDetails
import uk.gov.hmrc.agentregistration.shared.contactdetails.ApplicantName

import uk.gov.hmrc.agentregistrationfrontend.forms.AuthorisedNameForm
import uk.gov.hmrc.agentregistrationfrontend.testsupport.ControllerSpec
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.AgentRegistrationStubs
import uk.gov.hmrc.agentregistrationfrontend.testsupport.wiremock.stubs.AuthStubs

class AuthorisedNameControllerSpec
extends ControllerSpec:

  private val path = "/agent-registration/apply/applicant/applicant-name"

  object agentApplication:

    val beforeRoleSelected: AgentApplicationLlp =
      tdAll
        .agentApplicationLlp
        .afterGrsDataReceived

    val afterAuthorisedRoleSelected: AgentApplicationLlp =
      tdAll
        .agentApplicationLlp
        .sectionContactDetails
        .whenApplicantIsAuthorised
        .afterRoleSelected

    val afterNameDeclared: AgentApplicationLlp =
      tdAll
        .agentApplicationLlp
        .sectionContactDetails
        .whenApplicantIsAuthorised
        .afterNameDeclared

  "routes should have correct paths and methods" in:
    routes.AuthorisedNameController.show shouldBe Call(
      method = "GET",
      url = path
    )
    routes.AuthorisedNameController.submit shouldBe Call(
      method = "POST",
      url = path
    )
    routes.AuthorisedNameController.submit.url shouldBe routes.AuthorisedNameController.show.url

  s"GET $path without the authorised name role selected should redirect to the applicant role page" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.beforeRoleSelected)
    val response: WSResponse = get(path)

    response.status shouldBe Status.SEE_OTHER
    response.body[String] shouldBe ""
    response.header("Location").value shouldBe routes.ApplicantRoleInLlpController.show.url

  s"GET $path should return 200 and render page" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.afterAuthorisedRoleSelected)
    val response: WSResponse = get(path)

    response.status shouldBe Status.OK
    response.parseBodyAsJsoupDocument.title() shouldBe "What is your full name? - Apply for an agent services account - GOV.UK"

  s"GET $path after name has been stored should return 200 and render page with previous answer filled in" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.afterNameDeclared)
    val response: WSResponse = get(path)

    response.status shouldBe Status.OK
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "What is your full name? - Apply for an agent services account - GOV.UK"
    doc.mainContent
      .selectOrFail(s"input[name='${AuthorisedNameForm.key}']")
      .attr("value") shouldBe agentApplication
      .afterNameDeclared
      .getApplicantContactDetails
      .getApplicantName

  s"POST $path with valid name should save data and redirect to check your answers" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.afterAuthorisedRoleSelected)
    AgentRegistrationStubs.stubUpdateAgentApplication(agentApplication.afterNameDeclared)
    val response: WSResponse =
      post(path)(Map(
        AuthorisedNameForm.key -> Seq("Miss Alexa Fantastic")
      ))

    response.status shouldBe Status.SEE_OTHER
    response.body[String] shouldBe ""
    response.header("Location").value shouldBe routes.CheckYourAnswersController.show.url

  s"POST $path with blank inputs should return 400" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.afterAuthorisedRoleSelected)
    val response: WSResponse =
      post(path)(Map(
        AuthorisedNameForm.key -> Seq("")
      ))

    response.status shouldBe Status.BAD_REQUEST
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "Error: What is your full name? - Apply for an agent services account - GOV.UK"
    doc.mainContent.select(s"#${AuthorisedNameForm.key}-error").text() shouldBe "Error: Enter your full name"

  s"POST $path with invalid characters should return 400" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.afterAuthorisedRoleSelected)
    val response: WSResponse =
      post(path)(Map(
        AuthorisedNameForm.key -> Seq("[[)(*%")
      ))

    response.status shouldBe Status.BAD_REQUEST
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "Error: What is your full name? - Apply for an agent services account - GOV.UK"
    doc.mainContent.select(
      s"#${AuthorisedNameForm.key}-error"
    ).text() shouldBe "Error: Your full name must only include letters a to z, hyphens, apostrophes and spaces"

  s"POST $path with more than 100 characters should return 400" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.afterAuthorisedRoleSelected)
    val response: WSResponse =
      post(path)(Map(
        AuthorisedNameForm.key -> Seq("A".repeat(101))
      ))

    response.status shouldBe Status.BAD_REQUEST
    val doc = response.parseBodyAsJsoupDocument
    doc.title() shouldBe "Error: What is your full name? - Apply for an agent services account - GOV.UK"
    doc.mainContent.select("#authorisedName-error").text() shouldBe "Error: Your full name must be 100 characters or fewer"

  s"POST $path with save for later and valid selection should save data and redirect to the saved for later page" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.afterAuthorisedRoleSelected)
    AgentRegistrationStubs.stubUpdateAgentApplication(agentApplication.afterNameDeclared)
    val response: WSResponse =
      post(path)(Map(
        AuthorisedNameForm.key -> Seq("Miss Alexa Fantastic"),
        "submit" -> Seq("SaveAndComeBackLater")
      ))

    response.status shouldBe Status.SEE_OTHER
    response.body[String] shouldBe ""
    response.header("Location").value shouldBe AppRoutes.apply.SaveForLaterController.show.url

  s"POST $path with save for later and invalid inputs should not return errors and redirect to save for later page" in:
    AuthStubs.stubAuthorise()
    AgentRegistrationStubs.stubGetAgentApplication(agentApplication.afterAuthorisedRoleSelected)
    val response: WSResponse =
      post(path)(Map(
        AuthorisedNameForm.key -> Seq("[[)(*%"),
        "submit" -> Seq("SaveAndComeBackLater")
      ))

    response.status shouldBe Status.SEE_OTHER
    response.body[String] shouldBe ""
    response.header("Location").value shouldBe AppRoutes.apply.SaveForLaterController.show.url
